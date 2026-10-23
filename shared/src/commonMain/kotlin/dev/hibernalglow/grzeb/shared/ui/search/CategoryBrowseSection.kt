package dev.hibernalglow.grzeb.shared.ui.search

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hibernalglow.grzeb.core.text.TextFileTypes
import dev.hibernalglow.grzeb.shared.ui.GrzebIcons
import dev.hibernalglow.grzeb.shared.ui.components.getUniversalResponsiveGridItemShape

/**
 * Category browse card data.
 */
data class CategoryBrowseItem(
    val category: TextFileTypes.FormatCategory,
    val icon: ImageVector,
)

/**
 * Format category browse section modeled directly on Rhythm's UniversalGenreBrowseSection.
 * Features connected responsive card shapes, bold typography, and rotated watermark icons.
 */
@Composable
fun CategoryBrowseSection(
    categories: List<TextFileTypes.FormatCategory> = TextFileTypes.PRESET_CATEGORIES,
    isWide: Boolean = false,
    onCategoryClick: (TextFileTypes.FormatCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = remember(categories) {
        categories.map { category ->
            val icon = when {
                category.name.contains("代码") -> GrzebIcons.Code
                category.name.contains("现代") -> GrzebIcons.Terminal
                category.name.contains("Web") -> GrzebIcons.Language
                category.name.contains("配置") -> GrzebIcons.Tune
                category.name.contains("文档") -> GrzebIcons.Description
                category.name.contains("数据") -> GrzebIcons.Storage
                category.name.contains("字幕") -> GrzebIcons.Description
                else -> GrzebIcons.Folder
            }
            CategoryBrowseItem(category, icon)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .padding(bottom = 14.dp),
        ) {
            Icon(
                imageVector = GrzebIcons.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = "按类型浏览",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 10.dp),
            )
        }

        val columnsCount = if (isWide) 3 else 2
        val rows = remember(items, columnsCount) { items.chunked(columnsCount) }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            rows.forEachIndexed { rowIndex, rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    rowItems.forEachIndexed { colIndex, item ->
                        val itemIndex = rowIndex * columnsCount + colIndex
                        CategoryBrowseItemCard(
                            item = item,
                            index = itemIndex,
                            totalItems = items.size,
                            columnsCount = columnsCount,
                            onClick = { onCategoryClick(item.category) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowItems.size < columnsCount) {
                        repeat(columnsCount - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryBrowseItemCard(
    item: CategoryBrowseItem,
    index: Int,
    totalItems: Int,
    columnsCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = remember(index, totalItems, columnsCount) {
        getUniversalResponsiveGridItemShape(index, totalItems, columnsCount)
    }

    // Color trio palette rotation matching Rhythm
    val colorPair = when (index % 3) {
        0 -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        1 -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }

    Card(
        onClick = onClick,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = colorPair.first),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.height(115.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Watermark icon in bottom-right corner rotated -18 degrees (alpha 0.08)
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = colorPair.second.copy(alpha = 0.08f),
                modifier = Modifier
                    .size(96.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 24.dp, y = 24.dp)
                    .graphicsLayer { rotationZ = -18f },
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    text = item.category.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = colorPair.second,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${item.category.extensions.size} 种格式",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorPair.second.copy(alpha = 0.75f),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
