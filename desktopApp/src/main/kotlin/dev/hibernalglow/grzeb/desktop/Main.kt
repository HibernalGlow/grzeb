package dev.hibernalglow.grzeb.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.hibernalglow.grzeb.shared.GrzebApp

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "grzeb",
        // 1120dp 宽正好落在 M3 展开窗口（≥840dp）里，默认就能看到两栏布局
        state = rememberWindowState(size = DpSize(1120.dp, 780.dp)),
    ) {
        GrzebApp()
    }
}
