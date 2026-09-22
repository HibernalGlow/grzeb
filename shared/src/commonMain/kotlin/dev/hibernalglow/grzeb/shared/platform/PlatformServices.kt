package dev.hibernalglow.grzeb.shared.platform

import androidx.compose.runtime.Composable
import dev.hibernalglow.grzeb.core.fs.GrzebFileSystem
import dev.hibernalglow.grzeb.core.index.IndexStore

/**
 * 平台能力入口。
 *
 * 三件事都必须落到平台 UI API 上：Android 的 SAF 要 ActivityResultLauncher，
 * 桌面是 AWT 对话框，Web 需要 File System Access API。把它们收在一个接口里，
 * UI 层就只剩"选目录 → 存 uri → 建文件系统"这一条与平台无关的流程。
 */
interface PlatformServices {

    /** 该平台是否已接入目录选择（Web 端尚未接入，见 wasmJsMain 的说明）。 */
    val isDirectoryPickerSupported: Boolean

    /** 打开系统目录选择器；用户取消或失败返回 null。 */
    suspend fun pickDirectory(): String?

    /** 按已选目录构造文件系统实例；Android 需要 tree uri 才能列子项。 */
    fun createFileSystem(treeUri: String?): GrzebFileSystem

    /** 交给系统默认程序打开；成功返回 true。 */
    suspend fun openExternally(uri: String): Boolean

    /**
     * 索引库（目录树快照 + 正文缓存）。Android / 桌面落盘，Web 只有内存实现。
     *
     * 只在真正要用的时候才去开库 —— 实现方应当把 driver 的创建做成惰性的。
     */
    fun createIndexStore(): IndexStore
}

@Composable
expect fun rememberPlatformServices(): PlatformServices
