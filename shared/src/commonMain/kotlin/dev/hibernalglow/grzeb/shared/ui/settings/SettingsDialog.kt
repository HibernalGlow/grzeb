package dev.hibernalglow.grzeb.shared.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import dev.hibernalglow.grzeb.shared.ui.search.SearchUiState

/**
 * 设置弹窗：完全遵循 Material 3 Expressive 规范设计。
 *
 * 核心功能：
 * 1. 索引预热开关（默认关闭，不进行预热，支持手动触发单次预热/取消预热）；
 * 2. 扩充的内置可检索格式展示库（分类查看 90+ 种常用格式）；
 * 3. 用户自定义扩展名管理（支持格式校验、批量解析输入、动态添加/移除标签）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsDialog(
    state: SearchUiState,
    baseFileSystem: GrzebFileSystem,
    indexStore: IndexStore,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
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

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 320.dp, max = 680.dp)
                .fillMaxWidth(0.92f)
                .heightIn(max = 760.dp)
                .clip(MaterialTheme.shapes.extraLarge),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // ── 顶部标题区 ──────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = GrzebIcons.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Column {
                            Text(
                                text = "设置与检索选项",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "索引行为、自定义格式与文件类型管理",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    ) {
                        Icon(
                            imageVector = GrzebIcons.Close,
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // ── 可滚动内容区 ────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // 1. 索引预热卡片
                    PrewarmSettingsCard(
                        state = state,
                        baseFileSystem = baseFileSystem,
                        indexStore = indexStore,
                        onWarmToggle = { enabled ->
                            state.isWarmEnabled = enabled
                            if (enabled && state.directory != null) {
                                state.warm(scope, indexStore, baseFileSystem)
                            } else if (!enabled && state.warmProgress != null) {
                                state.cancelWarm()
                            }
                        },
                        onManualWarm = {
                            if (state.directory != null) {
                                state.warm(scope, indexStore, baseFileSystem)
                            }
                        },
                        onCancelWarm = state::cancelWarm,
                    )

                    // 2. 自定义格式输入卡片
                    CustomFormatsCard(
                        customExtensions = state.customExtensions,
                        inputText = inputExtension,
                        feedbackMessage = feedbackMessage,
                        onInputTextChange = {
                            inputExtension = it
                            feedbackMessage = null
                        },
                        onAdd = ::submitCustomExtension,
                        onRemove = state::removeCustomExtension,
                        onClearAll = state::clearCustomExtensions,
                    )

                    // 3. 内置格式库展示卡片
                    BuiltInFormatsCard(
                        categories = TextFileTypes.PRESET_CATEGORIES,
                        selectedCategoryIndex = selectedCategoryIndex,
                        onSelectCategory = { selectedCategoryIndex = it },
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // ── 底部操作区 ──────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onDismiss,
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
                    ) {
                        Text("完成", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/** 预热设置卡片：遵循 M3E 表面分层与弹性控件。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PrewarmSettingsCard(
    state: SearchUiState,
    baseFileSystem: GrzebFileSystem,
    indexStore: IndexStore,
    onWarmToggle: (Boolean) -> Unit,
    onManualWarm: () -> Unit,
    onCancelWarm: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 开关行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            if (state.isWarmEnabled) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = GrzebIcons.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (state.isWarmEnabled) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "目录索引预热",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (state.isWarmEnabled) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    }
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = if (state.isWarmEnabled) "已启用" else "默认关闭",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.isWarmEnabled) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "默认关闭。开启后选择目录将自动在后台建立正文快照，显著提升二次检索速度；关闭时按需实时流式检索，不占用持久缓存。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Switch(
                    checked = state.isWarmEnabled,
                    onCheckedChange = onWarmToggle,
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
            }

            // 预热动态状态与即时触发区
            AnimatedVisibility(
                visible = state.warmProgress != null,
                enter = androidx.compose.animation.expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                    androidx.compose.animation.fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                exit = androidx.compose.animation.shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                    androidx.compose.animation.fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                state.warmProgress?.let { progress ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "正在扫描并入库文件...",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                TextButton(
                                    onClick = onCancelWarm,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                ) {
                                    Text("取消预热", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            LinearWavyProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            )
                            Text(
                                text = "已扫描 ${progress.scannedFiles} 个文件（${progress.indexedFiles} 新增入库，${progress.reusedFiles} 命中快照）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // 当前目录索引状态栏与手动预热按钮
            if (state.directory != null && state.warmProgress == null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "当前目录状态",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val isWarmed = state.indexStatus?.isWarmed == true
                            Text(
                                text = if (isWarmed) {
                                    "已构建索引（${state.indexStatus?.fileCount ?: 0} 文件，${state.indexStatus?.textCount ?: 0} 文本缓存）"
                                } else {
                                    "尚未构建索引快照（实时检索模式）"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = if (isWarmed) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }

                        Button(
                            onClick = onManualWarm,
                            shapes = ButtonDefaults.shapes(),
                            colors = ButtonDefaults.filledTonalButtonColors(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                imageVector = GrzebIcons.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (state.indexStatus?.isWarmed == true) "重新预热" else "单次预热",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 用户自定义格式卡片：支持实时输入、规范化、批量解析与可形变标签展示。 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CustomFormatsCard(
    customExtensions: List<String>,
    inputText: String,
    feedbackMessage: String?,
    onInputTextChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "自定义检索格式",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "${customExtensions.size} 个自定义",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }

                if (customExtensions.isNotEmpty()) {
                    TextButton(
                        onClick = onClearAll,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "清空全部",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Text(
                text = "输入自定义文件扩展名（如 .xyz、log2、dat），支持用逗号分隔批量添加：",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 输入与添加行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = "例如 .custom 或 dat, log2...",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = GrzebIcons.Description,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingIcon = {
                        if (inputText.isNotEmpty()) {
                            IconButton(onClick = { onInputTextChange("") }) {
                                Icon(
                                    imageVector = GrzebIcons.Close,
                                    contentDescription = "清空输入",
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    },
                    shape = MaterialTheme.shapes.medium,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onAdd() }),
                )

                Button(
                    onClick = onAdd,
                    enabled = inputText.isNotBlank(),
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Icon(
                        imageVector = GrzebIcons.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("添加", style = MaterialTheme.typography.labelLarge)
                }
            }

            // 动态反馈消息
            AnimatedVisibility(
                visible = feedbackMessage != null,
                enter = androidx.compose.animation.fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                exit = androidx.compose.animation.fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                feedbackMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }

            // 自定义标签云
            if (customExtensions.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    customExtensions.forEach { ext ->
                        CustomExtensionPill(
                            extension = ext,
                            onRemove = { onRemove(ext) },
                        )
                    }
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            MaterialTheme.shapes.medium,
                        )
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                ) {
                    Text(
                        text = "暂无自定义格式。添加后将立即与内置格式一同参与实时检索与索引预热。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 单个自定义扩展名标签（胶囊药丸造型 + 快速移除按钮）。 */
