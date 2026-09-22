package dev.hibernalglow.grzeb.core.index

import dev.hibernalglow.grzeb.core.fs.JvmGrzebFileSystem
import dev.hibernalglow.grzeb.core.search.SearchOptions
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 预热的语义回归：首次全建、二次全复用、改过的重读、删掉的清干净。
 *
 * 这几个断言就是"预热真的省掉了 IO"的依据 —— 第二次的 `indexedFiles` 必须是 0。
 */
class IndexWarmerTest {

    private fun gbk(text: String): ByteArray = text.toByteArray(Charset.forName("GB18030"))

    @Test
    fun warmThenReuseThenRefreshChangedAndDeleted() = runBlocking {
        val dir = Files.createTempDirectory("grzeb-warm").toFile()
        try {
            val novel = File(dir, "刘备.txt")
            novel.writeBytes(gbk("第一段：他站在廊下。\n第二段：踩脚袜。"))

            val sub = File(dir, "sub").apply { mkdirs() }
            File(sub, "b.txt").writeText("子目录里的 UTF-8 文本", Charsets.UTF_8)
            File(sub, "c.bin").writeBytes(byteArrayOf(0, 1, 2, 3, 0, 5))

            val store = InMemoryIndexStore()
            val fileSystem = JvmGrzebFileSystem(dir.absolutePath)
            var progress: WarmProgress? = null
            val warmer = IndexWarmer(fileSystem, store, SearchOptions()) { progress = it }

            // ① 首次：三个文件都进目录项，二进制那条不进正文
            val first = warmer.warm(dir.absolutePath)
            assertEquals(3, first.fileCount)
            assertEquals(2, first.textCount)
            assertTrue(first.isWarmed)

            // GBK 正文要能被正确读出来（顺带验了编码探测走通预热链路）
            val novelEntry = store.entries(dir.absolutePath).first { it.name == "刘备.txt" }
            val cached = store.text(dir.absolutePath, novelEntry.uri)
            assertNotNull(cached)
            assertTrue(cached.text.contains("踩脚袜"), "GBK 正文应被正确解码：${cached.text.take(40)}")

            // ② 再来一次：正文一条都不重读
            progress = null
            val second = warmer.warm(dir.absolutePath)
            assertEquals(2, second.textCount)
            assertEquals(0, progress!!.indexedFiles, "二次预热不该重读任何文件")
            assertEquals(2, progress!!.reusedFiles)

            // ③ 改过的文件要重读（显式挪 mtime，免得同一秒内写入被当成没变）
            novel.writeBytes(gbk("改过的内容 踩脚袜"))
            novel.setLastModified(novel.lastModified() + 60_000)

            progress = null
            warmer.warm(dir.absolutePath)
            assertEquals(1, progress!!.indexedFiles)
            assertEquals(1, progress!!.reusedFiles)
            val updated = store.text(dir.absolutePath, novelEntry.uri)
            assertNotNull(updated)
            assertTrue(updated.text.startsWith("改过的内容"))

            // ④ 删掉的文件：目录项与正文都要清掉，否则缓存会让它继续出现在检索结果里
            File(sub, "b.txt").delete()
            val afterDelete = warmer.warm(dir.absolutePath)
            assertEquals(2, afterDelete.fileCount)
            assertEquals(1, afterDelete.textCount)

            val deletedUri = store.entries(dir.absolutePath).firstOrNull { it.name == "b.txt" }
            assertNull(deletedUri, "已删除的文件不该还留在索引里")
        } finally {
            dir.deleteRecursively()
        }
    }
}
