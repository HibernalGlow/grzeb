package dev.hibernalglow.grzeb.shared.ui

import androidx.compose.runtime.Composable

/** 桌面端没有系统级返回手势，这里不做任何事；预览仍用关闭按钮退出。 */
@Composable
actual fun PredictiveBackGesture(
    enabled: Boolean,
    onProgress: suspend (Float) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    // no-op
}
