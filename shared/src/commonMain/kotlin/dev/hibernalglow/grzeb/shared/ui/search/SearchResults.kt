package dev.hibernalglow.grzeb.shared.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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

/** 命中文本的底色，取自 react 版的 `bg-yellow-500/30`。 */
internal val HighlightColor = Color(0x4DEAB308)

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
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp),
        )
    }
}

/**
 * 单个文件的命中卡片。
 *
 * 多于一处的文件可以折叠；只有一处时始终展开（react 版折叠后仍会把那一处显示在下面，
 * 这里直接合并成一种状态）。
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = result.relPath,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (result.matchCount > 0) {
                    MatchCountBadge(result.matchCount)
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

@Composable
internal fun MatchCountBadge(count: Int) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = if (count > 999) "999+" else count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 单处命中：左侧一条主色竖线 + 等宽的预览文本。 */
@Composable
internal fun MatchPreview(match: SearchMatch, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.small
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .drawBehind {
                drawRect(
                    color = accent,
                    size = Size(width = 2.dp.toPx(), height = size.height),
                )
            }
            .clickable(onClick = onClick)
            .padding(8.dp),
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
    val lines = remember(match.preview) {
        match.preview.split('\n').let { if (it.size > 1 && it.last().isEmpty()) it.dropLast(1) else it }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        lines.forEachIndexed { index, line ->
            val absoluteLine = match.previewStartLine + index
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = (absoluteLine + 1).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.width(40.dp).padding(end = 6.dp),
                )
                if (absoluteLine == match.lineNumber) {
                    val (start, end) = highlightRange(line, match)
                    Text(
                        text = buildAnnotatedString {
                            append(line.substring(0, start))
                            withStyle(SpanStyle(background = HighlightColor, fontWeight = FontWeight.Bold)) {
                                append(line.substring(start, end))
                            }
                            append(line.substring(end))
                        },
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Text(
                        text = line,
                        style = MaterialTheme.typography.labelSmall,
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
