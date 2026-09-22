package dev.hibernalglow.grzeb.shared.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * 本工程用到的 Material 图标（24dp）。
 *
 * 不引 material-icons-core：该库停在 1.7.3，与 CMP 1.11 的 ui 已经脱节；
 * 而 folder / description 这类图标只在体积大得多的 extended 包里。
 * 这里的 path 数据逐个取自 google/material-design-icons 的 `materialicons/24px.svg`，
 * 未经改动。
 */
object GrzebIcons {

    val Search: ImageVector by lazy {
        icon(
            name = "Search",
            pathData = "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5" +
                " 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01" +
                " 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z",
        )
    }

    val Close: ImageVector by lazy {
        icon(
            name = "Close",
            pathData = "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z",
        )
    }

    val ChevronRight: ImageVector by lazy {
        icon(
            name = "ChevronRight",
            pathData = "M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z",
        )
    }

    val ExpandMore: ImageVector by lazy {
        icon(
            name = "ExpandMore",
            pathData = "M16.59 8.59L12 13.17 7.41 8.59 6 10l6 6 6-6z",
        )
    }

    val Folder: ImageVector by lazy {
        icon(
            name = "Folder",
            pathData = "M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z",
        )
    }

    val Description: ImageVector by lazy {
        icon(
            name = "Description",
            pathData = "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2" +
                " 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z",
        )
    }
}

private fun icon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(pathData),
        fill = SolidColor(Color.Black),
    ).build()
