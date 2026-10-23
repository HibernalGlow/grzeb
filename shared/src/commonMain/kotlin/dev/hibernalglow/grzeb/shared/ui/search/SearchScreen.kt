package dev.hibernalglow.grzeb.shared.ui.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import dev.hibernalglow.grzeb.shared.ui.components.Material3SettingsGroup
import dev.hibernalglow.grzeb.shared.ui.components.Material3SettingsItem
import dev.hibernalglow.grzeb.shared.ui.components.SettingsPalettes
import dev.hibernalglow.grzeb.shared.ui.detailPaneWidth
import dev.hibernalglow.grzeb.shared.ui.maxContentWidth
import dev.hibernalglow.grzeb.shared.ui.settings.SettingsDialog
import dev.hibernalglow.grzeb.shared.ui.windowSizeClassOf
import kotlinx.coroutines.launch

/**
 * 检索主界面：
 * 全面遵循 Material 3 Expressive 设计规范与 Rhythm 的设计语言架构：
 * 1. 底部停靠/浮动搜索栏（Bottom Docked Search Bar），贴合拇指交互，支持键盘弹出与手势缩放。
 * 2. 搜索历史（Search History）列表，支持关键词回填、单项移除与一键清空。
 * 3. 分类矩阵（Browse by Category），采用连通响应式圆角与水印背景。
 * 4. 表现力空状态（Expressive Empty Prompt），采用多层次卫星曲奇图形结构。
 * 5. 自适应分流（Supporting Pane），大屏支持文件预览与拖动手柄。
 */
@Composable
fun SearchScreen(
    services: PlatformServices,
    state: SearchUiState = remember { SearchUiState() },
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val baseFileSystem = remember(state.directory) { services.createFileSystem(state.directory) }
    val indexStore = remember(services) { services.createIndexStore() }
    val fileSystem = remember(baseFileSystem, indexStore) {
        IndexedFileSystem(indexStore, state.directory.orEmpty(), baseFileSystem)
    }

    LaunchedEffect(baseFileSystem, indexStore) {
        if (state.directory == null) {
            state.directory = indexStore.lastRoot()?.treeUri ?: baseFileSystem.rootLocation()
        }
    }

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
            onPickDirectory = { scope.launch { services.pickDirectory()?.let { state.addDirectory(it) } } },
            onDismiss = { state.showSettings = false },
        )
    }

    val openExternally: (FileSearchResult) -> Unit = { result ->
        scope.launch { services.openExternally(result.uri) }
    }

    val reveal: (FileSearchResult) -> Unit = { result ->
        scope.launch { services.revealInFileManager(result.uri) }
    }

    var paneWidthDp by rememberSaveable { mutableStateOf(DefaultPaneWidth.value) }

    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val windowClass = windowSizeClassOf(maxWidth)
        val paneWidth = windowClass.detailPaneWidth
        val preview = state.preview

        when {
            preview != null && paneWidth != null -> {
                val maxPane = minOf(maxWidth * 0.5f, maxWidth - MinListWidth - PaneDividerWidth)
                    .coerceAtLeast(MinPaneWidth)
                val pane = paneWidthDp.dp.coerceIn(MinPaneWidth, maxPane)
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
                        isWide = false,
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
                isWide = maxWidth >= 600.dp,
                modifier = Modifier.widthIn(max = windowClass.maxContentWidth ?: maxWidth).fillMaxSize(),
            )
        }
    }
}

