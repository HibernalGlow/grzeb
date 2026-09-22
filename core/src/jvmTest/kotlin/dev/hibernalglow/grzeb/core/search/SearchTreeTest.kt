package dev.hibernalglow.grzeb.core.search

import dev.hibernalglow.grzeb.core.index.InMemoryIndexStore
import dev.hibernalglow.grzeb.core.index.IndexStore
import dev.hibernalglow.grzeb.core.index.IndexedEntry
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 结果树的语义回归：聚合计数、按 scope 裁剪、钻取后的面包屑。
 *
 * 树形全靠索引库的 parentUri 链，所以这里先把一棵固定的树塞进 InMemoryIndexStore，
 * 再喂扁平的检索结果进去 —— 和真实链路的差别只是数据来源。
 */
class SearchTreeTest {

    private val root = "root://刘备"
    private val apr = "$root/23-04"
    private val may = "$root/23-05"

    private suspend fun store(): IndexStore = InMemoryIndexStore().apply {
        putEntries(
            root,
            listOf(
                IndexedEntry(root, null, "刘备", isDirectory = true, size = null, lastModified = null),
                IndexedEntry(apr, root, "23-04", isDirectory = true, size = null, lastModified = 1L),
                IndexedEntry(may, root, "23-05", isDirectory = true, size = null, lastModified = 1L),
            ),
        )
        putEntries(
            root,
            listOf(
                IndexedEntry("$apr/a.txt", apr, "a.txt", isDirectory = false, size = 1L, lastModified = 1L),
                IndexedEntry("$apr/b.txt", apr, "b.txt", isDirectory = false, size = 1L, lastModified = 1L),
                IndexedEntry("$may/c.txt", may, "c.txt", isDirectory = false, size = 1L, lastModified = 1L),
                IndexedEntry("$root/d.txt", root, "d.txt", isDirectory = false, size = 1L, lastModified = 1L),
            ),
        )
    }

    private fun result(uri: String, name: String, matchCount: Int) = FileSearchResult(
        uri = uri,
        relPath = name,
        matchCount = matchCount,
        matches = List(matchCount) { index ->
            SearchMatch(
                matchText = "刘备",
                start = index,
                end = index + 2,
                lineNumber = index,
                preview = "第 $index 行 刘备",
                previewStartLine = index,
                indexInLine = 0,
            )
        },
    )

    private fun allResults() = listOf(
        result("$apr/a.txt", "a.txt", 3),
        result("$apr/b.txt", "b.txt", 1),
        result("$may/c.txt", "c.txt", 2),
        result("$root/d.txt", "d.txt", 5),
    )

    @Test
    fun aggregatesCountsUpTheTree() = runBlocking {
        val tree = SearchTreeBuilder(store(), root).build(allResults(), root)

        assertEquals(4, tree.fileCount)
        assertEquals(11, tree.matchCount)
        assertTrue(tree.isDirectory)

        // 目录在前、其余按名字排
        assertEquals(listOf("23-04", "23-05", "d.txt"), tree.children.map { it.name })

        val april = tree.children.first { it.name == "23-04" }
        assertEquals(2, april.fileCount)
        assertEquals(4, april.matchCount)
        assertEquals(listOf("a.txt", "b.txt"), april.children.map { it.name })

        // 文件节点要带着原始命中，目录节点不带
        val d = tree.children.first { it.name == "d.txt" }
        assertEquals(1, d.fileCount)
        assertEquals(5, d.matchCount)
        assertEquals(5, d.matches.size)
        assertTrue(april.matches.isEmpty())
    }

    @Test
    fun drilledScopeOnlyKeepsItsOwnSubtree() = runBlocking {
        val tree = SearchTreeBuilder(store(), root).build(allResults(), apr)

        assertEquals(apr, tree.uri)
        assertEquals(2, tree.fileCount, "钻到 23-04 后不该把 23-05 和根下的结果算进来")
        assertEquals(4, tree.matchCount)
        assertEquals(listOf("a.txt", "b.txt"), tree.children.map { it.name })
    }

    @Test
    fun unknownFileIsKeptFlatUnderScope() = runBlocking {
        // 索引还没热完时，某些文件不在库里，上溯不出位置 —— 宁可平铺也不能丢
        val results = allResults() + result("$may/未入库.txt", "未入库.txt", 7)
        val tree = SearchTreeBuilder(store(), root).build(results, root)

        assertEquals(5, tree.fileCount)
        assertEquals(18, tree.matchCount)
        assertTrue(tree.children.any { it.name == "未入库.txt" }, "位置未知的文件应当挂在 scope 下")
    }

    @Test
    fun scopePathGivesBreadcrumbFromRoot() = runBlocking {
        val builder = SearchTreeBuilder(store(), root)

        assertEquals(listOf("刘备", "23-04"), builder.scopePath(apr).map { it.name })
        assertEquals(listOf("刘备"), builder.scopePath(root).map { it.name })
        assertEquals(listOf(root, apr), builder.scopePath(apr).map { it.uri })
    }
}
