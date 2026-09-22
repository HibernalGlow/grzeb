package dev.hibernalglow.grzeb.core.search

import dev.hibernalglow.grzeb.core.fs.FileEntry
import dev.hibernalglow.grzeb.core.fs.GrzebFileSystem
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 多关键词 / 通配符在**整条检索链路**上的回归样本（假文件系统，不碰真磁盘）。
 *
 * 用一份"借条"当样本，因为它天然是"关键词分散在不同行"的形态：
 * 人名在第 1 行、卡号在第 4 行——正好是"多关键词可以不在同一行"要解决的场景。
 */
class SearchEngineTest {

    private val borrowNote = listOf(
        "借款人：张三",
        "身份证：1101**********1234",
        "开户行：招商银行",
        "卡号：6225 8888 6666 0000",
        "备注：已电话核实",
    ).joinToString("\n")

    private val logFile = listOf(
        "连接失败：timeout",
        "退避 1s",
        "重试 连接失败",
    ).joinToString("\n")

    private val files = mapOf(
        "/root/借条.txt" to borrowNote,
        "/root/日志.log" to logFile,
    )

    private val tree = mapOf(
        "/root" to listOf(
            entry("/root/借条.txt", "借条.txt"),
            entry("/root/日志.log", "日志.log"),
        ),
    )

    private fun search(options: SearchOptions): List<FileSearchResult> = runBlocking {
        SearchEngine(FakeFileSystem(tree, files), options).search("/root")
    }

    /** 核心命题：两个词分别落在不同行，文件依然算命中。 */
    @Test
    fun multiKeywordMatchesAcrossDifferentLines() {
        val results = search(SearchOptions(query = "张三 卡号", isMultiKeyword = true))

        assertEquals(1, results.size)
        val matches = results.single().matches
        assertEquals(setOf(0, 3), matches.map { it.lineNumber }.toSet())
        assertEquals("张三", matches.first().matchText)
        assertEquals("卡号", matches.last().matchText)
    }

    /** 无序：把词序调过来，结果一样。（有序版本见 [wildcardStarIsOrdered]） */
    @Test
    fun multiKeywordIsOrderIndependent() {
        val forward = search(SearchOptions(query = "张三 卡号", isMultiKeyword = true))
        val backward = search(SearchOptions(query = "卡号 张三", isMultiKeyword = true))

        assertEquals(forward.map { it.relPath }, backward.map { it.relPath })
    }

    /** 少一个词就不算命中：这是 AND，不是 OR。 */
    @Test
    fun multiKeywordRequiresEveryTerm() {
        assertTrue(search(SearchOptions(query = "张三 李四", isMultiKeyword = true)).isEmpty())
    }

    /** 排除词命中即整份文件出局。 */
    @Test
    fun exclusionDropsWholeFile() {
        assertEquals(1, search(SearchOptions(query = "连接", isMultiKeyword = true)).size)
        assertTrue(search(SearchOptions(query = "连接 -重试", isMultiKeyword = true)).isEmpty())
    }

    /** `*` 可以跨行，也就可以替代"同行相邻"的假设。 */
    @Test
    fun wildcardStarMatchesAcrossLines() {
        assertEquals(1, search(SearchOptions(query = "连接*重试", isWildcard = true)).size)
    }

    /** 有序：`*` 两侧不能调换，这是它与空格分词的关键差别。 */
    @Test
    fun wildcardStarIsOrdered() {
        assertEquals(1, search(SearchOptions(query = "张三*卡号", isWildcard = true)).size)
        assertTrue(search(SearchOptions(query = "卡号*张三", isWildcard = true)).isEmpty())
    }

    /** 修复用例：全词匹配 + 中文，改动之前这里恒为 0。 */
    @Test
    fun wholeWordChineseStillMatches() {
        assertEquals(1, search(SearchOptions(query = "张三", isWholeWord = true)).size)
    }

    /**
     * 「每文件首个」在多关键词下按"**每词**首个"理解：
     * 若仍按整份文件只留一处，其余词的位置会被整批丢掉，多关键词就白搜了。
     */
    @Test
    fun onlyFirstMatchKeepsOneHitPerTerm() {
        val all = search(SearchOptions(query = "连接 重试", isMultiKeyword = true))
        assertEquals(3, all.single().matchCount)

        val first = search(
            SearchOptions(query = "连接 重试", isMultiKeyword = true, isOnlyFirstMatch = true),
        )
        assertEquals(2, first.single().matchCount)
    }

    /** 默认配置（两个开关都不开）保持旧行为：整条 query 是一个字面量。 */
    @Test
    fun defaultOptionsKeepSingleLiteralSemantics() {
        assertEquals(1, search(SearchOptions(query = "张三")).size)
        assertTrue(search(SearchOptions(query = "张三 李四")).isEmpty())
    }

    private class FakeFileSystem(
        private val tree: Map<String, List<FileEntry>>,
        private val contents: Map<String, String>,
    ) : GrzebFileSystem {

        override val isSupported: Boolean = true

        override suspend fun list(uri: String): List<FileEntry> = tree[uri].orEmpty()

        override suspend fun readText(uri: String): String? = contents[uri]

        override fun displayName(uri: String): String = uri

        override fun rootLocation(): String? = "/root"
    }

    private companion object {
        fun entry(uri: String, name: String) =
            FileEntry(uri = uri, name = name, isDirectory = false, size = null)
    }
}
