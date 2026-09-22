package dev.hibernalglow.grzeb.shared.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hibernalglow.grzeb.core.search.FileSearchResult
import dev.hibernalglow.grzeb.core.search.ScopePath
import dev.hibernalglow.grzeb.core.search.SearchMatch
import dev.hibernalglow.grzeb.core.search.SearchTreeNode
import dev.hibernalglow.grzeb.shared.ui.GrzebIcons

/**
 * 面包屑：整棵树的根 → 当前 scope，每一节都能点回去。
 *
 * 左边那个箭头就是"退回上一层"，和点上一节等价（窄屏上没有系统的返回键可用）。
 */
@Composable
fun ScopeBreadcrumb(
    path: List<ScopePath>,
    onNavigate: (ScopePath) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (path.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (path.size > 1) {
            Icon(
                imageVector = GrzebIcons.ChevronRight,
                contentDescription = "返回上一层",
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onNavigate(path[path.size - 2]) },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        path.forEachIndexed { index, segment ->
            val isCurrent = index == path.lastIndex
            Text(
                text = segment.name,
                style = MaterialTheme.typography.labelMedium,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.primary
                },
                maxLines = 1,
                modifier = Modifier
                    .clickable(enabled = !isCurrent) { onNavigate(segment) }
                    .padding(horizontal = 2.dp),
            )

            if (!isCurrent) {
                Text(
                    text = "›",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 结果树。
 *
 * 目录与文件是同一种节点，区别只在点下去做什么：
 * - 箭头 → 就地展开 / 折叠（目录展开出下一层，文件展开出各处理中）
 * - 点目录名 → 钻进这个范围（scope +1，面包屑变长）
 * - 点文件名 → 预览，跳到第一处命中
 * - 点某处命中 → 预览并跳到那一行
 *
 * 目录与文件都**默认全展开**（[collapsedFolders] / [collapsedFiles] 存的是被折叠的那些），
 * 一轮检索下来直接能看到完整层级和每文件的命中正文。
 */
@Composable
fun SearchResultTree(
    node: SearchTreeNode?,
    isLoading: Boolean,
    emptyText: String,
    collapsedFolders: Set<String>,
    collapsedFiles: Set<String>,
    selectedUri: String?,
    onToggleFolder: (String) -> Unit,
    onToggleFile: (String) -> Unit,
    onDrill: (String) -> Unit,
    onSelect: (FileSearchResult, SearchMatch) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (node == null) {
        when {
            isLoading -> CenteredHint("搜索中...", modifier)
            else -> CenteredHint(emptyText, modifier)
        }
        return
    }

    val rows = remember(node, collapsedFolders) { flatten(node, collapsedFolders) }

    Column(modifier.fillMaxSize()) {
        ScopeSummary(node, isLoading)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 20.dp),
        ) {
            items(rows, key = { it.node.uri }) { row ->
                val child = row.node
                if (child.isDirectory) {
                    FolderRow(
                        node = child,
                        depth = row.depth,
                        isExpanded = child.uri !in collapsedFolders,
                        onToggleExpand = { onToggleFolder(child.uri) },
                        onDrill = { onDrill(child.uri) },
                    )
                } else {
                    FileRow(
                        node = child,
                        depth = row.depth,
                        isExpanded = child.uri !in collapsedFiles,
                        isSelected = child.uri == selectedUri,
                        onToggleExpand = { onToggleFile(child.uri) },
                        onSelect = onSelect,
                    )
                }
            }
        }
    }
}

/** 当前 scope 的一行小计。 */
@Composable
private fun ScopeSummary(node: SearchTreeNode, isLoading: Boolean) {
    Text(
        text = if (isLoading) {
            "检索中… 已找到 ${node.fileCount} 个文件 · ${node.matchCount} 处命中"
        } else {
            "${node.fileCount} 个文件 · ${node.matchCount} 处命中"
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun FolderRow(
    node: SearchTreeNode,
    depth: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onDrill: () -> Unit,
) {
    val hasChildren = node.children.isNotEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDrill)
            .padding(start = indent(depth), top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Caret(visible = hasChildren, isExpanded = isExpanded, onClick = onToggleExpand)
        Icon(
            imageVector = GrzebIcons.Folder,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = node.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        MatchCountBadge(node.fileCount)
    }
}

@Composable
private fun FileRow(
    node: SearchTreeNode,
    depth: Int,
    isExpanded: Boolean,
    isSelected: Boolean,
    onToggleExpand: () -> Unit,
    onSelect: (FileSearchResult, SearchMatch) -> Unit,
) {
    val first = node.matches.firstOrNull()
    // 显式标成 () -> Unit：不然 `first?.let { … }` 会把返回类型推成 Unit?
    val select: () -> Unit = { first?.let { onSelect(asResult(node), it) } }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                )
                .clickable(onClick = select)
                .padding(start = indent(depth), top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Caret(
                visible = node.matches.size > 1,
                isExpanded = isExpanded,
                onClick = onToggleExpand,
            )
            Icon(
                imageVector = GrzebIcons.Description,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            MatchCountBadge(node.matchCount)
        }

        if (isExpanded) {
            Column(
                modifier = Modifier.padding(start = indent(depth) + 20.dp, end = 4.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val result = asResult(node)
                node.matches.forEach { match ->
                    MatchPreview(match = match, onClick = { onSelect(result, match) })
                }
            }
        }
    }
}

@Composable
private fun Caret(visible: Boolean, isExpanded: Boolean, onClick: () -> Unit) {
    if (!visible) {
        // 没有可展开的内容也要占位，否则同级节点的图标对不齐
        Box(Modifier.width(20.dp))
        return
    }
    Icon(
        imageVector = if (isExpanded) GrzebIcons.ExpandMore else GrzebIcons.ChevronRight,
        contentDescription = if (isExpanded) "折叠" else "展开",
        modifier = Modifier.size(20.dp).clickable(onClick = onClick),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun indent(depth: Int) = (8 + depth * 14).dp

/** 把树上的文件节点还原成检索结果，交给预览面板（它只认 [FileSearchResult]）。 */
private fun asResult(node: SearchTreeNode) = FileSearchResult(
    uri = node.uri,
    relPath = node.name,
    isDirectory = false,
    matches = node.matches,
    matchCount = node.matchCount,
)

private data class TreeRow(val node: SearchTreeNode, val depth: Int)

/** 按折叠状态把树摊平成一行行（只做一层懒加载，避免嵌套滚动）。 */
private fun flatten(root: SearchTreeNode, collapsedFolders: Set<String>): List<TreeRow> {
    val rows = mutableListOf<TreeRow>()

    fun walk(node: SearchTreeNode, depth: Int) {
        for (child in node.children) {
            rows += TreeRow(child, depth)
            // 文件不在这里下钻：它的命中由 FileRow 自己渲染
            if (child.isDirectory && child.uri !in collapsedFolders) walk(child, depth + 1)
        }
    }

    walk(root, 0)
    return rows
}