/**
 * Expressive 检索面板：主体滚动区 + 底部停靠搜索栏。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchPane(
    state: SearchUiState,
    services: PlatformServices,
    fileSystem: GrzebFileSystem,
    indexStore: IndexStore,
    isWide: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部工作区目录卡片（轻巧精致）
            WorkspaceDirectoryHeader(
                selected = state.directories.isNotEmpty() || state.directory != null,
                label = if (state.directories.size > 1) {
                    "${state.directories.size} 个工作区 (${state.directories.joinToString { services.createFileSystem(it).displayName(it) }})"
                } else {
                    state.directory?.let(fileSystem::displayName)
                },
                isWarming = state.warmProgress != null,
                indexStatus = state.indexStatus,
                isSupported = services.isDirectoryPickerSupported,
                onPick = { scope.launch { services.pickDirectory()?.let { state.addDirectory(it) } } },
                onClear = { state.clearDirectories() },
            )

            // 主展示区：未搜索时展示历史 + 分类 / 空状态；搜索中有结果时展示结果树
            val isBrowsingState = state.results.isEmpty() && !state.isSearching

            if (isBrowsingState) {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // 1. 搜索历史（若存在）
                    if (state.searchHistory.isNotEmpty()) {
                        item(key = "history_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "最近搜索",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                                TextButton(
                                    onClick = { state.clearSearchHistory() },
                                    colors = ButtonDefaults.textButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = MaterialTheme.colorScheme.error,
                                    ),
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(
                                            imageVector = GrzebIcons.Delete,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Text(
                                            text = "清空全部",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        )
                                    }
                                }
                            }
                        }

                        item(key = "history_list") {
                            val historyItems = state.searchHistory.take(8).map { keyword ->
                                Material3SettingsItem(
                                    icon = GrzebIcons.History,
                                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    iconBackgroundTint = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f),
                                    iconShape = RoundedCornerShape(12.dp),
                                    title = {
                                        Text(
                                            text = keyword,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    trailingContent = {
                                        IconButton(
                                            onClick = { state.removeSearchQuery(keyword) },
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                        ) {
                                            Icon(
                                                imageVector = GrzebIcons.Close,
                                                contentDescription = "移除搜索记录",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                modifier = Modifier.size(15.dp),
                                            )
                                        }
                                    },
                                    onClick = {
                                        state.query = keyword
                                        state.search(scope, fileSystem, indexStore)
                                    },
                                )
                            }
                            Material3SettingsGroup(items = historyItems)
                        }
                    }

                    // 2. 空状态曲奇图形展示（当无历史记录时出现，复刻 Rhythm 的 empty_prompt）
                    if (state.searchHistory.isEmpty()) {
                        item(key = "empty_prompt") {
                            RhythmExpressiveEmptyState()
                        }
                    }

                    // 3. 按类型浏览格式矩阵（复刻 Rhythm 的 UniversalGenreBrowseSection）
                    item(key = "format_browse") {
                        CategoryBrowseSection(
                            isWide = isWide,
                            onCategoryClick = { category ->
                                // 点击分类：将该分类下的常见后缀加入关键词或提示搜索
                                state.query = category.extensions.firstOrNull() ?: ""
                                if (state.canSearch) {
                                    state.search(scope, fileSystem, indexStore)
                                }
                            },
                        )
                    }
                }
            } else {
                // 结果区：有搜索结果或检索进行中
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(bottom = 90.dp),
                ) {
                    AnimatedVisibility(
                        visible = state.progress != null || state.isSearching,
                        enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                            fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                        exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                            fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                    ) {
                        state.progress?.let { ProgressArea(it) } ?: Box(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
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
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }

                    if (state.tree != null) {
                        ScopeBreadcrumb(
                            path = state.scopePath,
                            onNavigate = { segment -> state.drillInto(segment.uri, scope, indexStore) },
                        )
                    }

                    val rootDisplayNames = remember(state.directories.toList()) {
                        state.directories.associateWith { services.createFileSystem(it).displayName(it) }
                    }

                    SearchResultTree(
                        node = state.tree,
                        isLoading = state.isSearching,
                        emptyText = if (state.hasSearched) "没有找到匹配的结果" else "选择目录并在底部输入关键词",
                        collapsedFolders = state.collapsedFolders,
                        collapsedFiles = state.collapsedFiles,
                        selectedUri = state.preview?.file?.uri,
                        onToggleFolder = state::toggleFolder,
                        onToggleFile = state::toggleFile,
                        onDrill = { uri -> state.drillInto(uri, scope, indexStore, rootDisplayNames) },
                        onSelect = { file, match -> state.preview = PreviewSelection(file, match) },
                        onExpandAll = state::expandAll,
                        onCollapseAll = state::collapseAll,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        val rootDisplayNames = remember(state.directories.toList()) {
            state.directories.associateWith { services.createFileSystem(it).displayName(it) }
        }

        // 底部停靠/浮动搜索栏
        BottomSearchBar(
            query = state.query,
            onQueryChange = { state.query = it },
            canSearch = state.canSearch,
            isSearching = state.isSearching,
            onSearch = {
                state.search(
                    scope = scope,
                    fileSystem = fileSystem,
                    store = indexStore,
                    fileSystemProvider = { services.createFileSystem(it) },
                    rootDisplayNames = rootDisplayNames,
                )
            },
            onCancel = state::cancel,
            hasDirectory = state.directories.isNotEmpty() || state.directory != null,
            directoryLabel = if (state.directories.size > 1) {
                "${state.directories.size} 个工作区"
            } else {
                state.directory?.let(fileSystem::displayName)
            },
            isScopeActive = state.currentScope != null,
            onDirectoryOrBackClick = {
                if (state.currentScope != null) {
                    state.scopeUp(scope, indexStore, rootDisplayNames)
                } else {
                    scope.launch { services.pickDirectory()?.let { state.addDirectory(it) } }
                }
            },
            onAddDirectory = {
                scope.launch { services.pickDirectory()?.let { state.addDirectory(it) } }
            },
            showFilters = state.showOptions,
            onToggleFilters = { state.showOptions = !state.showOptions },
            state = state,
            isWide = isWide,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * 顶部精简工作区目录卡片。
 */
