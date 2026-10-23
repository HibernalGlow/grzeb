package dev.hibernalglow.grzeb.shared.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.hibernalglow.grzeb.core.fs.GrzebFileSystem
import dev.hibernalglow.grzeb.core.index.IndexStore
import dev.hibernalglow.grzeb.core.text.TextFileTypes
import dev.hibernalglow.grzeb.shared.ui.GrzebIcons
import dev.hibernalglow.grzeb.shared.ui.components.Material3SettingsGroup
import dev.hibernalglow.grzeb.shared.ui.components.Material3SettingsItem
import dev.hibernalglow.grzeb.shared.ui.components.SettingsPalettes
import dev.hibernalglow.grzeb.shared.ui.search.SearchUiState

/**
 * 设置界面：完全对齐 Rhythm 的 Material 3 Expressive 界面规范。
 *
 * 特性：
 * 1. 顶部圆角返回按钮 + 醒目标题 + 胶囊状设置搜索栏；
 * 2. 多工作区目录管理（支持增删、展示多目录）；
 * 3. 索引预热控制（默认关闭，支持手动预热与波浪进度指示器）；
 * 4. 内置 90+ 种格式分类展示与用户自定义文件后缀管理；
 * 5. 按照 Rhythm 的 [Material3SettingsGroup] 与 [SettingsPalettes] 呈现卡片堆叠与弹性动效。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsDialog(
    state: SearchUiState,
    baseFileSystem: GrzebFileSystem,
    indexStore: IndexStore,
    onPickDirectory: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val isCompact = maxWidth < 640.dp
            Surface(
                modifier = if (isCompact) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .widthIn(min = 440.dp, max = 680.dp)
                        .fillMaxWidth(0.92f)
                        .fillMaxHeight(0.92f)
                        .clip(RoundedCornerShape(28.dp))
                },
                shape = if (isCompact) RoundedCornerShape(0.dp) else RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
            ) {
                SettingsContent(
                    state = state,
                    baseFileSystem = baseFileSystem,
                    indexStore = indexStore,
                    onPickDirectory = onPickDirectory,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun SettingsContent(
    state: SearchUiState,
    baseFileSystem: GrzebFileSystem,
    indexStore: IndexStore,
    onPickDirectory: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var settingsSearchQuery by remember { mutableStateOf("") }

    var inputExtension by remember { mutableStateOf("") }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var selectedCategoryIndex by remember { mutableStateOf<Int?>(null) }

    fun submitCustomExtension() {
        val trimmed = inputExtension.trim()
        if (trimmed.isEmpty()) return
        val count = state.addCustomExtensions(trimmed)
        if (count > 0) {
            feedbackMessage = "已添加 $count 个自定义格式"
            inputExtension = ""
        } else {
            val normalized = TextFileTypes.normalizeExtension(trimmed)
            feedbackMessage = when {
                normalized == null -> "格式无效，请输入有效后缀（如 .xyz）"
                normalized in state.customExtensions -> "'$normalized' 已经添加过了"
                normalized in TextFileTypes.DEFAULT_EXTENSIONS -> "'$normalized' 已属于内置支持格式"
                else -> "添加失败，请检查输入"
            }
        }
    }

    val query = settingsSearchQuery.trim().lowercase()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // ── 顶部导航栏 (Rhythm 风格：返回按钮 + 大标题) ─────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(42.dp),
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxSize().pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(
                        imageVector = GrzebIcons.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // ── 设置搜索栏 (Rhythm 胶囊样式) ──────────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = GrzebIcons.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )

                BasicTextField(
                    value = settingsSearchQuery,
                    onValueChange = { settingsSearchQuery = it },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box {
                            if (settingsSearchQuery.isEmpty()) {
                                Text(
                                    text = "搜索设置项...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            }
                            innerTextField()
                        }
                    },
                )

                if (settingsSearchQuery.isNotEmpty()) {
                    Icon(
                        imageVector = GrzebIcons.Close,
                        contentDescription = "清空搜索",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { settingsSearchQuery = "" },
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── 设置主体分组列表 ────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // ── 1. 多工作区与检索目录 ─────────────────────────────────────────
            val showWorkspaceSection = query.isEmpty() ||
                "工作区".contains(query) || "目录".contains(query) || "文件夹".contains(query) ||
                "预热".contains(query) || "索引".contains(query)

            if (showWorkspaceSection) {
                val workspaceItems = buildList {
                    // 多工作区目录管理
                    add(
                        Material3SettingsItem(
                            icon = GrzebIcons.Folder,
                            palette = SettingsPalettes.SkyBlue,
                            title = { Text("多工作区检索目录") },
                            description = {
                                Text("支持同时配置多个目录一起检索。当前共有 ${state.directories.size} 个工作区。")
                            },
                            trailingContent = onPickDirectory?.let { pickAction ->
                                {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .pointerHoverIcon(PointerIcon.Hand)
                                            .clickable(onClick = pickAction)
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Text(
                                                text = "+ 添加文件夹",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            )
                                        }
                                    }
                                }
                            },
                        )
                    )

                    // 索引预热开关
                    add(
                        Material3SettingsItem(
                            icon = GrzebIcons.Bolt,
                            palette = SettingsPalettes.Emerald,
                            title = { Text("后台目录索引预热") },
                            description = {
                                Text("默认关闭。开启后选择目录时在后台预先建立目录树与正文快照以显著加速检索；关闭时进行实时流式检索。")
                            },
                            trailingContent = {
                                Switch(
                                    checked = state.isWarmEnabled,
                                    onCheckedChange = { enabled ->
                                        state.isWarmEnabled = enabled
                                        if (enabled && state.directory != null) {
                                            state.warm(scope, indexStore, baseFileSystem)
                                        } else if (!enabled && state.warmProgress != null) {
                                            state.cancelWarm()
                                        }
                                    },
                                    thumbContent = {
                                        if (state.isWarmEnabled) {
                                            Icon(
                                                imageVector = GrzebIcons.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(SwitchDefaults.IconSize),
                                            )
                                        }
                                    },
                                )
                            },
                        )
                    )

                    // 手动预热与缓存状态
                    add(
                        Material3SettingsItem(
                            icon = GrzebIcons.Storage,
                            palette = SettingsPalettes.Lime,
                            title = { Text("快照缓存状态") },
                            description = {
                                val isWarmed = state.indexStatus?.isWarmed == true
                                val statusText = if (isWarmed) {
                                    "已缓存 ${state.indexStatus?.fileCount ?: 0} 个文件 · ${state.indexStatus?.textCount ?: 0} 份正文快照"
                                } else {
                                    "当前尚未构建持久索引快照（实时检索模式）"
                                }
                                Text(statusText)
                            },
                            trailingContent = {
                                val isWarming = state.warmProgress != null
                                Button(
                                    onClick = {
                                        if (isWarming) {
                                            state.cancelWarm()
                                        } else if (state.directory != null) {
                                            state.warm(scope, indexStore, baseFileSystem)
                                        }
                                    },
                                    shapes = ButtonDefaults.shapes(),
                                    colors = ButtonDefaults.filledTonalButtonColors(),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text(
                                        text = if (isWarming) "取消" else if (state.indexStatus?.isWarmed == true) "重新预热" else "立即预热",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            },
                        )
                    )
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Material3SettingsGroup(
                        title = "工作区与索引",
                        items = workspaceItems,
                    )

                    // 预热波浪动态指示条
                    AnimatedVisibility(
                        visible = state.warmProgress != null,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        state.warmProgress?.let { p ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                                    .padding(horizontal = 4.dp),
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = "正在扫描并入库文件...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = "${p.scannedFiles} 文件已扫",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    LinearWavyProgressIndicator(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    )
                                }
                            }
                        }
                    }

                    // 展开展示当前工作区目录列表
                    if (state.directories.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .padding(horizontal = 4.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "已配置的工作区目录：",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    state.directories.forEach { dir ->
                                        val name = dir.substringAfterLast('/').ifEmpty { dir }
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            modifier = Modifier.height(30.dp),
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(start = 10.dp, end = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Icon(
                                                    imageVector = GrzebIcons.Folder,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                                Text(
                                                    text = name,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                )
                                                if (state.directories.size > 1) {
                                                    Icon(
                                                        imageVector = GrzebIcons.Close,
                                                        contentDescription = "移除工作区",
                                                        modifier = Modifier
                                                            .size(16.dp)
                                                            .clip(CircleShape)
                                                            .pointerHoverIcon(PointerIcon.Hand)
                                                            .clickable { state.removeDirectory(dir) },
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── 2. 文件格式管理 (自定义后缀 + 90+ 内置库) ────────────────────
            val showFormatsSection = query.isEmpty() ||
                "格式".contains(query) || "后缀".contains(query) || "扩展名".contains(query) ||
                "代码".contains(query) || "类型".contains(query)

            if (showFormatsSection) {
                val formatItems = buildList {
                    // 自定义格式输入项
                    add(
                        Material3SettingsItem(
                            icon = GrzebIcons.Description,
                            palette = SettingsPalettes.Rose,
                            title = { Text("自定义文件扩展名") },
                            description = {
                                Text("支持输入专有格式后缀（如 .proto, .graphql, .vue, .custom）。已添加 ${state.customExtensions.size} 个后缀。")
                            },
                        )
                    )

                    // 内置格式库展示
                    add(
                        Material3SettingsItem(
                            icon = GrzebIcons.Code,
                            palette = SettingsPalettes.Purple,
                            title = { Text("内置支持格式库 (90+)") },
                            description = {
                                Text("涵盖代码语言（kt, rs, ts, py）、Web、配置文件、文档与脚本等 8 大常用分类。")
                            },
                        )
                    )
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Material3SettingsGroup(
                        title = "可检索格式",
                        items = formatItems,
                    )

                    // 自定义格式输入与展示卡片
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .padding(horizontal = 4.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = "添加自定义扩展名：",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = inputExtension,
                                    onValueChange = {
                                        inputExtension = it
                                        feedbackMessage = null
                                    },
                                    modifier = Modifier.weight(1f),
                                    placeholder = {
                                        Text(".custom, .graphql", style = MaterialTheme.typography.bodySmall)
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { submitCustomExtension() }),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    ),
                                )

                                Button(
                                    onClick = ::submitCustomExtension,
                                    shapes = ButtonDefaults.shapes(),
                                    enabled = inputExtension.isNotBlank(),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                ) {
                                    Text("添加", style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            feedbackMessage?.let { msg ->
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (msg.startsWith("已添加")) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                )
                            }

                            if (state.customExtensions.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "已添加的格式 (${state.customExtensions.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    TextButton(
                                        onClick = state::clearCustomExtensions,
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    ) {
                                        Text("清空全部", style = MaterialTheme.typography.labelSmall)
                                    }
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    state.customExtensions.forEach { ext ->
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.height(28.dp),
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(start = 10.dp, end = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                Text(
                                                    text = ext,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontFamily = FontFamily.Monospace,
                                                    ),
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                )
                                                Icon(
                                                    imageVector = GrzebIcons.Close,
                                                    contentDescription = "移除 $ext",
                                                    modifier = Modifier
                                                        .size(14.dp)
                                                        .clip(CircleShape)
                                                        .pointerHoverIcon(PointerIcon.Hand)
                                                        .clickable { state.removeCustomExtension(ext) },
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            // 8 大内置格式分类浏览
                            Text(
                                text = "点击分类查看内置支持格式：",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                TextFileTypes.PRESET_CATEGORIES.forEachIndexed { index, cat ->
                                    val isSelected = selectedCategoryIndex == index
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedCategoryIndex = if (isSelected) null else index },
                                        shape = CircleShape,
                                        label = {
                                            Text(
                                                text = "${cat.name} (${cat.extensions.size})",
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        ),
                                        border = null,
                                    )
                                }
                            }

                            selectedCategoryIndex?.let { idx ->
                                val activeCat = TextFileTypes.PRESET_CATEGORIES.getOrNull(idx)
                                if (activeCat != null) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Text(
                                                text = "${activeCat.name}包含的扩展名：",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            Text(
                                                text = activeCat.extensions.joinToString("  "),
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── 3. 偏好与历史 ────────────────────────────────────────────────
            val showPreferencesSection = query.isEmpty() ||
                "偏好".contains(query) || "历史".contains(query) || "匹配".contains(query) ||
                "正则".contains(query)

            if (showPreferencesSection) {
                val preferenceItems = buildList {
                    add(
                        Material3SettingsItem(
                            icon = GrzebIcons.FilterList,
                            palette = SettingsPalettes.Amber,
                            title = { Text("搜索匹配选项") },
                            description = {
                                Text("支持在主页面底部栏快捷切换大小写敏感、正则表达式、全词匹配与单次命中。")
                            },
                        )
                    )

                    add(
                        Material3SettingsItem(
                            icon = GrzebIcons.History,
                            palette = SettingsPalettes.Cyan,
                            title = { Text("搜索关键词历史") },
                            description = {
                                Text("自动保存最近 10 次搜索词。当前保留了 ${state.searchHistory.size} 条历史。")
                            },
                            trailingContent = if (state.searchHistory.isNotEmpty()) {
                                {
                                    TextButton(
                                        onClick = state::clearSearchHistory,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    ) {
                                        Text("清空历史", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            } else null,
                        )
                    )
                }

                Material3SettingsGroup(
                    title = "偏好与历史",
                    items = preferenceItems,
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
