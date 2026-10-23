package dev.hibernalglow.grzeb.shared.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Settings badge color palette directly ported from Rhythm's Material 3 Expressive palette.
 */
data class SettingsBadgePalette(
    val container: Color,
    val tint: Color,
) {
    fun containerColor(isDark: Boolean = false): Color = container
    fun iconTintColor(isDark: Boolean = false): Color = tint
}

object SettingsPalettes {
    val Amber = SettingsBadgePalette(Color(0xFFFFDCBE), Color(0xFF7C4113))
    val Yellow = SettingsBadgePalette(Color(0xFFFFF0A4), Color(0xFF624F00))
    val Emerald = SettingsBadgePalette(Color(0xFFC4EED0), Color(0xFF18532A))
    val Cyan = SettingsBadgePalette(Color(0xFFC0EAE6), Color(0xFF006A6F))
    val SkyBlue = SettingsBadgePalette(Color(0xFFC2E7FF), Color(0xFF004D77))
    val Indigo = SettingsBadgePalette(Color(0xFFD6E3FF), Color(0xFF1D5FA9))
    val Purple = SettingsBadgePalette(Color(0xFFEEDCFF), Color(0xFF5D367B))
    val Rose = SettingsBadgePalette(Color(0xFFF7D8EB), Color(0xFF82276C))
    val Coral = SettingsBadgePalette(Color(0xFFFFD8E2), Color(0xFF962846))
    val Lime = SettingsBadgePalette(Color(0xFFE0F3B8), Color(0xFF3B5606))
    val Slate = SettingsBadgePalette(Color(0xFFE2E2E6), Color(0xFF44474E))
    val Teal = SettingsBadgePalette(Color(0xFFBCEBEB), Color(0xFF006874))
    val Orange = SettingsBadgePalette(Color(0xFFFFDAC5), Color(0xFF763710))
}

data class Material3SettingsItem(
    val icon: ImageVector? = null,
    val iconTint: Color? = null,
    val iconBackgroundTint: Color? = null,
    val iconShape: Shape? = null,
    val palette: SettingsBadgePalette? = null,
    val title: @Composable () -> Unit,
    val description: (@Composable () -> Unit)? = null,
    val trailingContent: (@Composable () -> Unit)? = null,
    val leadingContent: (@Composable () -> Unit)? = null,
    val enabled: Boolean = true,
    val isHighlighted: Boolean = false,
    val onClick: (() -> Unit)? = null,
)

/**
 * Rhythm's Material 3 Expressive settings group:
 * Stacked cards with dynamic rounded corners:
 * - Single item -> 24dp all around
 * - First item -> 24dp top, 6dp bottom
 * - Middle items -> 6dp all
 * - Last item -> 6dp top, 24dp bottom
 */
@Composable
fun Material3SettingsGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    items: List<Material3SettingsItem>,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    palette: SettingsBadgePalette? = null,
    itemShape: Shape? = null,
    lastItemShape: Shape? = null,
    iconShape: Shape? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp, top = 16.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEachIndexed { index, item ->
                val isLast = index == items.size - 1
                val shape = when {
                    lastItemShape != null && isLast -> lastItemShape
                    itemShape != null -> itemShape
                    items.size == 1 -> RoundedCornerShape(24.dp)
                    index == 0 -> RoundedCornerShape(
                        topStart = 24.dp, topEnd = 24.dp,
                        bottomStart = 6.dp, bottomEnd = 6.dp,
                    )
                    isLast -> RoundedCornerShape(
                        topStart = 6.dp, topEnd = 6.dp,
                        bottomStart = 24.dp, bottomEnd = 24.dp,
                    )
                    else -> RoundedCornerShape(6.dp)
                }

                val itemPalette = item.palette ?: palette ?: SettingsPalettes.Purple

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    shape = shape,
                    colors = CardDefaults.cardColors(containerColor = containerColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Material3SettingsItemRow(
                        item = item,
                        fallbackPalette = itemPalette,
                        fallbackShape = item.iconShape ?: iconShape ?: CircleShape,
                    )
                }
            }
        }
    }
}

@Composable
private fun Material3SettingsItemRow(
    item: Material3SettingsItem,
    fallbackPalette: SettingsBadgePalette? = null,
    fallbackShape: Shape = CircleShape,
) {
    val isDark = isSystemInDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val itemPalette = item.palette ?: fallbackPalette

    val defaultIconBg = when {
        item.palette != null -> item.palette.containerColor(isDark)
        fallbackPalette != null -> fallbackPalette.containerColor(isDark)
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val defaultIconTint = when {
        item.palette != null -> item.palette.iconTintColor(isDark)
        fallbackPalette != null -> fallbackPalette.iconTintColor(isDark)
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    // Expressive press scale animation from Rhythm
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "setting_item_scale",
    )

    // Animated icon background color
    val iconBgColor by animateColorAsState(
        targetValue = when {
            !item.enabled -> MaterialTheme.colorScheme.surfaceContainerHighest
            isPressed -> (item.iconBackgroundTint ?: defaultIconBg).copy(alpha = 0.86f)
            else -> (item.iconBackgroundTint ?: defaultIconBg)
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "icon_bg_color",
    )

    val iconColor by animateColorAsState(
        targetValue = if (!item.enabled) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        } else {
            (item.iconTint ?: defaultIconTint)
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "icon_color",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                enabled = item.enabled && item.onClick != null,
                interactionSource = interactionSource,
                indication = null,
                onClick = { item.onClick?.invoke() },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.leadingContent != null) {
            item.leadingContent.invoke()
            Spacer(modifier = Modifier.width(14.dp))
        } else item.icon?.let { icon ->
            Surface(
                modifier = Modifier.size(40.dp),
                shape = item.iconShape ?: fallbackShape,
                color = iconBgColor,
                tonalElevation = if (item.isHighlighted) 2.dp else 0.dp,
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            ProvideTextStyle(
                MaterialTheme.typography.titleMedium.copy(
                    color = if (!item.enabled) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                ),
            ) {
                item.title()
            }

            item.description?.let { desc ->
                Spacer(modifier = Modifier.height(2.dp))
                ProvideTextStyle(
                    MaterialTheme.typography.bodySmall.copy(
                        color = if (!item.enabled) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    ),
                ) {
                    desc()
                }
            }
        }

        item.trailingContent?.let { trailing ->
            Spacer(modifier = Modifier.width(10.dp))
            trailing()
        }
    }
}
