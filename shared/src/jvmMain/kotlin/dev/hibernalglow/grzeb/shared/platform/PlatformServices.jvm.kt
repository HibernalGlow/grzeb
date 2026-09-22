package dev.hibernalglow.grzeb.shared.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.hibernalglow.grzeb.core.fs.GrzebFileSystem
import dev.hibernalglow.grzeb.core.fs.JvmGrzebFileSystem
import dev.hibernalglow.grzeb.core.index.IndexStore
import dev.hibernalglow.grzeb.core.index.SqlDelightIndexStore
import dev.hibernalglow.grzeb.core.index.createJvmIndexDriver
import dev.hibernalglow.grzeb.core.index.defaultJvmIndexPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser

@Composable
actual fun rememberPlatformServices(): PlatformServices = remember { JvmPlatformServices() }

/**
 * 桌面侧：AWT 原生目录对话框 + 系统默认程序打开。
 *
 * macOS 上 `apple.awt.fileDialogForDirectories` 是让 FileDialog 只选目录的开关
 * （必须在创建对话框之前设好）；其他系统用 Swing 的 JFileChooser ——
 * 这两个是各自平台上现成、且能"只选目录"的对话框。
 */
private class JvmPlatformServices : PlatformServices {

    override val isDirectoryPickerSupported: Boolean = true

    override suspend fun pickDirectory(): String? = withContext(Dispatchers.Swing) {
        if (isMacOs) pickWithNativeDialog() else pickWithSwingChooser()
    }

    override fun createFileSystem(treeUri: String?): GrzebFileSystem = JvmGrzebFileSystem()

    /** 惰性开库：库落在 `~/.grzeb/index.db`。 */
    private val index: IndexStore by lazy {
        SqlDelightIndexStore.create(createJvmIndexDriver(defaultJvmIndexPath()))
    }

    override fun createIndexStore(): IndexStore = index

    override suspend fun openExternally(uri: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(uri)
            if (!file.exists() || !Desktop.isDesktopSupported()) return@runCatching false
            Desktop.getDesktop().open(file)
            true
        }.getOrDefault(false)
    }

    override val isRevealSupported: Boolean = true

    override suspend fun revealInFileManager(uri: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(uri).absoluteFile
            if (!file.exists()) return@runCatching false
            // Desktop API 没有"在文件管理器里选中"这一项，只能按平台各调一次系统命令：
            // macOS 的 `open -R`、Windows 的 `explorer /select,`（路径要整条给一个参数），
            // Linux 没有通用的选中接口，退化为打开它所在的目录
            val command = when {
                isMacOs -> listOf("open", "-R", file.path)
                isWindows -> listOf("explorer", "/select,${file.path}")
                else -> listOf("xdg-open", file.parent ?: file.path)
            }
            ProcessBuilder(command).start()
            true
        }.getOrDefault(false)
    }

    private fun pickWithNativeDialog(): String? {
        System.setProperty("apple.awt.fileDialogForDirectories", "true")
        val dialog = FileDialog(null as Frame?, "选择搜索目录", FileDialog.LOAD)
        return try {
            dialog.isVisible = true
            val parent = dialog.directory
            val name = dialog.file
            if (parent == null || name == null) null else File(parent, name).absolutePath
        } finally {
            dialog.dispose()
        }
    }

    private fun pickWithSwingChooser(): String? {
        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "选择搜索目录"
        }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile?.absolutePath
        } else {
            null
        }
    }

    private companion object {
        val isMacOs: Boolean = System.getProperty("os.name").orEmpty().startsWith("Mac")
        val isWindows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows")
    }
}
