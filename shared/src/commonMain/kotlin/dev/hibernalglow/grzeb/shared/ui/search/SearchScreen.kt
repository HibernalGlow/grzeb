package dev.hibernalglow.grzeb.shared.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hibernalglow.grzeb.core.fs.GrzebFileSystem
import dev.hibernalglow.grzeb.core.index.IndexStore
import dev.hibernalglow.grzeb.core.index.IndexedFileSystem
import dev.hibernalglow.grzeb.core.search.FileSearchResult
import dev.hibernalglow.grzeb.core.search.SearchProgress
import dev.hibernalglow.grzeb.shared.platform.PlatformServices
import dev.hibernalglow.grzeb.shared.ui.DefaultPaneWidth
import dev.hibernalglow.grzeb.shared.ui.GrzebIcons
import dev.hibernalglow.grzeb.shared.ui.MinListWidth
import dev.hibernalglow.grzeb.shared.ui.MinPaneWidth
import dev.hibernalglow.grzeb.shared.ui.PaneDividerWidth
import dev.hibernalglow.grzeb.shared.ui.PaneResizeHandle
import dev.hibernalglow.grzeb.shared.ui.detailPaneWidth
import dev.hibernalglow.grzeb.shared.ui.maxContentWidth
import dev.hibernalglow.grzeb.shared.ui.windowSizeClassOf
import kotlinx.coroutines.launch

/**
 * 检索页，对应 react 版 `app/search.tsx`。
 *
 * 自上而下：目录行 → 输入行 → 可折叠选项 → 分隔线 → 进度 / 统计 → 结果列表。
 *
 * 宽窗口按 M3 自适应分流（见 [dev.hibernalglow.grzeb.shared.ui.WindowSizeClass]）：
 * 展开窗口里预览进右侧 supporting pane，紧凑 / 中等窗口里仍占满整屏；
 * 单栏时正文限宽居中，不再整页拉伸。
 */
@Composable
fun SearchScreen(services: PlatformServices, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val state = remember { SearchUiState() }
    // Android 的 tree uri 变了就要换实例（实例持有那次授权），桌面端则一直是同一个
    val baseFileSystem = remember(state.directory) { services.createFileSystem(state.directory) }
    // 检索走索引包装：预热完成后列目录、读正文都不再碰真实文件系统（见 core 的 IndexedFileSystem）
    val indexStore = remember(services) { services.createIndexStore() }
    val fileSystem = remember(baseFileSystem, indexStore) {
        IndexedFileSystem(indexStore, state.directory.orEmpty(), baseFileSystem)
    }

    // 回填上次用过的目录（索引库里记着）；没有记录再退回平台默认目录 ——
    // 桌面是用户主目录，Android 未授权时为 null，保持"请先选择目录"
    LaunchedEffect(baseFileSystem, indexStore) {
        if (state.directory == null) {
            state.directory = indexStore.lastRoot()?.treeUri ?: baseFileSystem.rootLocation()
        }
    }

    // 选中目录就后台预热（可取消、带进度）：目录项与正文先入库，之后的检索直接打索引。
    // 预热读的是**没套索引的** baseFileSystem —— 套了索引的实例在预热完成前是空的。
    LaunchedEffect(state.directory) {
        if (state.directory != null) state.warm(scope, indexStore, baseFileSystem)
    }

    val openExternally: (FileSearchResult) -> Unit = { result ->
        scope.launch { services.openExternally(result.uri) }
    }

    val reveal: (FileSearchResult) -> Unit = { result ->
        scope.launch { services.revealInFileManager(result.uri) }
    }

    // 侧栏宽度存成 Float dp：Dp 是 value class，进不了 rememberSaveable
    var paneWidthDp by rememberSaveable { mutableStateOf(DefaultPaneWidth.value) }

    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val windowClass = windowSizeClassOf(maxWidth)
        val paneWidth = windowClass.detailPaneWidth
        val preview = state.preview

        when {
            preview != null && paneWidth != null -> {
                // 侧栏宽度可拖：最窄 280dp，最宽不超过半屏且给列表留 360dp
                val maxPane = minOf(maxWidth * 0.5f, maxWidth - MinListWidth - PaneDividerWidth)
                    .coerceAtLeast(MinPaneWidth)
                val pane = paneWidthDp.dp.coerceIn(MinPaneWidth, maxPane)
                // 整组仍是"单栏上限 + 侧栏"再居中：拖手柄只改两侧比例，不动外边界
                val shellWidth = minOf(
                    maxWidth,
                    (windowClass.maxContentWidth ?: maxWidth) + pane + PaneDividerWidth,
                )
                val listWidth = shellWidth - pane - PaneDividerWidth
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center) {
                    SearchPane(
                        state = state,
                        services = services,
                        fileSystem = fileSystem,
                        indexStore = indexStore,
                        modifier = Modifier.width(listWidth).fillMaxHeight(),
                    )
                    PaneResizeHandle(
                        onDrag = { delta -> paneWidthDp = (paneWidthDp + delta.value).coerceIn(0f, 4000f) },
                        onReset = { paneWidthDp = DefaultPaneWidth.value },
                        modifier = Modifier.width(PaneDividerWidth).fillMaxHeight(),
                    )
                    FilePreviewPane(
                        selection = preview,
                        fileSystem = fileSystem,
                        canReveal = services.isRevealSupported,
                        onDismiss = { state.preview = null },
                        onOpenExternally = openExternally,
                        onReveal = reveal,
                        modifier = Modifier.width(pane).fillMaxHeight(),
                    )
                }
            }

            preview != null -> FilePreviewPane(
                selection = preview,
                fileSystem = fileSystem,
                canReveal = services.isRevealSupported,
                onDismiss = { state.preview = null },
                onOpenExternally = openExternally,
                onReveal = reveal,
                modifier = Modifier.widthIn(max = windowClass.maxContentWidth ?: maxWidth).fillMaxSize(),
            )

            else -> SearchPane(
                state = state,
                services = services,
                fileSystem = fileSystem,
                indexStore = indexStore,
                modifier = Modifier.widthIn(max = windowClass.maxContentWidth ?: maxWidth).fillMaxSize(),
            )
        }
    }
}

/** 检索正文：控件区 + 结果列表。紧凑时是整页，展开时是左侧列表栏。 */
@Composable
private fun SearchPane(
    state: SearchUiState,
    services: PlatformServices,
    fileSystem: GrzebFileSystem,
    indexStore: IndexStore,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    Column(modifier) {
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
                onSearch = { state.search(scope, fileSystem, indexStore) },
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

        WarmIndicator(progress = state.warmProgress, status = state.indexStatus)

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

        // 面包屑：整棵树的根 → 当前 scope。点哪一节就回到哪一层，等价于浏览器后退。
        if (state.tree != null) {
            ScopeBreadcrumb(
                path = state.scopePath,
                onNavigate = { segment -> state.drillInto(segment.uri, scope, indexStore) },
            )
        }

        SearchResultTree(
            node = state.tree,
            isLoading = state.isSearching,
            emptyText = if (state.hasSearched) "没有找到匹配的结果" else "选择目录并输入关键词后开始检索",
            collapsedFolders = state.collapsedFolders,
            collapsedFiles = state.collapsedFiles,
            selectedUri = state.preview?.file?.uri,
            onToggleFolder = state::toggleFolder,
            onToggleFile = state::toggleFile,
            onDrill = { uri -> state.drillInto(uri, scope, indexStore) },
            onSelect = { file, match -> state.preview = PreviewSelection(file, match) },
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
