package dev.hibernalglow.grzeb.shared.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.hibernalglow.grzeb.core.fs.GrzebFileSystem
import dev.hibernalglow.grzeb.core.fs.UnsupportedGrzebFileSystem
import dev.hibernalglow.grzeb.core.index.InMemoryIndexStore
import dev.hibernalglow.grzeb.core.index.IndexStore

/**
 * Web 侧：目录访问要接 File System Access API（showDirectoryPicker + 句柄持久化），
 * 属于后续移植项。这里先给出能力声明为 false 的实现 ——
 * UI 会显示"暂未接入"，而不是给一个点不动的空界面。
 */
@Composable
actual fun rememberPlatformServices(): PlatformServices = remember {
    object : PlatformServices {

        override val isDirectoryPickerSupported: Boolean = false

        override suspend fun pickDirectory(): String? = null

        override fun createFileSystem(treeUri: String?): GrzebFileSystem =
            UnsupportedGrzebFileSystem(WEB_REASON)

        override suspend fun openExternally(uri: String): Boolean = false

        override val isRevealSupported: Boolean = false

        override suspend fun revealInFileManager(uri: String): Boolean = false

        /** Web 端没有目录访问，索引也就只是会话内的内存缓存。 */
        override fun createIndexStore(): IndexStore = InMemoryIndexStore()
    }
}

private const val WEB_REASON = "Web 端目录访问需接 File System Access API，尚未接入"
