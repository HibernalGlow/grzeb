package dev.hibernalglow.grzeb.core.search

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import dev.hibernalglow.grzeb.core.fs.FileEntry
import dev.hibernalglow.grzeb.core.fs.GrzebFileSystem
import dev.hibernalglow.grzeb.core.text.TextFileTypes

/** 检索选项。对应 react 版 `lib/search/types.ts` 的 SearchOptions。 */
data class SearchOptions(
    val query: String = "",
    /** 把 query 当作正则而非字面量。 */
    val isRegex: Boolean = false,
    val isCaseSensitive: Boolean = false,
    /** 全词匹配（在 pattern 外包一层 \b）。 */
    val isWholeWord: Boolean = false,
    /** 每个文件只保留第一个命中。 */
    val isOnlyFirstMatch: Boolean = false,
    /** 最大递归深度。 */
    val maxDepth: Int = 10,
    /** 忽略的目录名；含 '^' / '$' / '*' 的按正则处理，其余按名字精确匹配。 */
    val ignoredDirs: List<String> = DEFAULT_IGNORED_DIRS,
    /** 额外纳入的扩展名。 */
    val includeExtensions: List<String> = emptyList(),
    /** 超长行截断到该长度。 */
    val maxPreviewLength: Int = 100,
    /** 短行展示的上下文行数。 */
    val contextLines: Int = 2,
    /**
     * 同时在飞的 IO 数（列目录 + 读文件）。
     *
     * SAF 每次调用都是一次跨进程查询，瓶颈在延迟不在带宽，所以并发是净赚的；
     * 但 provider 端也要排队，开太高反而更慢，4 是实测出来的折中起点。
     */
    val concurrency: Int = 4,
) {
    companion object {
        val DEFAULT_IGNORED_DIRS = listOf(".git", ".svn", ".hg", "node_modules", ".expo", ".cache", ".tmp", "thumbs")
    }
}

/** 单个命中。 */
data class SearchMatch(
    val matchText: String,
    /** 在整文件内容中的字符偏移。 */
    val start: Int,
    val end: Int,
    /** 0-based 行号。 */
    val lineNumber: Int,
    /** 带上下文的预览文本。 */
    val preview: String,
    /** 预览起始行号。 */
    val previewStartLine: Int,
    /** 命中在所在行内的相对字符索引（用于行内高亮定位）。 */
    val indexInLine: Int,
)

/** 单文件检索结果。 */
data class FileSearchResult(
    val uri: String,
    val relPath: String,
    val isDirectory: Boolean = false,
    val size: Long? = null,
    val matches: List<SearchMatch> = emptyList(),
    val matchCount: Int = 0,
)

/** 检索进度。 */
data class SearchProgress(
    val currentFile: String? = null,
    val scannedFiles: Int = 0,
    val totalMatches: Int = 0,
    val currentDepth: Int = 0,
    val isComplete: Boolean = false,
    val error: String? = null,
)

/** 检索进度回调。 */
data class SearchCallbacks(
    val onProgress: (SearchProgress) -> Unit = {},
    val onResult: (FileSearchResult) -> Unit = {},
)

/**
 * 跨平台全文搜索引擎。
 *
 * 逻辑移植自 react 版 `lib/search/engine.ts`，但去掉了对 SAF 的直接依赖：
 * 目录遍历与读文件都通过 [GrzebFileSystem] 完成，因此同一份实现可在
 * Android / 桌面 / Web 上运行。
 *
 * 与 react 版的关键差别是并发：那边是一层一层顺序走，SAF 上光"列目录"就要几百毫秒到几秒，
 * 顺序走等于把这些延迟全加上；这里子目录各起一条协程、同目录下的文件再切成几批并行，
 * 并用 [SearchOptions.concurrency] 限制同时在飞的 IO 数。
 *
 * 结果顺序因此是不确定的（谁先扫完谁先进列表）——追加式列表本来也不保证顺序。
 */
