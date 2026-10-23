package dev.hibernalglow.grzeb.shared.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.hibernalglow.grzeb.core.search.FileSearchResult
import dev.hibernalglow.grzeb.core.search.SearchMatch
import dev.hibernalglow.grzeb.shared.ui.GrzebIcons
import dev.hibernalglow.grzeb.shared.ui.theme.GrzebThemeDefaults

/** 结果列表，对应 react 版的 `SearchResultList`。点中一处命中即选定它的预览。 */
@Composable
fun SearchResultList(
    results: List<FileSearchResult>,
    isLoading: Boolean,
    emptyText: String,
    selectedUri: String?,
    onSelect: (FileSearchResult, SearchMatch) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        isLoading && results.isEmpty() -> CenteredHint("搜索中...", modifier)
        results.isEmpty() -> CenteredHint(emptyText, modifier)
        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 20.dp),
        ) {
            items(results) { result ->
                SearchResultCard(
                    result = result,
                    isSelected = result.uri == selectedUri,
                    onSelect = onSelect,
                )
            }
        }
    }
}

@Composable
internal fun CenteredHint(text: String, modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 24.dp, vertical = 18.dp),
        ) {
            Icon(
                imageVector = GrzebIcons.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * 单个文件的命中卡片。
 */
@Composable
private fun SearchResultCard(
    result: FileSearchResult,
    isSelected: Boolean,
    onSelect: (FileSearchResult, SearchMatch) -> Unit,
) {
    val collapsible = result.matches.size > 1
    var isOpen by remember(result.uri) { mutableStateOf(result.matches.size <= 3) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        border = if (isSelected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = collapsible) { isOpen = !isOpen }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (collapsible) {
                    Icon(
                        imageVector = if (isOpen) GrzebIcons.ExpandMore else GrzebIcons.ChevronRight,
                        contentDescription = if (isOpen) "折叠" else "展开",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (result.isDirectory) GrzebIcons.Folder else GrzebIcons.Description,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = result.relPath,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (result.matchCount > 0) {
                    MatchCountBadge(result.matchCount, isHighlighted = true)
                }
            }

            if (result.matches.isNotEmpty() && (!collapsible || isOpen)) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    result.matches.forEach { match ->
                        MatchPreview(match = match, onClick = { onSelect(result, match) })
                    }
                }
            }
        }
    }
}

/**
 * 计数徽章：
 * - [isHighlighted] = true 用于文件命中数（采用暖琥珀 `tertiaryContainer` 突出命中密度）
 * - [isHighlighted] = false 用于目录文件数（采用 `surfaceContainerHighest` 保持克制）
 */
@Composable
internal fun MatchCountBadge(count: Int, isHighlighted: Boolean = true) {
    val containerColor = if (isHighlighted) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = if (isHighlighted) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(containerColor)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = if (count > 999) "999+" else count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
        )
    }
}

/** 单处命中：紧凑 M3E 表面容器 + 左侧主色圆角指示条 + 等宽预览文本。 */
@Composable
internal fun MatchPreview(match: SearchMatch, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.small
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), shape)
            .drawBehind {
                val barWidth = 3.dp.toPx()
                drawRoundRect(
                    color = accent,
                    size = Size(width = barWidth, height = size.height),
                    cornerRadius = CornerRadius(barWidth, barWidth),
                )
            }
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
    ) {
        HighlightedText(match)
    }
}

/**
 * 带高亮的预览文本。
 *
 * 与 react 版一致：逐行渲染，行号列为等宽字体；只有命中行做高亮，
 * 且当该行被截断过（含 "..."）时，按文本重新定位命中位置——
 * 截断后的 [SearchMatch.indexInLine] 已经对不上了。
 */
@Composable
private fun HighlightedText(match: SearchMatch) {
    val extendedColors = GrzebThemeDefaults.extendedColors
    val codeStyle = GrzebThemeDefaults.codeTextStyle
    val lines = remember(match.preview) {
        match.preview.split('\n').let { if (it.size > 1 && it.last().isEmpty()) it.dropLast(1) else it }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        lines.forEachIndexed { index, line ->
            val absoluteLine = match.previewStartLine + index
            val isMatchLine = absoluteLine == match.lineNumber
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = (absoluteLine + 1).toString(),
                    style = codeStyle,
                    color = if (isMatchLine) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        extendedColors.codeGutterText
                    },
                    fontWeight = if (isMatchLine) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.width(38.dp).padding(end = 8.dp),
                )
                if (isMatchLine) {
                    val (start, end) = highlightRange(line, match)
                    Text(
                        text = buildAnnotatedString {
                            append(line.substring(0, start))
                            withStyle(
                                SpanStyle(
                                    background = extendedColors.matchHighlightContainer,
                                    color = extendedColors.onMatchHighlightContainer,
                                    fontWeight = FontWeight.Bold,
                                )
                            ) {
                                append(line.substring(start, end))
                            }
                            append(line.substring(end))
                        },
                        style = codeStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Text(
                        text = line,
                        style = codeStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * 命中在预览行内的区间。
 *
 * 预览行可能是被截断后的片段（`...`），此时 indexInLine 已失效，退化为按文本查找；
 * 无论如何都把区间夹到行内，避免 substring 越界。
 */
private fun highlightRange(line: String, match: SearchMatch): Pair<Int, Int> {
    var start = match.indexInLine
    if (line.contains("...")) {
        val found = line.lowercase().indexOf(match.matchText.lowercase())
        if (found >= 0) start = found
    }
    start = start.coerceIn(0, line.length)
    val end = (start + match.matchText.length).coerceIn(start, line.length)
    return start to end
}