@Composable
private fun WorkspaceDirectoryHeader(
    selected: Boolean,
    label: String?,
    isWarming: Boolean,
    indexStatus: IndexStatus?,
    isSupported: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isSupported) {
                        Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onPick)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (selected) GrzebIcons.FolderOpen else GrzebIcons.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (selected) "当前搜索工作区" else "未选择工作区目录",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = label ?: if (isSupported) "点击选择目录..." else "当前环境未接入目录选择",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    ),
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
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

                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(24.dp).pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(
                        imageVector = GrzebIcons.Close,
                        contentDescription = "清除目录",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Rhythm 表现力空状态（Empty Prompt）：
 * 复合卫星曲奇圆角容器结构 + 渐变图标。
 */
@Composable
private fun RhythmExpressiveEmptyState() {
    val containerColor = MaterialTheme.colorScheme.secondaryContainer
    val contentColor = MaterialTheme.colorScheme.onSecondaryContainer

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(132.dp),
            contentAlignment = Alignment.Center,
        ) {
            // 卫星装饰圆 1 (左上)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = containerColor.copy(alpha = 0.45f),
                modifier = Modifier
                    .size(42.dp)
                    .align(Alignment.TopStart),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = GrzebIcons.Code,
                        contentDescription = null,
                        tint = contentColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // 卫星装饰圆 2 (右下)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = containerColor.copy(alpha = 0.45f),
                modifier = Modifier
                    .size(34.dp)
                    .align(Alignment.BottomEnd),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = GrzebIcons.Tune,
                        contentDescription = null,
                        tint = contentColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // 中心大曲奇容器 (96dp)
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = containerColor,
                shadowElevation = 6.dp,
                modifier = Modifier.size(96.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = GrzebIcons.Search,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "在工作区中全文检索",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "极速检索代码、脚本、配置、文档与纯文本",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 进度区：使用 M3 Expressive 的 [LinearWavyProgressIndicator]。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProgressArea(progress: SearchProgress) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
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
                    text = progress.currentFile?.let { "正在检索: $it" } ?: "扫描中...",
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

/**
 * 结果统计条。
 */
@Composable
private fun ResultSummary(fileCount: Int, matchCount: Int, onClear: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
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
