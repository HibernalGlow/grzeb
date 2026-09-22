package dev.hibernalglow.grzeb.core.index

import dev.hibernalglow.grzeb.core.fs.FileEntry
import dev.hibernalglow.grzeb.core.fs.GrzebFileSystem
import dev.hibernalglow.grzeb.core.search.SearchOptions
import dev.hibernalglow.grzeb.core.text.TextFileTypes
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** 预热进度。 */
data class WarmProgress(
    /** 刚处理完的文件名。 */
    val currentFile: String? = null,
    /** 已看过多少个文本文件。 */
    val scannedFiles: Int = 0,
    /** 其中重新读取 + 解码入库的。 */
    val indexedFiles: Int = 0,
    /** 其中命中缓存、直接跳过的。 */
    val reusedFiles: Int = 0,
    val currentDepth: Int = 0,
    val isComplete: Boolean = false,
)

/**
 * 目录索引预热：把一棵目录树扫进 [IndexStore]。
 *
 * 目录项进 entry 表（之后遍历不必再列目录），文本文件的解码正文进 content 表
 * （之后检索不必再读文件、猜编码）。这就是"提前预热"的全部意义。
 *
 * 增量靠 `last_modified`：缓存里的时间戳与当前列目录拿到的一致，就认定文件没变、跳过读取。
 * 于是**第二次预热只花列目录的钱**，正文一条都不重读；改过的文件会被重读覆盖，
 * 消失的文件连同正文一起清掉。
 *
 * 目录遍历的并发策略与 [dev.hibernalglow.grzeb.core.search.SearchEngine] 一致
 * （子目录各一条协程、同目录文件分批、IO 用信号量限流）——那边正在被并行修改，
 * 等稳定后再把两处合并成一份。
 */
class IndexWarmer(
    private val fileSystem: GrzebFileSystem,
    private val store: IndexStore,
    private val options: SearchOptions = SearchOptions(),
    private val onProgress: (WarmProgress) -> Unit = {},
) {

    /** 预热（或增量刷新）整棵树，返回刷新后的状态。 */
    @OptIn(ExperimentalTime::class)
    suspend fun warm(treeUri: String): IndexStatus {
        if (!fileSystem.isSupported) return store.status(treeUri)

        // 刷新前的快照：既用来判断"变没变"，也用来找出已经消失、需要清掉的条目
        val before = store.entries(treeUri)
        val cached = before.associateBy { it.uri }
        val seen = mutableSetOf<String>()

        var scanned = 0
        var indexed = 0
        var reused = 0

        val io = Semaphore(options.concurrency.coerceAtLeast(1))
        val lock = Mutex()

        fun report(currentFile: String?, depth: Int) {
            onProgress(
                WarmProgress(
                    currentFile = currentFile,
                    scannedFiles = scanned,
                    indexedFiles = indexed,
                    reusedFiles = reused,
                    currentDepth = depth,
                )
            )
        }

        suspend fun indexFile(entry: FileEntry, depth: Int) {
            currentCoroutineContext().ensureActive()

            val known = cached[entry.uri]
            val unchanged = known != null &&
                known.lastModified != null &&
                known.lastModified == entry.lastModified &&
                store.text(treeUri, entry.uri) != null

            if (unchanged) {
                lock.withLock {
                    scanned++
                    reused++
                    report(entry.name, depth)
                }
                return
            }

            val text = io.withPermit { fileSystem.readText(entry.uri) }
            if (text == null) {
                // 读不出来（二进制 / 超大 / 权限没了）：把旧正文清掉，
                // 否则陈旧的缓存会继续在检索里命中一个已经读不到的文件
                store.dropText(treeUri, entry.uri)
            } else {
                store.putText(treeUri, entry.uri, entry.lastModified, text)
            }
            lock.withLock {
                scanned++
                indexed++
                report(entry.name, depth)
            }
        }

        suspend fun walk(uri: String, depth: Int) {
            currentCoroutineContext().ensureActive()
            if (depth > options.maxDepth) return

            val entries = io.withPermit { fileSystem.list(uri) }
            val directories = mutableListOf<FileEntry>()
            val files = mutableListOf<FileEntry>()

            for (entry in entries) {
                seen += entry.uri
                if (entry.isDirectory) {
                    if (!entry.name.startsWith(".") && !isIgnored(entry.name)) directories += entry
                } else if (TextFileTypes.isTextFile(entry.name, options.includeExtensions)) {
                    files += entry
                }
            }

            // 目录项整批入库：这一步让"下次遍历"变成一次数据库查询
            store.putEntries(
                treeUri,
                entries.map {
                    IndexedEntry(
                        uri = it.uri,
                        parentUri = uri,
                        name = it.name,
                        isDirectory = it.isDirectory,
                        size = it.size,
                        lastModified = it.lastModified,
                    )
                },
            )

            coroutineScope {
                val batches = minOf(options.concurrency, files.size)
                if (batches > 0) {
                    val perBatch = (files.size + batches - 1) / batches
                    for (i in 0 until batches) {
                        val from = i * perBatch
                        val to = minOf(from + perBatch, files.size)
                        if (from >= to) continue
                        val slice = files.subList(from, to)
                        launch {
                            for (file in slice) indexFile(file, depth)
                        }
                    }
                }

                for (directory in directories) {
                    launch { walk(directory.uri, depth + 1) }
                }
            }
        }

        walk(treeUri, 0)

        // 这轮没见到的条目 = 已经被删 / 改名的，连同正文一起清掉。
        // 不清的话，缓存正文会让已经不存在的文件继续出现在检索结果里。
        val stale = cached.keys - seen
        if (stale.isNotEmpty()) store.removeEntries(treeUri, stale)

        store.markWarmed(
            treeUri = treeUri,
            displayName = runCatching { fileSystem.displayName(treeUri) }.getOrNull(),
            at = Clock.System.now().toEpochMilliseconds(),
        )
        onProgress(WarmProgress(isComplete = true, scannedFiles = scanned, indexedFiles = indexed, reusedFiles = reused))
        return store.status(treeUri)
    }

    private fun isIgnored(name: String): Boolean {
        val lower = name.lowercase()
        if (options.ignoredDirs.any { !it.hasRegexMeta() && it.lowercase() == lower }) return true
        return options.ignoredDirs.any { dir ->
            dir.hasRegexMeta() && runCatching { Regex(dir, RegexOption.IGNORE_CASE) }.getOrNull()
                ?.containsMatchIn(name) == true
        }
    }

    private fun String.hasRegexMeta(): Boolean =
        startsWith("^") || endsWith("$") || contains("*")
}
