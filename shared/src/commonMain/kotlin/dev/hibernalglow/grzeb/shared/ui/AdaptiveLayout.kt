package dev.hibernalglow.grzeb.shared.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * M3 窗口尺寸分类。
 *
 * 断点沿用 androidx `WidthSizeClass` 的口径（600 / 840dp），但按**内容宽度**判定：
 * CMP 的 commonMain 没有跨平台的窗口对象，BoxWithConstraints 给到的正是可绘制宽度，
 * 且已经扣掉了 Scaffold 的 insets。
 */
enum class WindowSizeClass { Compact, Medium, Expanded }

fun windowSizeClassOf(width: Dp): WindowSizeClass = when {
    width < 600.dp -> WindowSizeClass.Compact
    width < 840.dp -> WindowSizeClass.Medium
    else -> WindowSizeClass.Expanded
}

/** 侧栏默认宽度，取 M3 supporting pane 在展开窗口的档位。 */
val DefaultPaneWidth = 420.dp

/**
 * 详情侧栏默认宽度；null 表示预览仍按整屏呈现。
 *
 * 只有展开窗口才并排两栏：中等窗口扣掉 420dp 后列表放不下。
 * 实际宽度由用户拖 [PaneResizeHandle] 决定，这里只是初值。
 */
val WindowSizeClass.detailPaneWidth: Dp?
    get() = when (this) {
        WindowSizeClass.Compact, WindowSizeClass.Medium -> null
        WindowSizeClass.Expanded -> DefaultPaneWidth
    }

/**
 * 单栏时正文的最大宽度，超出的部分留给左右留白，避免整页被拉成一条横幅。
 * null 表示不限宽（紧凑窗口本身就窄，且 M3 要求紧凑下内容铺满）。
 */
val WindowSizeClass.maxContentWidth: Dp?
    get() = when (this) {
        WindowSizeClass.Compact -> null
        WindowSizeClass.Medium -> 720.dp
        WindowSizeClass.Expanded -> 900.dp
    }

/** 两栏之间拖拽手柄的命中宽度；中央带 M3E 胶囊拖拽指示器。 */
val PaneDividerWidth = 12.dp

/** 侧栏最小宽度，再窄就读不了带行号的内容。 */
val MinPaneWidth = 280.dp

/** 侧栏拖到最宽时留给列表的最小宽度。 */
val MinListWidth = 360.dp

/**
 * 两栏之间的拖拽手柄：横向拖动回调增量，双击复位。
 *
 * 采用 M3 Expressive 分栏手柄造型：细分隔线 + 中央可形变胶囊把手，拖拽时通过弹簧过渡放大并高亮。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PaneResizeHandle(
    onDrag: (delta: Dp) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }

    val pillWidth by animateDpAsState(
        targetValue = if (isDragging) 6.dp else 4.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )
    val pillHeight by animateDpAsState(
        targetValue = if (isDragging) 48.dp else 32.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )
    val pillColor by animateColorAsState(
        targetValue = if (isDragging) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
    )

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                ) { change, dragAmount ->
                    change.consume()
                    // dragAmount 是 px，且向右为正；侧栏在右侧，所以取反
                    onDrag((-dragAmount / density).dp)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onReset() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        )
        Box(
            Modifier
                .width(pillWidth)
                .height(pillHeight)
                .clip(CircleShape)
                .background(pillColor),
        )
    }
}
