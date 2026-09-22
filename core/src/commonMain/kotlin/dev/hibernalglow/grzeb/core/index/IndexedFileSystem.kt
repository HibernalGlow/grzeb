package dev.hibernalglow.grzeb.core.index

import dev.hibernalglow.grzeb.core.fs.FileEntry
import dev.hibernalglow.grzeb.core.fs.GrzebFileSystem
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

/**
 * 给文件系统套一层索引：目录树与正文优先从 [IndexStore] 取，取不到才落回真实文件系统。
 *
 * 这样 [dev.hibernalglow.grzeb.core.search.SearchEngine] 一行都不用改 ——
 * 它以为自己在遍历 SAF，实际上一旦预热完成，列目录与读文件就都成了内存 / 数据库命中。
 *
 * 三条设计约束：
 * - **按目录判空**：某目录在索引里有子项才走索引。预热是逐目录入库的，
 *   所以"库里没有"只意味着"这个目录还没预热到"，不能当成"它是空目录"。
 * - **预热完成要能自动切过来**：[snapshot] 每隔 [RECHECK_INTERVAL] 检查一次库的预热时间戳，
 *   变了就重建快照。没有这一步，预热是在用户停在当前页时完成的，
 *   而快照若只取一次，就永远停在"预热前"，快路径等于白做。
 * - **读到的正文顺手入库**（lastModified 记 null）：索引还没热就搜索时，读过的也算白赚；
 *   null 会让预热下次仍重读它一遍 —— 保守，但不会漏掉更新。
 */
class IndexedFileSystem(
    private val store: IndexStore,
    private val treeUri: String,
    private val delegate: GrzebFileSystem,
    /** 快照重建的检查间隔；测试传 [Duration.ZERO] 可以免去等待。 */
    private val recheckInterval: Duration = DEFAULT_RECHECK_INTERVAL,
) : GrzebFileSystem {

    override val isSupported: Boolean get() = delegate.isSupported

    private val lock = Mutex()
    private var childrenByParent: Map<String, List<FileEntry>> = emptyMap()
    private var snapshotWarmedAt: Long? = null
    private var lastChecked: TimeSource.Monotonic.ValueTimeMark = TimeSource.Monotonic.markNow()

    override suspend fun list(uri: String): List<FileEntry> {
        val snapshot = snapshot()
        // 库里没有这个目录的子项 → 还没预热到它，落回真实文件系统（只是慢，不会漏）
        return snapshot[uri] ?: delegate.list(uri)
    }

    override suspend fun readText(uri: String): String? {
        store.text(treeUri, uri)?.let { return it.text }
        val text = delegate.readText(uri) ?: return null
        store.putText(treeUri, uri, lastModified = null, text = text)
        return text
    }

    override fun displayName(uri: String): String = delegate.displayName(uri)

    override fun rootLocation(): String? = delegate.rootLocation()

    /** 丢掉快照，下次 [list] 重建（手动重建索引用）。 */
    suspend fun invalidate() = lock.withLock {
        childrenByParent = emptyMap()
        snapshotWarmedAt = null
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun snapshot(): Map<String, List<FileEntry>> {
        if (lastChecked.elapsedNow() < recheckInterval) return childrenByParent
        return lock.withLock {
            lastChecked = TimeSource.Monotonic.markNow()
            val warmedAt = store.status(treeUri).warmedAt
            if (warmedAt != snapshotWarmedAt || childrenByParent.isEmpty()) {
                childrenByParent = buildSnapshot()
                snapshotWarmedAt = warmedAt
            }
            childrenByParent
        }
    }

    private suspend fun buildSnapshot(): Map<String, List<FileEntry>> =
        store.entries(treeUri)
            .groupBy { it.parentUri.orEmpty() }
            .mapValues { (_, children) ->
                children.map {
                    FileEntry(
                        uri = it.uri,
                        name = it.name,
                        isDirectory = it.isDirectory,
                        size = it.size,
                        lastModified = it.lastModified,
                    )
                }
                    // 与两个平台实现保持同样的顺序：目录在前，再按名字排
                    .sortedWith(
                        compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.name.lowercase() },
                    )
            }

    private companion object {
        /** 快照重建的检查间隔：太密等于每个目录都查一次库，太疏则预热完成后迟迟切不过去。 */
        val DEFAULT_RECHECK_INTERVAL = 1.seconds
    }
}
