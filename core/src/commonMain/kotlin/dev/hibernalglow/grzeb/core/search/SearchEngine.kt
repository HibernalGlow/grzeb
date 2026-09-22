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
    /**
     * 把 query 里的 `*` 当作通配符（任意字符，**含换行**）、`?` 当作任意单个非换行字符。
     *
     * 与 [isRegex] 互斥，同时为 true 时以 [isRegex] 为准。
     * 于是 `张三*卡号` 可以跨行匹配，且是**有序**的（张三必须出现在卡号之前）——
     * 这正好是空格分词那种无序 AND 的"有序版"。
     */
    val isWildcard: Boolean = false,
    /**
     * 按空白把 query 切成多个关键词，语义是**文件级无序 AND**：
     * 每个词都要在该文件里出现，但**不要求出现在同一行**。
     *
     * 支持 `"短语"` 与 `-排除词`；仅在非正则模式下生效（正则整条编译，不切词）。
     * 细节见 [QueryParser]。
     */
    val isMultiKeyword: Boolean = false,
    val isCaseSensitive: Boolean = false,
    /**
     * 全词匹配。
     *
     * 仅对**不含中日韩字符**的词生效：中文两侧永远构不成 `\b`，加了会得到恒假的 pattern，
     * 详见 [QueryParser.termRegex]。
     */
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
        // 编译只做一次，遍历时复用同一组正则；某个词编译不出来（正则手滑）就整体放弃
        val plan = QueryParser.parse(options) ?: return emptyList()
        // 只有排除词（或空 query）时没有任何必含词，不必去遍历目录
        if (plan.includes.isEmpty()) return emptyList()
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
                searchInContent(entry.uri, entry.name, entry.size, it, plan)
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
        plan: SearchQuery,
    ): FileSearchResult? {
        // 排除词命中任一处，整份文件出局。先判它，省掉下面的按行切分
        if (plan.excludes.any { it.containsMatchIn(content) }) return null

        val lines = content.split('\n')
        val lineStarts = IntArray(lines.size)
        var cursor = 0
        for (i in lines.indices) {
            lineStarts[i] = cursor
            cursor += lines[i].length + 1
        }

        val found = mutableListOf<SearchMatch>()
        val seen = mutableSetOf<Int>()

        for (engine in plan.includes) {
            var hits = 0
            for (match in engine.findAll(content)) {
                hits++
                if (seen.add(match.range.first) && found.size < MAX_MATCHES_PER_FILE) {
                    found += buildMatch(lines, lineStarts, match)
                }
                if (options.isOnlyFirstMatch || found.size >= MAX_MATCHES_PER_FILE) break
            }
            // 文件级 AND：只要有任何一个必含词在整份文件里一次都没命中，就整份出局。
            // 注意判定用的是 hits 而不是 found.size —— 命中数会被 MAX_MATCHES_PER_FILE
            // 截断，用被截断的计数去判会让"命中太少"的文件被误判为不命中。
            if (hits == 0) return null
        }

        if (found.isEmpty()) return null
        // 多关键词时各处命中是交错收集的，按偏移排序才能让预览按原文顺序展示
        found.sortBy { it.start }

        return FileSearchResult(
            uri = uri,
            relPath = name,
            size = size,
            matches = found,
            matchCount = found.size,
        )
    }

    /** 把一处正则命中转成带行号与上下文的 [SearchMatch]。 */
    private fun buildMatch(
        lines: List<String>,
        lineStarts: IntArray,
        match: MatchResult,
    ): SearchMatch {
        val lineNumber = lineNumberFor(lineStarts, match.range.first)
        val line = lines.getOrElse(lineNumber) { "" }
        val indexInLine = match.range.first - lineStarts.getOrElse(lineNumber) { 0 }
        // 与 react 版一致：长行放弃上下文，避免预览被无关内容撑满
        val isLongLine = line.length > 50
        val effectiveContext = if (isLongLine) 0 else options.contextLines

        return SearchMatch(
            matchText = match.value,
            start = match.range.first,
            end = match.range.last + 1,
            lineNumber = lineNumber,
            preview = buildPreview(
                lines = lines,
                matchedLine = lineNumber,
                matchInLine = indexInLine,
                matchLength = match.value.length,
                contextLines = effectiveContext,
            ),
            previewStartLine = (lineNumber - effectiveContext).coerceAtLeast(0),
            indexInLine = indexInLine,
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
        /**
         * 单文件最多保留多少处命中。
         *
         * 多关键词会把每个词的命中都收进来，中文小说里搜"的"这种词一份文件就能命中几万处；
         * 不收口的话结果列表（[SearchCallbacks.onResult] 会累积到 UI）和内存都会被拖垮。
         *
         * 截断只影响**展示**，不影响"是否命中"的判定——每个词的命中数在截断之外单独计，
         * 见 [searchInContent] 里的 `hits`。
         */
        const val MAX_MATCHES_PER_FILE = 500

        /**
         * 把整条 query 当成**单个**词编译，不做多关键词切词。
         *
         * 保留这个入口是给 UI 判断"表达式是否合法"用的（返回 null 即非法）。
         * 需要多关键词语义时请用 [QueryParser.parse]。
         */
        fun buildRegex(options: SearchOptions): Regex? {
            if (options.query.isEmpty()) return null
            return QueryParser.termRegex(options.query, options)
        }
    }
}
