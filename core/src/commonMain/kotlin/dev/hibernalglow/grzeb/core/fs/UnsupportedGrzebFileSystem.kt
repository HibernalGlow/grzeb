package dev.hibernalglow.grzeb.core.fs

/**
 * 最低兼容实现：声明能力但实际不提供。
 *
 * 目前用于 iOS 与 Web——这两端的目录访问都要各自接系统选择器
 * （iOS 的 UIDocumentPicker、Web 的 File System Access API），
 * 属于本轮之后的移植项。放在 commonMain 是为了让它们不必各写一份空壳。
 *
 * [isSupported] 为 false 时，UI 会显示"当前平台暂不支持"的提示而不是空白界面。
 */
class UnsupportedGrzebFileSystem(
    private val reason: String = "当前平台暂未接入目录访问",
) : GrzebFileSystem {

    override val isSupported: Boolean = false

    override suspend fun list(uri: String): List<FileEntry> = emptyList()

    override suspend fun readText(uri: String): String? = null

    override fun displayName(uri: String): String = uri

    override fun rootLocation(): String? = null

    /** 供 UI 展示的原因说明。 */
    fun reason(): String = reason
}
