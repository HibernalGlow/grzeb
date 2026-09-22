package dev.hibernalglow.grzeb.shared.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hibernalglow.grzeb.core.search.SearchProgress
import dev.hibernalglow.grzeb.shared.platform.PlatformServices
import dev.hibernalglow.grzeb.shared.ui.GrzebIcons
import kotlinx.coroutines.launch

/**
 * 检索页，对应 react 版 `app/search.tsx`。
 *
 * 自上而下：目录行 → 输入行 → 可折叠选项 → 分隔线 → 进度 / 统计 → 结果列表。
 */
@Composable
fun SearchScreen(services: PlatformServices, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val state = remember { SearchUiState() }
    // Android 的 tree uri 变了就要换实例（实例持有那次授权），桌面端则一直是同一个
    val fileSystem = remember(state.directory) { services.createFileSystem(state.directory) }

    // 桌面端预填主目录；Android 未授权时 rootLocation() 为 null，保持"请先选择目录"
    LaunchedEffect(fileSystem) {
        if (state.directory == null) {
            fileSystem.rootLocation()?.let { state.directory = it }
        }
    }

    Column(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DirectoryRow(
                isSupported = services.isDirectoryPickerSupported,
                selected = state.directory != null,
                label = state.directory?.let(fileSystem::displayName),
                onPick = { scope.launch { services.pickDirectory()?.let { state.directory = it } } },
                onClear = { state.directory = null },
            )

            SearchRow(
                query = state.query,
                onQueryChange = { state.query = it },
                isSearching = state.isSearching,
                canSearch = state.canSearch,
                onSearch = { state.search(scope, fileSystem) },
                onCancel = state::cancel,
            )

            OptionsSection(
                isOpen = state.showOptions,
                onToggle = { state.showOptions = !state.showOptions },
                state = state,
            )
        }

        HorizontalDivider()

        state.progress?.let { ProgressArea(it) }

        if (state.results.isNotEmpty() && !state.isSearching) {
            ResultSummary(
                fileCount = state.results.size,
                matchCount = state.totalMatches,
                onClear = state::clearResults,
            )
        }

        state.error?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        SearchResultList(
            results = state.results,
            isLoading = state.isSearching,
            emptyText = if (state.hasSearched) "没有找到匹配的结果" else "选择目录并输入关键词后开始检索",
            onOpen = { result -> scope.launch { services.openExternally(result.uri) } },
            modifier = Modifier.weight(1f),
        )
    }
}

/** 目录行：点击选择；已选时右侧给一个清除按钮。 */
@Composable
private fun DirectoryRow(
    isSupported: Boolean,
    selected: Boolean,
    label: String?,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(enabled = isSupported, onClick = onPick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = GrzebIcons.Folder,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label ?: if (isSupported) "点击选择搜索目录..." else "当前平台暂未接入目录访问",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = GrzebIcons.Close,
                contentDescription = "清除已选目录",
                modifier = Modifier.size(16.dp).clickable(onClick = onClear),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 输入行：输入框 + 搜索 / 取消按钮。 */
@Composable
private fun SearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
    canSearch: Boolean,
    onSearch: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("输入搜索关键词...") },
            singleLine = true,
            enabled = !isSearching,
            trailingIcon = {
                if (query.isNotEmpty() && !isSearching) {
                    Icon(
                        imageVector = GrzebIcons.Close,
                        contentDescription = "清空输入",
                        modifier = Modifier.size(16.dp).clickable { onQueryChange("") },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        )

        Button(onClick = if (isSearching) onCancel else onSearch, enabled = isSearching || canSearch) {
            Icon(
                imageVector = if (isSearching) GrzebIcons.Close else GrzebIcons.Search,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(if (isSearching) "取消" else "搜索")
        }
    }
}

/** 可折叠的检索选项，对应 react 版的 CollapsibleSection + 四个 Checkbox。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionsSection(isOpen: Boolean, onToggle: () -> Unit, state: SearchUiState) {
    Column {
        Row(
            modifier = Modifier.clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = if (isOpen) GrzebIcons.ExpandMore else GrzebIcons.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "搜索选项",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (isOpen) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OptionCheckbox("区分大小写", state.isCaseSensitive) {
                    state.toggleOption(SearchUiState.Option.CaseSensitive)
                }
                OptionCheckbox("正则表达式", state.isRegex) {
                    state.toggleOption(SearchUiState.Option.Regex)
                }
                OptionCheckbox("全词匹配", state.isWholeWord) {
                    state.toggleOption(SearchUiState.Option.WholeWord)
                }
                OptionCheckbox("每文件首个", state.isOnlyFirstMatch) {
                    state.toggleOption(SearchUiState.Option.OnlyFirstMatch)
                }
            }
        }
    }
}

@Composable
private fun OptionCheckbox(label: String, checked: Boolean, onCheckedChange: () -> Unit) {
    Row(
        modifier = Modifier.clickable(onClick = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // 点击交给整行处理，Checkbox 自己不再收事件，避免一次点击翻两次
        Checkbox(checked = checked, onCheckedChange = null)
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * 进度区。
 *
 * core 的 [SearchProgress] 只有"已扫过多少"，没有总数，所以这里用不确定进度条：
 * react 版那个 50% → 100% 是写死的假值，没有照搬。
 */
@Composable
private fun ProgressArea(progress: SearchProgress) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = progress.currentFile?.let { "正在搜索: $it" } ?: "准备中...",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${progress.scannedFiles} 文件 | ${progress.totalMatches} 匹配",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 结果统计条。 */
@Composable
private fun ResultSummary(fileCount: Int, matchCount: Int, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "找到 $fileCount 个文件，共 $matchCount 处匹配",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onClear) {
            Text("清空", style = MaterialTheme.typography.labelSmall)
        }
    }
}
