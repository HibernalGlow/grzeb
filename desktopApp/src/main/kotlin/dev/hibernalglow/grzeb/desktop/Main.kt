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
        state = rememberWindowState(size = DpSize(480.dp, 800.dp)),
    ) {
        GrzebApp()
    }
}
