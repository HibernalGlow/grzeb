package dev.hibernalglow.grzeb.core.index

import dev.hibernalglow.grzeb.core.fs.FileEntry
import dev.hibernalglow.grzeb.core.fs.GrzebFileSystem
import dev.hibernalglow.grzeb.core.fs.JvmGrzebFileSystem
import dev.hibernalglow.grzeb.core.search.SearchOptions
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * 包装器的两条核心行为：没预热就落回真实文件系统，预热完自动切到索引。
 *
 * 用计数代理来证明"真的没碰文件系统"——只看返回值是看不出来的（两边结果一样）。
 */
class IndexedFileSystemTest {

    /** 转发给真实实现，同时数一下被调用了多少次。 */
    private class CountingFileSystem(private val delegate: GrzebFileSystem) : GrzebFileSystem {
        var listCalls = 0
            private set
        var readCalls = 0
            private set

        fun resetCounts() {
            listCalls = 0
            readCalls = 0
        }

        override val isSupported: Boolean get() = delegate.isSupported

        override suspend fun list(uri: String): List<FileEntry> {
            listCalls++
            return delegate.list(uri)
        }

        override suspend fun readText(uri: String): String? {
            readCalls++
            return delegate.readText(uri)
        }

        override fun displayName(uri: String): String = delegate.displayName(uri)

        override fun rootLocation(): String? = delegate.rootLocation()
    }

    @Test
    fun fallsBackToDelegateBeforeWarmThenSwitchesToIndex() = runBlocking {
        val dir = Files.createTempDirectory("grzeb-indexed-fs").toFile()
        try {
            File(dir, "a.txt").writeText("刘备 踩脚袜", Charsets.UTF_8)
            File(dir, "b.txt").writeText("粮草", Charsets.UTF_8)

            val store = InMemoryIndexStore()
            val counting = CountingFileSystem(JvmGrzebFileSystem(dir.absolutePath))
            val indexed = IndexedFileSystem(
                store = store,
                treeUri = dir.absolutePath,
                delegate = counting,
                recheckInterval = Duration.ZERO,
            )

            // ① 还没预热：必须落回真实文件系统，否则会给出"空目录"
            val cold = indexed.list(dir.absolutePath)
            assertEquals(2, cold.size)
            assertEquals(1, counting.listCalls)

            // ② 预热（走真实文件系统），然后同一次会话里再列目录
            IndexWarmer(counting, store, SearchOptions()).warm(dir.absolutePath)
            // 预热自己也要列目录，先把计数清零再验"这次没碰文件系统"
            counting.resetCounts()

            val warm = indexed.list(dir.absolutePath)
            assertEquals(2, warm.size)
            assertEquals(0, counting.listCalls, "预热完成后不该再列真实目录")

            // ③ 正文：索引里命中就不再读文件
            counting.resetCounts()
            val uri = warm.first { it.name == "a.txt" }.uri
            val text = indexed.readText(uri)
            assertNotNull(text)
            assertTrue(text.contains("踩脚袜"))
            assertEquals(0, counting.readCalls)
        } finally {
            dir.deleteRecursively()
        }
    }

    /** 冷索引下读文件：仍然要读得到，并且顺手把正文存进缓存。 */
    @Test
    fun readThroughCachesTextOnDemand() = runBlocking {
        val dir = Files.createTempDirectory("grzeb-indexed-cold").toFile()
        try {
            File(dir, "a.txt").writeText("随手缓存", Charsets.UTF_8)

            val store = InMemoryIndexStore()
            val counting = CountingFileSystem(JvmGrzebFileSystem(dir.absolutePath))
            val indexed = IndexedFileSystem(store, dir.absolutePath, counting, Duration.ZERO)

            val uri = File(dir, "a.txt").absolutePath
            assertEquals("随手缓存", indexed.readText(uri))
            assertEquals(1, counting.readCalls)

            // 第二次应该吃缓存
            assertEquals("随手缓存", indexed.readText(uri))
            assertEquals(1, counting.readCalls, "第二次读同一文件应命中缓存")
        } finally {
            dir.deleteRecursively()
        }
    }
}
