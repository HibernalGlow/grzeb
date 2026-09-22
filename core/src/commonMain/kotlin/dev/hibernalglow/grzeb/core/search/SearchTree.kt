package dev.hibernalglow.grzeb.core.search

import dev.hibernalglow.grzeb.core.index.IndexStore
import dev.hibernalglow.grzeb.core.index.IndexedEntry

/**
 * 结果树上的一个节点。
 *
 * 目录是聚合节点（只有计数，没有命中），文件是叶子（带完整命中列表）。
 * [fileCount] / [matchCount] 都是**整个子树**的合计 —— 点一个目录能看出"进去还有多少东西"。
 */
data class SearchTreeNode(
    val uri: String,
    val name: String,
    val isDirectory: Boolean,
    /** 该子树下命中的文件数。 */
    val fileCount: Int,
    /** 该子树下的命中处数。 */
    val matchCount: Int,
    /** 文件节点自己的命中；目录节点为空。 */
    val matches: List<SearchMatch> = emptyList(),
    val children: List<SearchTreeNode> = emptyList(),
) {
    val isFile: Boolean get() = !isDirectory
}

/** 面包屑上的一节。 */
data class ScopePath(
    val uri: String,
    val name: String,
)

/**
 * 把扁平结果整理成"可钻取的结果树"。
 *
 * 目录层级不是从结果里猜的，而是查索引库的 `parentUri` 链 —— 这样平台无关
 * （Android 的 SAF URI 与桌面的绝对路径都能用），而且不依赖检索引擎改动。
 *
 * 三个约定：
 * - **只保留落在当前 scope 里的命中**：钻到 `crates/renderer` 后，树里不会混进 `app/` 的结果。
 * - **位置未知的文件直接挂在 scope 下**：索引还没热完就搜索时，某些文件不在库里，
 *   上溯不出位置。这时宁可把它平铺在 scope 下，也不能丢。
 * - **目录在前、其余按名字排**：与文件系统实现里的顺序一致。
 */
class SearchTreeBuilder(
    private val store: IndexStore,
    private val treeUri: String,
) {

    /**
     * 以 [scopeUri] 为根，把 [results] 整理成树。
     *
     * [scopeUri] 就是返回的根节点（钻取栈的当前层），它的 children 是这一层要展示的
     * 子目录与文件。
     */
    suspend fun build(results: List<FileSearchResult>, scopeUri: String): SearchTreeNode {
        val entries = store.entries(treeUri).associateBy { it.uri }

        // 目录 uri → 子节点；自底向上建，最后再算计数
        val folderChildren = mutableMapOf<String, MutableList<SearchTreeNode>>()

        for (result in results) {
            val chain = folderChain(entries, result.uri, scopeUri) ?: continue

            var parent = scopeUri
            for (folderUri in chain) {
                val siblings = folderChildren.getOrPut(parent) { mutableListOf() }
                if (siblings.none { it.uri == folderUri }) {
                    siblings += SearchTreeNode(
                        uri = folderUri,
                        name = entries[folderUri]?.name ?: folderUri.substringAfterLast('/'),
                        isDirectory = true,
                        fileCount = 0,
                        matchCount = 0,
                    )
                }
                parent = folderUri
            }
            folderChildren.getOrPut(parent) { mutableListOf() } += SearchTreeNode(
                uri = result.uri,
                name = result.relPath,
                isDirectory = false,
                fileCount = 1,
                matchCount = result.matchCount,
                matches = result.matches,
            )
        }

        return assemble(scopeUri, entries[scopeUri]?.name, folderChildren)
    }

    /** 从整棵树的根到 [scopeUri] 的链路，顺序是 根 → 当前（面包屑直接用）。 */
    suspend fun scopePath(scopeUri: String): List<ScopePath> {
        val entries = store.entries(treeUri).associateBy { it.uri }
        val path = mutableListOf<ScopePath>()

        var cursor: String? = scopeUri
        var guard = 0
        while (cursor != null && guard++ < MAX_DEPTH) {
            path += ScopePath(cursor, entries[cursor]?.name ?: cursor.substringAfterLast('/'))
            if (cursor == treeUri) break
            cursor = entries[cursor]?.parentUri
        }

        path.reverse()
        if (path.firstOrNull()?.uri != treeUri) {
            // 链路断在库里没有的目录上：补上整棵树的根，别让面包屑少一截
            path.add(0, ScopePath(treeUri, entries[treeUri]?.name ?: treeUri.substringAfterLast('/')))
        }
        return path
    }

    /** 递归累加计数并排序：目录在前，再按名字（不区分大小写）。 */
    private fun assemble(
        uri: String,
        name: String?,
        folderChildren: Map<String, List<SearchTreeNode>>,
    ): SearchTreeNode {
        val children = folderChildren[uri].orEmpty().map { child ->
            if (child.isDirectory) {
                assemble(child.uri, child.name, folderChildren)
            } else {
                child
            }
        }.sortedWith(
            compareByDescending<SearchTreeNode> { it.isDirectory }.thenBy { it.name.lowercase() },
        )

        return SearchTreeNode(
            uri = uri,
            name = name ?: uri.substringAfterLast('/'),
            isDirectory = true,
            fileCount = children.sumOf { it.fileCount },
            matchCount = children.sumOf { it.matchCount },
            children = children,
        )
    }

    /**
     * 从 [uri] 的父目录一路上溯到 [scopeUri]，返回中间经过的目录（外层在前）。
     *
     * - 落在 [scopeUri] 里 → 返回经过的目录（可能为空：文件就在 scope 直属下）
     * - 位置不在库里 → 返回空列表（当作直接挂在 scope 下，宁可不分层也不丢结果）
     * - 上溯到顶都没遇到 [scopeUri] → 返回 null（这个结果不属于当前范围，丢掉）
     */
    private fun folderChain(
        entries: Map<String, IndexedEntry>,
        uri: String,
        scopeUri: String,
    ): List<String>? {
        val own = entries[uri] ?: return emptyList()

        val inner = mutableListOf<String>()
        var cursor = own.parentUri
        var guard = 0
        while (cursor != null && guard++ < MAX_DEPTH) {
            if (cursor == scopeUri) return inner.asReversed()
            inner += cursor
            cursor = entries[cursor]?.parentUri
        }
        return null
    }

    private companion object {
        /** 目录层数上限，纯粹是防库里出现环导致的死循环。 */
        const val MAX_DEPTH = 64
    }
}
