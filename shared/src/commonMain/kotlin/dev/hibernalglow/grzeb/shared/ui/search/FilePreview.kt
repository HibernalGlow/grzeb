package dev.hibernalglow.grzeb.shared.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.hibernalglow.grzeb.core.fs.GrzebFileSystem
import dev.hibernalglow.grzeb.core.search.FileSearchResult
import dev.hibernalglow.grzeb.core.search.SearchMatch
import dev.hibernalglow.grzeb.shared.ui.GrzebIcons

/** 一次预览选择：文件 + 点中的那处命中，命中行号用来定位侧栏的滚动位置。 */
data class PreviewSelection(val file: FileSearchResult, val match: SearchMatch)

/**
 * 文件预览：整文件文本 + 全部命中高亮。
 *
 * 紧凑 / 中等窗口下它占满整屏，展开窗口下作为列表右侧的 supporting pane
 * （分流在 SearchScreen 里，见那里的 detailPaneWidth）。
 */@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilePreviewPane(
    selection: PreviewSelection,
    fileSystem: GrzebFileSystem,
    canReveal: Boolean,
    onDismiss: () -> Unit,
    onOpenExternally: (FileSearchResult) -> Unit,
    onReveal: (FileSearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val file = selection.file

    // 读文本按 uri 记：换文件时先回到 loading，否则会把上一个文件的内容闪一下
    var text by remember(file.uri, fileSystem) { mutableStateOf<String?>(null) }
    var isLoading by remember(file.uri, fileSystem) { mutableStateOf(true) }

    LaunchedEffect(file.uri, fileSystem) {
        text = fileSystem.readText(file.uri)
        isLoading = false
    }

    // 取到局部量再判空：委托属性没法智能转换
    val content = text

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = GrzebIcons.Description,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = file.relPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = GrzebIcons.Close,
                    contentDescription = "关闭预览",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${file.matchCount} 处命中",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
            // 侧栏最窄只有 280dp，按钮放流式行里，放不下就换行而不是被截断
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = { onOpenExternally(file) }) {
                    Text("在系统中打开", style = MaterialTheme.typography.labelSmall)
                }
                if (canReveal) {
                    TextButton(onClick = { onReveal(file) }) {
                        Text("在文件管理器中显示", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        HorizontalDivider()

        when {
            isLoading -> CenteredHint("读取中…", Modifier.fillMaxWidth().weight(1f))
            content == null -> CenteredHint(
                text = "无法预览该文件：可能是二进制、超过读取上限，或已在磁盘上改动",
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            else -> PreviewBody(
                text = content,
                file = file,
                focusLine = selection.match.lineNumber,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 全文 + 命中高亮；进入时把点中的那一行滚到可视区顶部附近。 */
@Composable
private fun PreviewBody(text: String, file: FileSearchResult, focusLine: Int, modifier: Modifier = Modifier) {
    // 行偏移要按原始行算（引擎用同一套 split('\n')），CRLF 的 \r 只在展示时去掉
    val rawLines = remember(text) { text.split('\n') }
    val lines = remember(rawLines) {
        rawLines.map { it.trimEnd('\r') }.let { if (it.lastOrNull()?.isEmpty() == true) it.dropLast(1) else it }
    }
    val rangesByLine = remember(rawLines, file) { matchColumns(rawLines, file.matches) }

    // 换文件要换一个新的滚动状态，否则侧栏会停在上一个文件的位置
    val listState = remember(file.uri) { LazyListState() }
    LaunchedEffect(file.uri, focusLine) {
        if (focusLine in lines.indices) listState.scrollToItem(focusLine)
    }

    // 行号列宽随总行数变，不然四位数行号会把正文挤掉一格
    val gutterWidth = remember(lines.size) { (lines.size.toString().length.coerceAtLeast(3) * 8 + 8).dp }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        itemsIndexed(lines) { index, line ->
            PreviewLine(
                number = index + 1,
                line = line,
                ranges = rangesByLine[index],
                isFocus = index == focusLine,
                gutterWidth = gutterWidth,
            )
        }
    }
}

@Composable
private fun PreviewLine(number: Int, line: String, ranges: List<IntRange>?, isFocus: Boolean, gutterWidth: Dp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isFocus) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f) else Color.Transparent
            )
            .padding(horizontal = 8.dp),
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(gutterWidth).padding(end = 8.dp),
        )
        Text(
            text = remember(line, ranges) { highlight(line, ranges) },
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun highlight(line: String, ranges: List<IntRange>?): AnnotatedString {
    // 行尾 \r 已在展示前去掉，落在它上面的区间要夹回行内
    val clipped = ArrayList<IntRange>(ranges?.size ?: 0)
    var cursor = 0
    ranges?.forEach { range ->
        val from = range.first.coerceIn(cursor, line.length)
        val to = (range.last + 1).coerceIn(from, line.length)
        if (to > from) {
            clipped += from until to
            cursor = to
        }
    }

    return buildAnnotatedString {
        var position = 0
        clipped.forEach { range ->
            append(line.substring(position, range.first))
            withStyle(SpanStyle(background = HighlightColor, fontWeight = FontWeight.Bold)) {
                append(line.substring(range.first, range.last + 1))
            }
            position = range.last + 1
        }
        append(line.substring(position))
    }
}

/**
 * 命中偏移换算成"行号 → 行内列区间"。
 *
 * [SearchMatch.start] / [end] 是整文件的字符偏移，与 [dev.hibernalglow.grzeb.core.fs.GrzebFileSystem.readText]
 * 读回的文本同源，所以按偏移夹到行内就行，不像结果卡片那样需要按文本回查。
 */
private fun matchColumns(lines: List<String>, matches: List<SearchMatch>): Map<Int, List<IntRange>> {
    if (matches.isEmpty()) return emptyMap()
    val sorted = matches.sortedBy { it.start }
    val out = mutableMapOf<Int, MutableList<IntRange>>()
    var index = 0
    var lineStart = 0

    lines.forEachIndexed { line, raw ->
        // +1 是被 split 吃掉的换行符，与引擎的 lineStarts 一致
        val nextLineStart = lineStart + raw.length + 1
        while (index < sorted.size && sorted[index].start < nextLineStart) {
            val match = sorted[index]
            val from = (match.start - lineStart).coerceIn(0, raw.length)
            val to = (match.end - lineStart).coerceIn(from, raw.length)
            if (to > from) out.getOrPut(line) { mutableListOf() }.add(from until to)
            index++
        }
        lineStart = nextLineStart
    }
    return out
}
