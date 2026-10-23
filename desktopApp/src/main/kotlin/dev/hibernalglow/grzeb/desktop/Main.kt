package dev.hibernalglow.grzeb.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.hibernalglow.grzeb.shared.GrzebApp
import javax.imageio.ImageIO

fun main() = application {
    val windowIcon = remember {
        try {
            Thread.currentThread().contextClassLoader.getResourceAsStream("icons/icon.png")?.use { stream ->
                ImageIO.read(stream)?.toComposeImageBitmap()?.let { BitmapPainter(it) }
            }
        } catch (_: Throwable) {
            null
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "grzeb",
        icon = windowIcon,
        // 1120dp 宽正好落在 M3 展开窗口（≥840dp）里，默认就能看到两栏布局
        state = rememberWindowState(size = DpSize(1120.dp, 780.dp)),
    ) {
        GrzebApp()
    }
}
