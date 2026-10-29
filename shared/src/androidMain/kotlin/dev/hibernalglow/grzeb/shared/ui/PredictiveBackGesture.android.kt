package dev.hibernalglow.grzeb.shared.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CancellationException

/**
 * Android 走 androidx.activity 的 [PredictiveBackHandler]：
 * 既接预测性返回手势的进度回调，也在没有手势的旧设备上接普通返回键（提交时直接回调 [onDismiss]）。
 */
@Composable
actual fun PredictiveBackGesture(
    enabled: Boolean,
    onProgress: suspend (Float) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    PredictiveBackHandler(enabled = enabled) { progress ->
        try {
            progress.collect { event -> onProgress(event.progress) }
            onDismiss()
        } catch (cancellation: CancellationException) {
            // 手势松手取消。这里不能直接跑 suspend 的回弹动画：当前协程已被取消，
            // 任何挂起点都会立刻再抛一次，所以只通知调用方，由它自己的作用域去收尾。
            onCancel()
            throw cancellation
        }
    }
}
