package dev.hibernalglow.grzeb.core.fs

/**
 * 目录中的一个条目。
 *
 * 与 react 版不同：条目自带 [isDirectory]，避免旧实现里"读一次目录来探测类型"
 * 的额外 IO（见 `lib/search/engine.ts` 的 isDirectory 启发式判断）。
 */
data class FileEntry(
    /** 平台无关的定位符：Android 为 content:// SAF URI，桌面为绝对路径，Web 为句柄 id。 */
    val uri: String,
    /** 展示名（文件名或目录名）。 */
    val name: String,
    val isDirectory: Boolean,
    /** 字节数，未知时为 null。 */
    val size: Long? = null,
)

/**
 * 跨平台文件系统抽象。
 *
 * react 版把 SAF 的调用直接散在搜索引擎里，导致逻辑无法离开 Android。
 * 这里把"列目录 / 读文本"抽出为接口，各平台用 actual 实现：
 * - androidMain → SAF（Storage Access Framework）
 * - jvmMain     → java.io / java.nio
 * - iosMain     → 最低兼容（暂不支持，返回空）
 * - wasmJsMain  → 最低兼容
 */
interface GrzebFileSystem {

    /** 该平台的实现是否具备真实能力（用于 UI 给出提示）。 */
    val isSupported: Boolean

    /** 列出目录内容；失败返回空列表。 */
    suspend fun list(uri: String): List<FileEntry>

    /** 读取整文件为文本；失败返回 null。 */
    suspend fun readText(uri: String): String?

    /** 用于展示的短名。 */
    fun displayName(uri: String): String

    /** 默认起始目录（Android 需用户授权后才有值）。 */
    fun rootLocation(): String?
}
