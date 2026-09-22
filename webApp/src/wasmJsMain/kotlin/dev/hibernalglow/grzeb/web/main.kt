package dev.hibernalglow.grzeb.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.hibernalglow.grzeb.shared.GrzebApp

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport("composeApp") {
        GrzebApp()
    }
}
