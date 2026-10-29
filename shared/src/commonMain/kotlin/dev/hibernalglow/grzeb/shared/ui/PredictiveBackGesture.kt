package dev.hibernalglow.grzeb.shared.ui

import androidx.compose.runtime.Composable

/**
 * 预测性返回手势（Android 13 起提供，Android 15 起默认启用）。
 *
 * 手势进行中按 0f..1f 回调 [onProgress]，界面据此让当前页跟着手指走；
 * 松手取消时回调 [onCancel]，界面应把进度收回 0；划到底提交时回调 [onDismiss]。
 *
 * 没有系统返回手势的平台（桌面）是空实现，预览仍可用关闭按钮退出。
 */
@Composable
expect fun PredictiveBackGesture(
    enabled: Boolean,
    onProgress: suspend (Float) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
)
