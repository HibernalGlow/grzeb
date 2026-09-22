package dev.hibernalglow.grzeb.core.text

/**
 * 可被当作纯文本读取的扩展名集合。
 * 移植自 react 版 `lib/search/engine.ts` 的 TEXT_EXTENSIONS。
 */
object TextFileTypes {

    private val DEFAULT_EXTENSIONS: Set<String> = setOf(
        // 文档 / 标记
        ".txt", ".md", ".markdown", ".json", ".xml", ".html", ".htm",
        ".css", ".js", ".ts", ".jsx", ".tsx", ".vue", ".svelte",
        // 代码
        ".py", ".java", ".c", ".cpp", ".h", ".hpp", ".cs",
        ".go", ".rs", ".rb", ".php", ".swift", ".kt", ".kts", ".scala",
        ".sh", ".bash", ".zsh", ".ps1", ".bat", ".cmd",
        // 配置
        ".yaml", ".yml", ".toml", ".ini", ".cfg", ".conf",
        ".log", ".csv", ".tsv", ".sql", ".r", ".lua",
        ".org", ".adoc", ".rst", ".tex", ".bib",
        // 小说常见格式（本轮先支持文本，EPUB 由 legado 解析模块接管）
        ".epub", ".mobi", ".fb2", ".rtf",
    )

    /** 判断文件名是否属于可搜索的文本文件。 */
    fun isTextFile(fileName: String, extraExtensions: Collection<String> = emptyList()): Boolean {
        val dot = fileName.lastIndexOf('.')
        if (dot < 0 || dot == fileName.length - 1) return false
        val extension = fileName.substring(dot).lowercase()
        if (DEFAULT_EXTENSIONS.contains(extension)) return true
        return extraExtensions.any { it.lowercase() == extension }
    }

    /** 取小写扩展名（含点），无扩展名返回空串。 */
    fun extensionOf(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        if (dot < 0 || dot == fileName.length - 1) return ""
        return fileName.substring(dot).lowercase()
    }
}
