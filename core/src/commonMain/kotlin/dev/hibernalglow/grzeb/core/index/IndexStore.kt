package dev.hibernalglow.grzeb.core.index

/**
 * 索引里的一条目录项（目录树快照的一行）。
 *
 * 与 [dev.hibernalglow.grzeb.core.fs.FileEntry] 的区别：多了 [parentUri]。
 * 快照要能脱离文件系统独立还原出树形结构，而 FileEntry 只有"列目录时拿到的当下结果"。
 */
data class IndexedEntry(
    val uri: String,
    val parentUri: String?,
    val name: String,
    val isDirectory: Boolean,
    val size: Long?,
    val lastModified: Long?,
)

/** 某个根目录的索引状态。 */
data class IndexStatus(
    /** 上次预热完成的时间（epoch millis）；从没预热过为 null。 */
    val warmedAt: Long?,
    val fileCount: Int,
    val textCount: Int,
) {
    val isWarmed: Boolean get() = warmedAt != null
}

/** 正文缓存条目。[lastModified] 是写入时的文件修改时间，用来判断缓存是否过期。 */
data class CachedText(
    val text: String,
    val lastModified: Long?,
)

/**
 * 检索索引库。
 *
 * 存两样东西：目录树快照（[IndexedEntry]）与解码后的正文（[CachedText]）。
 * 有了它们，二次检索不必再走平台文件系统列目录、读文件、猜编码 —— 这正是"预热"的意义。
 *
 * 没有上 FTS：检索要支持正则 / 全词匹配 / 大小写敏感，这些语义 FTS 服务不了；
 * 真正的收益来自"正文进了库"。
 *
 * 实现方负责把阻塞的数据库调用挪出调用者线程。
 */
interface IndexStore {

    suspend fun status(treeUri: String): IndexStatus

    /** 该根目录下的全部条目（一次查出来，遍历在内存里做）。 */
    suspend fun entries(treeUri: String): List<IndexedEntry>

    /** 写入 / 更新若干条目；已存在则覆盖（增量刷新用）。 */
    suspend fun putEntries(treeUri: String, entries: List<IndexedEntry>)

    /** 删除若干条目（连同它们的正文缓存）——刷新时用来清掉已经消失的文件。 */
    suspend fun removeEntries(treeUri: String, uris: Collection<String>)

    /** 取正文缓存；没有则返回 null。 */
    suspend fun text(treeUri: String, uri: String): CachedText?

    suspend fun putText(treeUri: String, uri: String, lastModified: Long?, text: String)

    suspend fun dropText(treeUri: String, uri: String)

    /** 记录一次预热完成；[displayName] 供 UI 展示。 */
    suspend fun markWarmed(treeUri: String, displayName: String?, at: Long)

    /** 清空该根目录的全部索引与缓存（重建索引用）。 */
    suspend fun clear(treeUri: String)
}

/**
 * 内存实现：Web 端会话内缓存，以及单测用。
 *
 * 不落盘，进程结束即失效；Web 端本来也没有目录访问，所以只当它是"有总比没有好"的一层。
 */
class InMemoryIndexStore : IndexStore {

    private val entries = mutableMapOf<String, LinkedHashMap<String, IndexedEntry>>()
    private val texts = mutableMapOf<String, LinkedHashMap<String, CachedText>>()
    private val warmedAt = mutableMapOf<String, Long>()
    private val names = mutableMapOf<String, String?>()

    override suspend fun status(treeUri: String): IndexStatus = IndexStatus(
        warmedAt = warmedAt[treeUri],
        fileCount = entries[treeUri].orEmpty().values.count { !it.isDirectory },
        textCount = texts[treeUri].orEmpty().size,
    )

    override suspend fun entries(treeUri: String): List<IndexedEntry> =
        entries[treeUri].orEmpty().values.toList()

    override suspend fun putEntries(treeUri: String, entries: List<IndexedEntry>) {
        val target = this.entries.getOrPut(treeUri) { LinkedHashMap() }
        for (entry in entries) target[entry.uri] = entry
    }

    override suspend fun removeEntries(treeUri: String, uris: Collection<String>) {
        val target = entries[treeUri] ?: return
        val cached = texts[treeUri]
        for (uri in uris) {
            target.remove(uri)
            cached?.remove(uri)
        }
    }

    override suspend fun text(treeUri: String, uri: String): CachedText? = texts[treeUri]?.get(uri)

    override suspend fun putText(treeUri: String, uri: String, lastModified: Long?, text: String) {
        texts.getOrPut(treeUri) { LinkedHashMap() }[uri] = CachedText(text, lastModified)
    }

    override suspend fun dropText(treeUri: String, uri: String) {
        texts[treeUri]?.remove(uri)
    }

    override suspend fun markWarmed(treeUri: String, displayName: String?, at: Long) {
        warmedAt[treeUri] = at
        names[treeUri] = displayName
    }

    override suspend fun clear(treeUri: String) {
        entries.remove(treeUri)
        texts.remove(treeUri)
        warmedAt.remove(treeUri)
        names.remove(treeUri)
    }
}
