package dev.hibernalglow.grzeb.shared.ui.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hibernalglow.grzeb.shared.ui.GrzebIcons

/**
 * Rhythm's Bottom Docked/Floating Search Bar for Compose Multiplatform.
 * Positioned at the bottom of the screen with a gradient background,
 * thumb-friendly circular controls, pill-shaped search input card, and expandable filter chips.
 */
@Composable
fun BottomSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    canSearch: Boolean,
    isSearching: Boolean,
    onSearch: () -> Unit,
    onCancel: () -> Unit,
    hasDirectory: Boolean,
    directoryLabel: String?,
    isScopeActive: Boolean,
    onDirectoryOrBackClick: () -> Unit,
    onAddDirectory: (() -> Unit)? = null,
    showFilters: Boolean,
    onToggleFilters: () -> Unit,
    state: SearchUiState,
    isWide: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.18f to MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
                        1f to MaterialTheme.colorScheme.background,
                    ),
                ),
            )
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .then(
                    if (isWide) {
                        Modifier.widthIn(max = 680.dp).align(Alignment.BottomCenter)
                    } else {
                        Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    },
                )
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Left circular button: Back if drilled in, or Directory Picker
                Surface(
                    shape = CircleShape,
                    color = if (hasDirectory) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    modifier = Modifier.size(52.dp),
                ) {
                    IconButton(
                        onClick = onDirectoryOrBackClick,
                        modifier = Modifier.fillMaxSize().pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            imageVector = if (isScopeActive) {
                                GrzebIcons.ArrowBack
                            } else if (hasDirectory) {
                                GrzebIcons.FolderOpen
                            } else {
                                GrzebIcons.Folder
                            },
                            contentDescription = if (isScopeActive) "返回上一层" else "选择目录",
                            tint = if (hasDirectory) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                // Center Pill Search Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = CircleShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = GrzebIcons.Search,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            modifier = Modifier.weight(1f),
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    if (canSearch) onSearch()
                                },
                            ),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (query.isEmpty()) {
                                        val hint = when {
                                            !hasDirectory -> "请先点击左侧选择目录..."
                                            state.directories.size > 1 -> "在 ${state.directories.size} 个工作区中搜索... (Enter)"
                                            directoryLabel != null -> "在 $directoryLabel 中搜索... (Enter)"
                                            else -> "在工作区中搜索... (Enter)"
                                        }
                                        Text(
                                            text = hint,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                        )
                        if (query.isNotEmpty()) {
                            IconButton(
                                onClick = { onQueryChange("") },
                                modifier = Modifier
                                    .size(32.dp)
                                    .pointerHoverIcon(PointerIcon.Hand),
                            ) {
                                Icon(
                                    imageVector = GrzebIcons.Close,
                                    contentDescription = "清空输入",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // Right circular button: Filter toggle
                val filterButtonColor by animateColorAsState(
                    targetValue = if (showFilters) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "filterButtonColor",
                )
                val filterIconColor by animateColorAsState(
                    targetValue = if (showFilters) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "filterIconColor",
                )

                Surface(
                    shape = CircleShape,
                    color = filterButtonColor,
                    modifier = Modifier.size(52.dp),
                ) {
                    IconButton(
                        onClick = onToggleFilters,
                        modifier = Modifier.fillMaxSize().pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            imageVector = GrzebIcons.FilterList,
                            contentDescription = "检索筛选选项",
                            tint = filterIconColor,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            // 多工作区目录芯片行（当配置了多个工作区时常驻展示）
            if (state.directories.size > 1) {
                val dirScroll = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(dirScroll)
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    state.directories.forEach { dir ->
                        val name = dir.substringAfterLast('/').ifEmpty { dir }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.height(26.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 8.dp, end = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = GrzebIcons.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Icon(
                                    imageVector = GrzebIcons.Close,
                                    contentDescription = "移除目录",
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .clickable { state.removeDirectory(dir) },
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (onAddDirectory != null) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .height(26.dp)
                                .clip(CircleShape)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable(onClick = onAddDirectory),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = "+ 目录",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }
            }

            // Animated Filter Chips Row
            AnimatedVisibility(
                visible = showFilters,
                enter = expandVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = shrinkVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy)) + fadeOut(),
            ) {
                val scrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState)
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onAddDirectory != null) {
                        FilterChip(
                            selected = false,
                            onClick = onAddDirectory,
                            shape = CircleShape,
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                            leadingIcon = {
                                Icon(
                                    imageVector = GrzebIcons.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            label = {
                                Text(
                                    text = "+ 工作区",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                            border = null,
                        )
                    }
                    ExpressiveFilterChip(
                        code = "Aa",
                        label = "大小写敏感",
                        selected = state.isCaseSensitive,
                        onClick = { state.toggleOption(SearchUiState.Option.CaseSensitive) },
                    )
                    ExpressiveFilterChip(
                        code = ".*",
                        label = "正则表达式",
                        selected = state.isRegex,
                        onClick = { state.toggleOption(SearchUiState.Option.Regex) },
                    )
                    ExpressiveFilterChip(
                        code = "\\b",
                        label = "全词匹配",
                        selected = state.isWholeWord,
                        onClick = { state.toggleOption(SearchUiState.Option.WholeWord) },
                    )
                    ExpressiveFilterChip(
                        code = "1×",
                        label = "每文件首个",
                        selected = state.isOnlyFirstMatch,
                        onClick = { state.toggleOption(SearchUiState.Option.OnlyFirstMatch) },
                    )
                    FilterChip(
                        selected = false,
                        onClick = { state.showSettings = true },
                        shape = CircleShape,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        leadingIcon = {
                            Icon(
                                imageVector = GrzebIcons.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        label = {
                            Text(
                                text = if (state.customExtensions.isNotEmpty()) {
                                    "设置 (+${state.customExtensions.size})"
                                } else {
                                    "设置"
                                },
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        border = null,
                    )
                }
            }
        }
    }
}

/**
 * Filter chip with Rhythm's spring bouncy corner radius animation.
 */
@Composable
private fun ExpressiveFilterChip(
    code: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val cornerRadius by animateDpAsState(
        targetValue = if (selected) 24.dp else 12.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "filterChipCornerRadius",
    )

    FilterChip(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(cornerRadius),
        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        leadingIcon = {
            AnimatedContent(
                targetState = selected,
                label = "chipIconTransition",
            ) { isSelected ->
                if (isSelected) {
                    Icon(
                        imageVector = GrzebIcons.Check,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                } else {
                    Text(
                        text = code,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        border = null,
    )
}