@Composable
private fun CustomExtensionPill(
    extension: String,
    onRemove: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.clip(MaterialTheme.shapes.small),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = extension,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Medium,
            )
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = GrzebIcons.Close,
                    contentDescription = "移除 $extension",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 内置支持格式展示卡片：展示扩充后的分类格式库。 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BuiltInFormatsCard(
    categories: List<TextFileTypes.FormatCategory>,
    selectedCategoryIndex: Int?,
    onSelectCategory: (Int?) -> Unit,
) {
    val totalCount = remember(categories) { categories.sumOf { it.extensions.size } }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "内置支持格式库",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "已内置 $totalCount 种格式",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }

            Text(
                text = "已扩充支持代码开发、函数式语言、Web 样式与组件、工程配置、标记文档、字幕与常见文本格式：",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 分类筛选 Chip
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = selectedCategoryIndex == null,
                    onClick = { onSelectCategory(null) },
                    label = { Text("全部 (${totalCount})", style = MaterialTheme.typography.labelSmall) },
                    shape = MaterialTheme.shapes.small,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )

                categories.forEachIndexed { index, category ->
                    FilterChip(
                        selected = selectedCategoryIndex == index,
                        onClick = {
                            onSelectCategory(if (selectedCategoryIndex == index) null else index)
                        },
                        label = {
                            Text(
                                text = "${category.name} (${category.extensions.size})",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        shape = MaterialTheme.shapes.small,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }

            // 当前选中的扩展名展示
            val displayedExtensions = remember(selectedCategoryIndex, categories) {
                if (selectedCategoryIndex != null && selectedCategoryIndex in categories.indices) {
                    categories[selectedCategoryIndex].extensions
                } else {
                    categories.flatMap { it.extensions }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    displayedExtensions.forEach { ext ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = MaterialTheme.shapes.extraSmall,
                        ) {
                            Text(
                                text = ext,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
