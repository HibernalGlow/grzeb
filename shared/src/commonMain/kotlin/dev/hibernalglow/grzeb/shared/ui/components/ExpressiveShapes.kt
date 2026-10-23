package dev.hibernalglow.grzeb.shared.ui.components

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Rhythm-inspired Material 3 Expressive Shapes system for Compose Multiplatform.
 */
object ExpressiveShapeTokens {
    val Full: Shape = CircleShape
    val ExtraLarge = RoundedCornerShape(32.dp)
    val Large = RoundedCornerShape(28.dp)
    val Medium = RoundedCornerShape(20.dp)
    val Small = RoundedCornerShape(14.dp)
    val ExtraSmall = RoundedCornerShape(10.dp)
}

/**
 * Computes responsive connected shapes for a grid of items,
 * where the outside perimeter corners are smooth and large (24dp)
 * and interior corners are compact (8dp).
 * Directly ported from Rhythm's UniversalGenreBrowseSection.
 */
fun getUniversalResponsiveGridItemShape(
    index: Int,
    totalItems: Int,
    columnsCount: Int,
    outerRadius: Dp = 24.dp,
    innerRadius: Dp = 8.dp,
): RoundedCornerShape {
    if (totalItems <= 1) return RoundedCornerShape(outerRadius)
    val totalRows = (totalItems + columnsCount - 1) / columnsCount
    val r = index / columnsCount
    val c = index % columnsCount
    val isTopRow = r == 0
    val isBottomRow = r == totalRows - 1
    val isLeftColumn = c == 0
    val isRightColumn = c == columnsCount - 1 || index == totalItems - 1
    val topStart = if (isTopRow && isLeftColumn) outerRadius else innerRadius
    val topEnd = if (isTopRow && isRightColumn) outerRadius else innerRadius
    val bottomStart = if (isBottomRow && isLeftColumn) outerRadius else innerRadius
    val bottomEnd = if (isBottomRow && isRightColumn) outerRadius else innerRadius
    return RoundedCornerShape(
        topStart = topStart,
        topEnd = topEnd,
        bottomStart = bottomStart,
        bottomEnd = bottomEnd,
    )
}

/**
 * Cookie/Squircle expressive shape helper.
 */
@Composable
fun rememberCookieShape(size: Dp = 28.dp): Shape {
    return remember(size) {
        RoundedCornerShape(size)
    }
}
