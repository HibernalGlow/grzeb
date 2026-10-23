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
    val rootUri: String? = null,
    val relPath: String? = null,
) {
    val isFile: Boolean get() = !isDirectory
}

/** 面包屑上的一节。 */
data class ScopePath(
    val uri: String,
    val name: String,
)

/**
 * 把扁平结果整理成"类似 VSCode 的分层结果树"。
 *
 * 支持：
 * - 多工作区根目录并行检索与层级聚合；
 * - 结合 SQLite 缓存或根据文件相对路径 [relPath] 自动解析多层目录结构；
 * - 始终呈现清晰、可就地折叠/展开与下钻的树形体验，告别扁平列表。
 */
class SearchTreeBuilder(
    private val store: IndexStore? = null,
    private val treeUri: String,
    private val rootDisplayNames: Map<String, String> = emptyMap(),
) {

    /**
     * 以 [scopeUri] 为根，把 [results] 整理成树。
     *
     * [scopeUri] 就是返回的根节点（钻取栈的当前层），它的 children 是这一层要展示的
     * 子目录与文件。
     */
    suspend fun build(results: List<FileSearchResult>, scopeUri: String): SearchTreeNode {
        val distinctRoots = results.mapNotNull { it.rootUri }.distinct()
        val isMultiRoot = treeUri == MULTI_ROOT_URI || (distinctRoots.size > 1 && scopeUri == MULTI_ROOT_URI)

        if (isMultiRoot && scopeUri == MULTI_ROOT_URI) {
            // 多工作区模式顶层：为每个根目录创建子树
            val groupedByRoot = results.groupBy { it.rootUri ?: treeUri }
            val rootChildren = mutableListOf<SearchTreeNode>()

            for ((root, rootResults) in groupedByRoot) {
                val rootName = rootDisplayNames[root] ?: root.substringAfterLast('/').ifEmpty { root }
                val rootSubtree = buildSingleRoot(rootResults, root, root, rootName)
                rootChildren += rootSubtree
            }

            rootChildren.sortWith(
                compareByDescending<SearchTreeNode> { it.isDirectory }.thenBy { it.name.lowercase() }
            )

            return SearchTreeNode(
                uri = MULTI_ROOT_URI,
                name = "工作区",
                isDirectory = true,
                fileCount = rootChildren.sumOf { it.fileCount },
                matchCount = rootChildren.sumOf { it.matchCount },
                children = rootChildren,
            )
        }

        // 单工作区或已钻入某一特定根目录/子目录
        val currentRoot = if (isMultiRoot) {
            distinctRoots.firstOrNull { scopeUri.startsWith(it) } ?: treeUri
        } else {
            treeUri
        }
        val rootName = rootDisplayNames[currentRoot] ?: currentRoot.substringAfterLast('/').ifEmpty { currentRoot }
        return buildSingleRoot(results, currentRoot, scopeUri, rootName)
    }

    private suspend fun buildSingleRoot(
        results: List<FileSearchResult>,
        rootUri: String,
        scopeUri: String,
        rootDisplayName: String?,
    ): SearchTreeNode {
        val entries = store?.entries(rootUri)?.associateBy { it.uri }.orEmpty()

        // 目录 uri → 子节点；自底向上建，最后再算计数
        val folderChildren = mutableMapOf<String, MutableList<SearchTreeNode>>()
        val folderNames = mutableMapOf<String, String>()

        for (result in results) {
            // 如果指定了 rootUri，且结果显式带着其它 rootUri，则跳过
            if (result.rootUri != null && result.rootUri != rootUri && rootUri != MULTI_ROOT_URI) {
                continue
            }

            // 1. 优先查索引库链条
            val dbChain = folderChain(entries, result.uri, scopeUri)

            if (dbChain != null && dbChain.isNotEmpty()) {
                var parent = scopeUri
                for (folderUri in dbChain) {
                    val siblings = folderChildren.getOrPut(parent) { mutableListOf() }
                    val name = entries[folderUri]?.name ?: folderUri.substringAfterLast('/')
                    folderNames[folderUri] = name
                    if (siblings.none { it.uri == folderUri }) {
                        siblings += SearchTreeNode(
                            uri = folderUri,
                            name = name,
                            isDirectory = true,
                            fileCount = 0,
                            matchCount = 0,
                            rootUri = rootUri,
                        )
                    }
                    parent = folderUri
                }
                val fileName = entries[result.uri]?.name ?: result.relPath.substringAfterLast('/')
                folderChildren.getOrPut(parent) { mutableListOf() } += SearchTreeNode(
                    uri = result.uri,
                    name = fileName,
                    isDirectory = false,
                    fileCount = 1,
                    matchCount = result.matchCount,
                    matches = result.matches,
                    rootUri = rootUri,
                    relPath = result.relPath,
                )
            } else if (dbChain != null && dbChain.isEmpty()) {
                // dbChain 为空：可能是文件就在当前 scope 根下，或者未预热根据 relPath 拆分目录
                val normalizedRelPath = result.relPath.replace('\\', '/').trim('/')
                val parts = normalizedRelPath.split('/').filter { it.isNotEmpty() }

                if (parts.size > 1 && scopeUri == rootUri) {
                    // 根据相对路径分级展示多层目录结构
                    var parent = scopeUri
                    val dirParts = parts.dropLast(1)
                    val fileName = parts.last()

                    var pathAcc = scopeUri
                    for (part in dirParts) {
                        pathAcc = if (pathAcc.endsWith('/')) "$pathAcc$part" else "$pathAcc/$part"
                        val folderUri = pathAcc
                        folderNames[folderUri] = part
                        val siblings = folderChildren.getOrPut(parent) { mutableListOf() }
                        if (siblings.none { it.uri == folderUri }) {
                            siblings += SearchTreeNode(
                                uri = folderUri,
                                name = part,
                                isDirectory = true,
                                fileCount = 0,
                                matchCount = 0,
                                rootUri = rootUri,
                            )
                        }
                        parent = folderUri
                    }

                    folderChildren.getOrPut(parent) { mutableListOf() } += SearchTreeNode(
                        uri = result.uri,
                        name = fileName,
                        isDirectory = false,
                        fileCount = 1,
                        matchCount = result.matchCount,
                        matches = result.matches,
                        rootUri = rootUri,
                        relPath = result.relPath,
                    )
                } else {
                    // 文件直属当前 scope
                    val fileName = parts.lastOrNull() ?: result.relPath
                    folderChildren.getOrPut(scopeUri) { mutableListOf() } += SearchTreeNode(
                        uri = result.uri,
                        name = fileName,
                        isDirectory = false,
                        fileCount = 1,
                        matchCount = result.matchCount,
                        matches = result.matches,
                        rootUri = rootUri,
                        relPath = result.relPath,
                    )
                }
            }
        }

        val scopeName = if (scopeUri == rootUri) {
            rootDisplayName ?: entries[scopeUri]?.name ?: scopeUri.substringAfterLast('/')
        } else {
            folderNames[scopeUri] ?: entries[scopeUri]?.name ?: scopeUri.substringAfterLast('/')
        }

        return assemble(scopeUri, scopeName, folderChildren, rootUri)
    }

    /** 从整棵树的根到 [scopeUri] 的链路，顺序是 根 → 当前（面包屑直接用）。 */
    suspend fun scopePath(scopeUri: String): List<ScopePath> {
        if (scopeUri == MULTI_ROOT_URI) {
            return listOf(ScopePath(MULTI_ROOT_URI, "工作区"))
        }

        val entries = store?.entries(treeUri)?.associateBy { it.uri }.orEmpty()
        val path = mutableListOf<ScopePath>()

        var cursor: String? = scopeUri
        var guard = 0
        while (cursor != null && guard++ < MAX_DEPTH) {
            val name = if (cursor == treeUri) {
                rootDisplayNames[cursor] ?: cursor.substringAfterLast('/').ifEmpty { cursor }
            } else {
                entries[cursor]?.name ?: cursor.substringAfterLast('/')
            }
            path += ScopePath(cursor, name)
            if (cursor == treeUri) break
            cursor = entries[cursor]?.parentUri
        }

        path.reverse()
        if (path.firstOrNull()?.uri != treeUri && treeUri != MULTI_ROOT_URI) {
            // 链路断在库里没有的目录上：补上整棵树的根，别让面包屑少一截
            val rootName = rootDisplayNames[treeUri] ?: treeUri.substringAfterLast('/').ifEmpty { treeUri }
            path.add(0, ScopePath(treeUri, rootName))
        }

        if (treeUri == MULTI_ROOT_URI) {
            path.add(0, ScopePath(MULTI_ROOT_URI, "工作区"))
        }

        return path
    }

    /** 递归累加计数并排序：目录在前，再按名字（不区分大小写）。 */
    private fun assemble(
        uri: String,
        name: String?,
        folderChildren: Map<String, List<SearchTreeNode>>,
        rootUri: String,
    ): SearchTreeNode {
        val children = folderChildren[uri].orEmpty().map { child ->
            if (child.isDirectory) {
                assemble(child.uri, child.name, folderChildren, rootUri)
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
            rootUri = rootUri,
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

    companion object {
        const val MULTI_ROOT_URI = "grzeb://workspace"

        /** 目录层数上限，纯粹是防库里出现环导致的死循环。 */
        private const val MAX_DEPTH = 64
    }
}
