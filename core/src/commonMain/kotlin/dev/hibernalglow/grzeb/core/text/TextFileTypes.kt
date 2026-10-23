package dev.hibernalglow.grzeb.core.text

/**
 * 可被当作纯文本读取的扩展名集合与分类管理。
 * 原生内置主流开发语言、标记语言、现代语言、工程配置、文档及常见文本格式，并支持用户动态扩充。
 */
object TextFileTypes {

    /** 格式分类结构，供 UI 展示分类与统计使用。 */
    data class FormatCategory(
        val name: String,
        val description: String,
        val extensions: List<String>,
    )

    /** 内置分类扩展名集合。 */
    val PRESET_CATEGORIES: List<FormatCategory> = listOf(
        FormatCategory(
            name = "代码与脚本",
            description = "主流编程语言与系统控制台脚本",
            extensions = listOf(
                ".py", ".pyw", ".ipynb",
                ".java", ".kt", ".kts", ".scala", ".groovy", ".gradle",
                ".c", ".cpp", ".cc", ".cxx", ".h", ".hpp", ".hh", ".hxx", ".cs",
                ".go", ".rs", ".rb", ".php", ".swift",
                ".sh", ".bash", ".zsh", ".fish", ".ps1", ".bat", ".cmd",
                ".asm", ".s",
            ),
        ),
        FormatCategory(
            name = "现代与函数式语言",
            description = "新兴语言、领域特定语言与接口协议",
            extensions = listOf(
                ".dart", ".zig", ".nim", ".v", ".odin",
                ".erl", ".ex", ".exs",
                ".clj", ".cljs", ".cljc", ".edn",
                ".fs", ".fsi", ".fsx", ".hs", ".lhs", ".ml", ".mli",
                ".jl", ".sol", ".proto", ".graphql", ".gql", ".wat",
            ),
        ),
        FormatCategory(
            name = "Web 与样式",
            description = "Web 页面模板、样式表与前端组件",
            extensions = listOf(
                ".html", ".htm", ".xhtml",
                ".css", ".scss", ".sass", ".less", ".styl",
                ".js", ".mjs", ".cjs", ".jsx",
                ".ts", ".mts", ".cts", ".tsx",
                ".vue", ".svelte", ".astro",
            ),
        ),
        FormatCategory(
            name = "配置与工程规范",
            description = "声明式配置文件、环境变量与构建规范",
            extensions = listOf(
                ".json", ".json5", ".jsonl", ".ndjson",
                ".yaml", ".yml", ".toml", ".xml",
                ".ini", ".cfg", ".conf", ".properties", ".env",
                ".editorconfig", ".gitattributes", ".gitignore", ".dockerignore",
                ".dockerfile", ".containerfile", ".cmake", ".make", ".mk",
                ".hcl", ".tf", ".tfvars",
            ),
        ),
        FormatCategory(
            name = "文档与笔记",
            description = "学术排版、轻量标记与个人知识库文档",
            extensions = listOf(
                ".txt", ".text", ".md", ".markdown", ".mdown", ".mkd",
                ".org", ".adoc", ".asciidoc", ".rst", ".tex", ".bib",
                ".typ", ".nfo",
            ),
        ),
        FormatCategory(
            name = "数据、查询与轻脚本",
            description = "表格数据、数据库脚本与文本处理工具",
            extensions = listOf(
                ".log", ".csv", ".tsv", ".sql", ".r", ".lua",
                ".awk", ".sed", ".perl", ".pl", ".pm",
            ),
        ),
        FormatCategory(
            name = "字幕与歌词",
            description = "音视频纯文本时间轴与歌词文件",
            extensions = listOf(
                ".srt", ".vtt", ".lrc", ".ass", ".ssa",
            ),
        ),
        FormatCategory(
            name = "电子书纯文本格式",
            description = "常见纯文本小说及排版文件",
            extensions = listOf(
                ".epub", ".mobi", ".fb2", ".rtf",
            ),
        ),
    )

    /** 所有默认内置支持的文件扩展名集合。 */
    val DEFAULT_EXTENSIONS: Set<String> = PRESET_CATEGORIES.flatMap { it.extensions }.toSet()

    /** 判断文件名是否属于可搜索的文本文件。 */
    fun isTextFile(fileName: String, extraExtensions: Collection<String> = emptyList()): Boolean {
        val dot = fileName.lastIndexOf('.')
        if (dot < 0 || dot == fileName.length - 1) return false
        val extension = fileName.substring(dot).lowercase()
        if (DEFAULT_EXTENSIONS.contains(extension)) return true
        return extraExtensions.any {
            val normalized = if (it.startsWith(".")) it.lowercase() else ".$it".lowercase()
            normalized == extension
        }
    }

    /** 取小写扩展名（含点），无扩展名返回空串。 */
    fun extensionOf(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        if (dot < 0 || dot == fileName.length - 1) return ""
        return fileName.substring(dot).lowercase()
    }

    /** 规范化用户输入的扩展名（去空白、转小写、补齐前导点）。若格式无效则返回 null。 */
    fun normalizeExtension(input: String): String? {
        val trimmed = input.trim().lowercase()
        if (trimmed.isEmpty()) return null
        val ext = if (trimmed.startsWith(".")) trimmed else ".$trimmed"
        if (ext.length < 2 || ext.contains(" ") || ext.contains("/") || ext.contains("\\")) return null
        return ext
    }
}
