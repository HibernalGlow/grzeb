package dev.hibernalglow.grzeb.shared.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
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
 * 采用胶囊表面层级区分可回退节点与当前所在节点，并支持桌面光标提示与平滑弹簧动效。
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
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (path.size > 1) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { onNavigate(path[path.size - 2]) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = GrzebIcons.ChevronRight,
                    contentDescription = "返回上一层",
                    modifier = Modifier
                        .size(14.dp)
                        .graphicsLayer { rotationZ = 180f },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        path.forEachIndexed { index, segment ->
            val isCurrent = index == path.lastIndex
            val isRoot = index == 0
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(
                        if (isCurrent) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            Color.Transparent
                        }
                    )
                    .then(
                        if (!isCurrent) {
                            Modifier
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable { onNavigate(segment) }
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (isRoot) {
                        Icon(
                            imageVector = GrzebIcons.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = if (isCurrent) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                    Text(
                        text = segment.name,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        maxLines = 1,
                    )
                }
            }

            if (!isCurrent) {
                Text(
                    text = "›",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

/**
 * 结果树。
 *
 * 目录与文件是同一种节点，区别只在点下去做什么：
 * - 箭头 → 就地展开 / 折叠（目录展开出下一层，文件展开出各处命中）
 * - 点目录名 → 钻进这个范围（scope +1，面包屑变长）
 * - 点文件名 → 预览，跳到第一处命中
 * - 点某处命中 → 预览并跳到那一行
 *
 * 保持紧凑的信息密度，不给每一行包厚重卡片，靠缩进、图标色彩、选中态容器色（secondaryContainer）
 * 和琥珀色命中计数徽章传达层级。
 */
@Composable
fun SearchResultTree(
    node: SearchTreeNode?,
    isLoading: Boolean,
    emptyText: String,
    collapsedFolders: Set<String>,
    collapsedFiles: Set<String>,
    selectedUri: String?,
    /** 滚动状态由调用方持有：紧凑窗口的预览会顶掉整个面板，状态放里面会跟着销毁。 */
    listState: LazyListState,
    onToggleFolder: (String) -> Unit,
    onToggleFile: (String) -> Unit,
    onDrill: (String) -> Unit,
    onSelect: (FileSearchResult, SearchMatch) -> Unit,
    onExpandAll: () -> Unit = {},
    onCollapseAll: () -> Unit = {},
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
        ScopeSummary(node, isLoading, onExpandAll, onCollapseAll)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
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

/** 当前 scope 的一行小计与快捷全部展开/折叠。 */
@Composable
private fun ScopeSummary(
    node: SearchTreeNode,
    isLoading: Boolean,
    onExpandAll: () -> Unit,
    onCollapseAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = if (isLoading) {
                "检索中… 已找到 ${node.fileCount} 个文件 · ${node.matchCount} 处命中"
            } else {
                "${node.fileCount} 个文件 · ${node.matchCount} 处匹配"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(onClick = onExpandAll)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "全部展开",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(onClick = onCollapseAll)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "全部折叠",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
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
            .padding(vertical = 1.dp)
            .clip(MaterialTheme.shapes.small)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onToggleExpand)
            .padding(start = indent(depth), top = 7.dp, bottom = 7.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Caret(visible = hasChildren, isExpanded = isExpanded, onClick = onToggleExpand)
        Icon(
            imageVector = if (isExpanded) GrzebIcons.FolderOpen else GrzebIcons.Folder,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.secondary,
        )
        Text(
            text = node.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        MatchCountBadge(count = node.matchCount.takeIf { it > 0 } ?: node.fileCount, isHighlighted = false)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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

    val rowContainerColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
    )

    val rowContentColor = if (isSelected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp)
                .clip(MaterialTheme.shapes.small)
                .background(rowContainerColor)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = select)
                .padding(start = indent(depth), top = 7.dp, bottom = 7.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = rowContentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val dirPart = node.relPath?.substringBeforeLast('/', "")?.ifEmpty { null }
                if (dirPart != null && dirPart != node.name) {
                    Text(
                        text = dirPart,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            MatchCountBadge(count = node.matchCount, isHighlighted = true)
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
            exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            Column(
                modifier = Modifier.padding(start = indent(depth) + 22.dp, end = 4.dp, top = 2.dp, bottom = 6.dp),
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Caret(visible: Boolean, isExpanded: Boolean, onClick: () -> Unit) {
    if (!visible) {
        // 没有可展开的内容也要占位，否则同级节点的图标对不齐
        Box(Modifier.width(20.dp))
        return
    }
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = GrzebIcons.ChevronRight,
            contentDescription = if (isExpanded) "折叠" else "展开",
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer { rotationZ = rotation },
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun indent(depth: Int) = (8 + depth * 14).dp

/** 把树上的文件节点还原成检索结果，交给预览面板（它只认 [FileSearchResult]）。 */
private fun asResult(node: SearchTreeNode) = FileSearchResult(
    uri = node.uri,
    relPath = node.relPath ?: node.name,
    isDirectory = false,
    matches = node.matches,
    matchCount = node.matchCount,
    rootUri = node.rootUri,
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
