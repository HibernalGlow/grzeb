package dev.hibernalglow.grzeb.core.fs

import dev.hibernalglow.grzeb.core.text.TextDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 桌面（JVM）实现：`uri` 直接就是绝对路径，无需任何授权——这也是选它作为
 * 首选验证平台的原因（Android 的 SAF 要用户点授权，Web 根本没有真实目录）。
 */
class JvmGrzebFileSystem(
    /** 起始目录；默认用户主目录。 */
    private val startDir: String? = null,
) : GrzebFileSystem {

    override val isSupported: Boolean = true

    override suspend fun list(uri: String): List<FileEntry> = withContext(Dispatchers.IO) {
        val dir = File(uri)
        if (!dir.isDirectory) return@withContext emptyList()

        dir.listFiles().orEmpty()
            .map { file ->
                FileEntry(
                    uri = file.absolutePath,
                    name = file.name,
                    isDirectory = file.isDirectory,
                    size = if (file.isFile) file.length() else null,
                )
            }
            // 目录在前，再按名字排——与 react 版的列表顺序一致
            .sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    override suspend fun readText(uri: String): String? = withContext(Dispatchers.IO) {
        val file = File(uri)
        if (!file.isFile) return@withContext null
        // 超大文件不读进内存：否则一次全盘检索就能把堆吃穿
        if (file.length() > MAX_TEXT_BYTES) return@withContext null

        runCatching { TextDecoder.decode(file.readBytes()) }.getOrNull()
    }

    override fun displayName(uri: String): String = File(uri).name.ifEmpty { uri }

    override fun rootLocation(): String? = startDir ?: System.getProperty("user.home")

    private companion object {
        /** 单文件读取上限 8 MB。 */
        const val MAX_TEXT_BYTES = 8L * 1024 * 1024
    }
}