class SearchEngine(
    private val fileSystem: GrzebFileSystem,
    private val options: SearchOptions,
    private val callbacks: SearchCallbacks = SearchCallbacks(),
) {

    private val regex: Regex? = buildRegex(options)
    private val ignoredRegexes: List<Regex>
    private val ignoredExact: Set<String>

    init {
        val patterns = mutableListOf<Regex>()
        val exact = mutableSetOf<String>()
        for (raw in options.ignoredDirs) {
            if (raw.startsWith("^") || raw.endsWith("$") || raw.contains("*")) {
                runCatching { patterns += Regex(raw, RegexOption.IGNORE_CASE) }
            } else {
                exact += raw.lowercase()
            }
        }
        ignoredRegexes = patterns
        ignoredExact = exact
    }

    /** 正则是否可用（UI 据此提示"表达式无效"）。 */
    val isQueryValid: Boolean get() = regex != null

    /**
     * 从 [rootUri] 开始递归检索。
     * 可被协程取消；取消后所有在飞的子协程一起停。
     */
    suspend fun search(rootUri: String): List<FileSearchResult> {
        val engine = regex ?: return emptyList()
        if (!fileSystem.isSupported) return emptyList()

        val results = mutableListOf<FileSearchResult>()
        var scanned = 0
        var matches = 0

        // IO 限流：列目录与读文件都算，别把 provider 打爆
        val io = Semaphore(options.concurrency.coerceAtLeast(1))
        // 结果、进度来自多条协程，用锁串起来；顺带保证回调不会并发进 UI
        val lock = Mutex()

        suspend fun scanFile(entry: FileEntry, depth: Int) {
            currentCoroutineContext().ensureActive()
            val content = io.withPermit { fileSystem.readText(entry.uri) }
            val fileResult = content?.let {
                searchInContent(entry.uri, entry.name, entry.size, it, engine)
            }

            lock.withLock {
                scanned++
                if (fileResult != null) {
                    results += fileResult
                    matches += fileResult.matchCount
                }
                callbacks.onProgress(
                    SearchProgress(
                        currentFile = entry.name,
                        scannedFiles = scanned,
                        totalMatches = matches,
                        currentDepth = depth,
                    )
                )
                if (fileResult != null) callbacks.onResult(fileResult)
            }
        }

        suspend fun walk(uri: String, depth: Int) {
            currentCoroutineContext().ensureActive()
            if (depth > options.maxDepth) return

            val entries = io.withPermit { fileSystem.list(uri) }

            val subdirectories = mutableListOf<FileEntry>()
            val files = mutableListOf<FileEntry>()
            for (entry in entries) {
                if (entry.isDirectory) {
                    if (!entry.name.startsWith(".") && !shouldIgnore(entry.name)) {
                        subdirectories += entry
                    }
                } else if (TextFileTypes.isTextFile(entry.name, options.includeExtensions)) {
                    files += entry
                }
            }

            coroutineScope {
                // 同目录下的文件切成几批并行，批数封顶在 concurrency —— 一个目录里
                // 几万个文件时，一批一条协程，不至于把协程数也撑爆
                val batches = minOf(options.concurrency, files.size)
                if (batches > 0) {
                    val perBatch = (files.size + batches - 1) / batches
                    for (i in 0 until batches) {
                        val from = i * perBatch
                        val to = minOf(from + perBatch, files.size)
                        if (from >= to) continue
                        val slice = files.subList(from, to)
                        launch {
                            for (file in slice) scanFile(file, depth)
                        }
                    }
                }

                for (directory in subdirectories) {
                    launch { walk(directory.uri, depth + 1) }
                }
            }
        }

        walk(rootUri, 0)

        lock.withLock {
            callbacks.onProgress(
                SearchProgress(scannedFiles = scanned, totalMatches = matches, isComplete = true)
            )
        }
        return results
    }

    private fun shouldIgnore(name: String): Boolean {
        val lower = name.lowercase()
        if (ignoredExact.contains(lower)) return true
        return ignoredRegexes.any { it.containsMatchIn(name) }
    }

    private fun searchInContent(
        uri: String,
        name: String,
        size: Long?,
        content: String,
        engine: Regex,
    ): FileSearchResult? {
        val lines = content.split('\n')
        val lineStarts = IntArray(lines.size)
        var cursor = 0
        for (i in lines.indices) {
            lineStarts[i] = cursor
            cursor += lines[i].length + 1
        }

        val found = mutableListOf<SearchMatch>()
        val seen = mutableSetOf<Int>()

        for (match in engine.findAll(content)) {
            if (!seen.add(match.range.first)) continue

            val lineNumber = lineNumberFor(lineStarts, match.range.first)
            val line = lines.getOrElse(lineNumber) { "" }
            // 与 react 版一致：长行放弃上下文，避免预览被无关内容撑满
            val isLongLine = line.length > 50
            val effectiveContext = if (isLongLine) 0 else options.contextLines

            val previewStart = (lineNumber - effectiveContext).coerceAtLeast(0)
            val preview = buildPreview(
                lines = lines,
                matchedLine = lineNumber,
                matchInLine = match.range.first - lineStarts.getOrElse(lineNumber) { 0 },
                matchLength = match.value.length,
                contextLines = effectiveContext,
            )

            found += SearchMatch(
                matchText = match.value,
                start = match.range.first,
                end = match.range.last + 1,
                lineNumber = lineNumber,
                preview = preview,
                previewStartLine = previewStart,
                indexInLine = match.range.first - lineStarts.getOrElse(lineNumber) { 0 },
            )

            if (options.isOnlyFirstMatch) break
        }

        if (found.isEmpty()) return null
        return FileSearchResult(
            uri = uri,
            relPath = name,
            size = size,
            matches = found,
            matchCount = found.size,
        )
    }

    /** 二分查找命中所属行号。 */
    private fun lineNumberFor(lineStarts: IntArray, offset: Int): Int {
        var low = 0
        var high = lineStarts.size - 1
        var answer = 0
        while (low <= high) {
            val mid = (low + high) / 2
            if (lineStarts[mid] <= offset) {
                answer = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return answer
    }

    private fun buildPreview(
        lines: List<String>,
        matchedLine: Int,
        matchInLine: Int,
        matchLength: Int,
        contextLines: Int,
    ): String {
        val from = (matchedLine - contextLines).coerceAtLeast(0)
        val to = (matchedLine + contextLines).coerceAtMost(lines.size - 1)

        val parts = mutableListOf<String>()
        for (i in from..to) {
            var line = lines[i]
            if (line.length > options.maxPreviewLength) {
                line = if (i == matchedLine) {
                    val offset = ((options.maxPreviewLength - matchLength) / 2).coerceAtLeast(0)
                    val subStart = (matchInLine - offset).coerceAtLeast(0)
                    val subEnd = (matchInLine + matchLength + offset).coerceAtMost(line.length)
                    val prefix = if (subStart > 0) "..." else ""
                    val suffix = if (subEnd < line.length) "..." else ""
                    prefix + line.substring(subStart, subEnd) + suffix
                } else {
                    line.take(options.maxPreviewLength) + "..."
                }
            }
            parts += line
        }
        return parts.joinToString("\n")
    }

    companion object {
        /** 构建检索正则；非法表达式返回 null。 */
        fun buildRegex(options: SearchOptions): Regex? {
            if (options.query.isEmpty()) return null
            var pattern = options.query
            if (!options.isRegex) pattern = Regex.escape(pattern)
            if (options.isWholeWord) pattern = "\\b$pattern\\b"

            val flags = buildSet {
                if (!options.isCaseSensitive) add(RegexOption.IGNORE_CASE)
                add(RegexOption.MULTILINE)
            }
            return runCatching { Regex(pattern, flags) }.getOrNull()
        }
    }
}
