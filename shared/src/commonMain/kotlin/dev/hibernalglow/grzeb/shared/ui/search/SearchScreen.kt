package dev.hibernalglow.grzeb.shared.ui.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hibernalglow.grzeb.core.fs.GrzebFileSystem
import dev.hibernalglow.grzeb.core.index.IndexStatus
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
import dev.hibernalglow.grzeb.shared.ui.settings.SettingsDialog
import dev.hibernalglow.grzeb.shared.ui.windowSizeClassOf
import kotlinx.coroutines.launch

/**
 * 检索页，对应 react 版 `app/search.tsx`。
 *
 * 自上而下：目录区 → 主搜索操作组 → 筛选器区 → 进度 / 统计 → 面包屑 → 结果树。
 *
 * 宽窗口按 M3 自适应分流（见 [dev.hibernalglow.grzeb.shared.ui.WindowSizeClass]）：
 * 展开窗口里预览进右侧 supporting pane，紧凑 / 中等窗口里仍占满整屏；
 * 单栏时正文限宽居中，不再整页拉伸。
 */
@Composable
fun SearchScreen(
    services: PlatformServices,
    state: SearchUiState = remember { SearchUiState() },
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
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

    // 预热行为受 isWarmEnabled 开关控制（默认关闭，不进行自动预热）：
    // 只有在开关开启且已选择目录时才在后台建立快照；关闭时若在预热中则主动取消。
    LaunchedEffect(state.directory, state.isWarmEnabled) {
        if (state.directory != null && state.isWarmEnabled) {
            state.warm(scope, indexStore, baseFileSystem)
        } else if (!state.isWarmEnabled && state.warmProgress != null) {
            state.cancelWarm()
        }
    }

    if (state.showSettings) {
        SettingsDialog(
            state = state,
            baseFileSystem = baseFileSystem,
            indexStore = indexStore,
            onDismiss = { state.showSettings = false },
        )
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

/** 检索正文：M3E 操作面板 + 结果树。紧凑时是整页，展开时是左侧列表栏。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DirectoryRow(
                    isSupported = services.isDirectoryPickerSupported,
                    selected = state.directory != null,
                    label = state.directory?.let(fileSystem::displayName),
                    isWarming = state.warmProgress != null,
                    indexStatus = state.indexStatus,
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
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        AnimatedVisibility(
            visible = state.progress != null,
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
            exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            state.progress?.let { ProgressArea(it) }
        }

        WarmIndicator(progress = state.warmProgress, status = state.indexStatus)

        AnimatedVisibility(
            visible = state.results.isNotEmpty() && !state.isSearching,
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
            exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            ResultSummary(
                fileCount = state.results.size,
                matchCount = state.totalMatches,
                onClear = state::clearResults,
            )
        }

        state.error?.let { message ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
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

/** 目录行：独立表面层级 + 文件夹胶囊图标 + 索引状态徽章 + 桌面悬停手势。 */
@Composable
private fun DirectoryRow(
    isSupported: Boolean,
    selected: Boolean,
    label: String?,
    isWarming: Boolean,
    indexStatus: IndexStatus?,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), shape)
            .then(
                if (isSupported) {
                    Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(onClick = onPick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(MaterialTheme.shapes.small)
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (selected) GrzebIcons.FolderOpen else GrzebIcons.Folder,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (selected) "工作区目录" else "未选择目录",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = label ?: if (isSupported) "点击选择搜索目录..." else "当前平台暂未接入目录访问",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                ),
                color = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (selected) {
            val badgeContainer = when {
                isWarming -> MaterialTheme.colorScheme.tertiaryContainer
                indexStatus?.isWarmed == true -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainerHighest
            }
            val badgeContent = when {
                isWarming -> MaterialTheme.colorScheme.onTertiaryContainer
                indexStatus?.isWarmed == true -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val badgeText = when {
                isWarming -> "预热中"
                indexStatus?.isWarmed == true -> "${indexStatus.fileCount} 文件"
                else -> "实时检索"
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(badgeContainer)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeContent,
                )
            }
        }

        if (selected) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(onClick = onClear),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = GrzebIcons.Close,
                    contentDescription = "清除已选目录",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 主搜索操作组：突出输入框与具备 Expressive 按压形变的搜索/取消按钮。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
    canSearch: Boolean,
    onSearch: () -> Unit,
    onCancel: () -> Unit,
) {
    val fastEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.large,
            placeholder = {
                Text(
                    text = "输入关键词或正则表达式... (Enter 开始搜索)",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = GrzebIcons.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            trailingIcon = {
                if (query.isNotEmpty() && !isSearching) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            imageVector = GrzebIcons.Close,
                            contentDescription = "清空输入",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            singleLine = true,
            enabled = !isSearching,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        )

        val buttonContainerColor by animateColorAsState(
            targetValue = if (isSearching) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primary
            },
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        )
        val buttonContentColor by animateColorAsState(
            targetValue = if (isSearching) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onPrimary
            },
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        )

        Button(
            onClick = if (isSearching) onCancel else onSearch,
            enabled = isSearching || canSearch,
            shapes = ButtonDefaults.shapes(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 15.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = buttonContainerColor,
                contentColor = buttonContentColor,
            ),
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        ) {
            AnimatedContent(
                targetState = isSearching,
                transitionSpec = {
                    fadeIn(fastEffectsSpec) togetherWith fadeOut(fastEffectsSpec)
                },
            ) { searching ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (searching) GrzebIcons.Close else GrzebIcons.Search,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (searching) "取消" else "搜索",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

/**
 * 检索筛选器区：用状态明确的 M3 [FilterChip] 替代原本松散的复选框行，
 * 折叠时仍显示当前已启用的筛选条件数量，展开/收起采用 Expressive 弹簧曲线。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OptionsSection(isOpen: Boolean, onToggle: () -> Unit, state: SearchUiState) {
    val activeCount = listOf(
        state.isCaseSensitive,
        state.isRegex,
        state.isWholeWord,
        state.isOnlyFirstMatch,
    ).count { it }

    val chevronRotation by animateFloatAsState(
        targetValue = if (isOpen) 90f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )

    Column {
        Row(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = onToggle)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = GrzebIcons.ChevronRight,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = chevronRotation },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = GrzebIcons.Tune,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "检索筛选器",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (activeCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 7.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = "已启用 $activeCount 项",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            if (state.customExtensions.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(horizontal = 7.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = "+${state.customExtensions.size} 格式",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            if (state.isWarmEnabled) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 7.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = "预热开启",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isOpen,
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
            exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SearchOptionChip(
                    codePrefix = "Aa",
                    label = "区分大小写",
                    selected = state.isCaseSensitive,
                    onClick = { state.toggleOption(SearchUiState.Option.CaseSensitive) },
                )
                SearchOptionChip(
                    codePrefix = ".*",
                    label = "正则表达式",
                    selected = state.isRegex,
                    onClick = { state.toggleOption(SearchUiState.Option.Regex) },
                )
                SearchOptionChip(
                    codePrefix = "\\b",
                    label = "全词匹配",
                    selected = state.isWholeWord,
                    onClick = { state.toggleOption(SearchUiState.Option.WholeWord) },
                )
                SearchOptionChip(
                    codePrefix = "1×",
                    label = "每文件首个",
                    selected = state.isOnlyFirstMatch,
                    onClick = { state.toggleOption(SearchUiState.Option.OnlyFirstMatch) },
                )
                FilterChip(
                    selected = false,
                    onClick = { state.showSettings = true },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    leadingIcon = {
                        Icon(
                            imageVector = GrzebIcons.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    label = {
                        Text(
                            text = if (state.customExtensions.isNotEmpty()) {
                                "格式与预热 (+${state.customExtensions.size})"
                            } else {
                                "格式与预热"
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchOptionChip(
    codePrefix: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val fastEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    FilterChip(
        selected = selected,
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        leadingIcon = {
            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    fadeIn(fastEffectsSpec) togetherWith fadeOut(fastEffectsSpec)
                },
            ) { isSelected ->
                if (isSelected) {
                    Icon(
                        imageVector = GrzebIcons.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Text(
                        text = codePrefix,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            selectedBorderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
        ),
    )
}

/**
 * 进度区：使用 M3 Expressive 的 [LinearWavyProgressIndicator] 表达平滑波浪进度的扫描状态。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProgressArea(progress: SearchProgress) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LinearWavyProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = progress.currentFile?.let { "正在检索: $it" } ?: "准备中...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "${progress.scannedFiles} 文件 · ${progress.totalMatches} 命中",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

/** 结果统计条：紧凑 M3E 胶囊徽章 + 动作按钮。 */
@Composable
private fun ResultSummary(fileCount: Int, matchCount: Int, onClear: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "命中 $fileCount 个文件",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            MatchCountBadge(count = matchCount, isHighlighted = true)
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = onClear,
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            ) {
                Text("清空结果", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
