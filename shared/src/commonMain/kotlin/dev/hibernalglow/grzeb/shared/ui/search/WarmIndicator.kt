package dev.hibernalglow.grzeb.shared.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hibernalglow.grzeb.core.index.IndexStatus
import dev.hibernalglow.grzeb.core.index.WarmProgress

/**
 * 索引预热状态条。放在检索进度条附近。
 *
 * 预热在后台跑，用户看不到过程会以为"没反应"，所以这里既报进度也报结果：
 * - 预热中：走一条不确定进度条 + "已写入 N 个文件（跳过 M 个）"
 * - 预热完：一行摘要，"跳过"那一项是增量生效的直观证据（第二次预热应当全部跳过）
 */
@Composable
fun WarmIndicator(
    progress: WarmProgress?,
    status: IndexStatus?,
    modifier: Modifier = Modifier,
) {
    if (progress == null && status == null) return

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (progress != null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = progress?.let { warmText(it) }
                    ?: status?.let { "索引就绪：${it.fileCount} 个文件 / ${it.textCount} 份正文" }
                    .orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun warmText(progress: WarmProgress): String {
    val head = progress.currentFile?.let { "正在建索引：$it" } ?: "正在建索引…"
    return "$head ｜ 已写入 ${progress.indexedFiles} 个文件，跳过 ${progress.reusedFiles} 个"
}
