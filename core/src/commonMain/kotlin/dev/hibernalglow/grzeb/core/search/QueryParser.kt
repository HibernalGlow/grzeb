package dev.hibernalglow.grzeb.core.search

/**
 * 查询里的一个词。
 *
 * [isExcluded] 为 true 表示排除词（`-词`）：命中它的文件整份出局。
 */
data class SearchTerm(
    val text: String,
    val isExcluded: Boolean = false,
)

/**
 * 编译好的查询。
 *
 * [includes] 之间是**无序 AND**：每一条都要在文件里出现，文件才算命中；
 * 但**不要求它们出现在同一行**——这就是"多关键词、可以不在同一行"的语义。
 *
 * [includes] 为空表示"没什么可搜的"（空 query，或整条 query 只有排除词），
 * 此时检索直接返回空结果，不必去遍历目录。
 */
data class SearchQuery(
    val includes: List<Regex>,
    val excludes: List<Regex> = emptyList(),
)

/**
 * 把用户输入的 query 编译成 [SearchQuery]。
 *
 * 移植自 react 版 `lib/search/types.ts` 的单 pattern 模型，但拆成了"编译"这一步：
 * 编译只做一次（构造 [SearchEngine] 时），遍历每个文件时复用同一组 [Regex]。
 *
 * ## 匹配模式（三选一，互斥）
 * - **字面量**（默认）：整个词按字面量匹配，`*` 只是普通字符
 * - **通配符**（[SearchOptions.isWildcard]）：`*` = 任意字符**含换行**，`?` = 任意单个**非换行**字符
 * - **正则**（[SearchOptions.isRegex]）：整条 query 原样编译，**不切词**
 *
 * ## 切词（[SearchOptions.isMultiKeyword]，仅非正则模式生效）
 * - 空白分词：半角空格、全角空格（U+3000）、Tab、换行、不换行空格（U+00A0）
 * - `"短语"` / `“短语”`：其中的空白不切分
 * - `-词`：排除词；被引号包住时不当作排除（`"-abc"` 搜的是字面量 `-abc`）
 *
 * 正则模式不切词，因为切词会破坏本身含空白的表达式（`\d+ \w+`、`a{1, 3}`）；
 * 正则本身已经足够表达"与或非"，不需要再叠一层关键词语法。
 */
object QueryParser {

    /**
     * 分词用的空白字符。
     *
     * 显式列出而不是只用 `Char.isWhitespace()`：中文输入法极易打出全角空格（U+3000），
     * 而断行保护空格（U+00A0）在 Java 的 `Character.isWhitespace` 里**不算**空白，
     * 只靠 `split(" ")` 或只靠 `isWhitespace()` 都会漏。
     */
    private const val TERM_BREAKS = " \t\n\r\u000B\u000C\u3000\u00A0"

    /** 通配符模式下需要转义的元字符；`*` 与 `?` 已在前面单独处理，不在此列。 */
    private const val REGEX_META = "\\^$|()[]{}+."

    private const val QUOTE_OPEN = '"'
    private const val QUOTE_OPEN_CJK = '“'
    private const val QUOTE_CLOSE_CJK = '”'

    /**
     * 编译整条 query。
     *
     * 返回 null 只有一种情况：某个词编译不成合法正则（用户在正则模式下手滑）。
     * UI 据此提示"表达式无效"。
     */
    fun parse(options: SearchOptions): SearchQuery? {
        if (options.query.isEmpty()) return SearchQuery(emptyList())

        val terms = if (options.isRegex || !options.isMultiKeyword) {
            listOf(SearchTerm(options.query))
        } else {
            splitTerms(options.query)
        }

        val includes = mutableListOf<Regex>()
        val excludes = mutableListOf<Regex>()
        for (term in terms) {
            val regex = termRegex(term.text, options) ?: return null
            if (term.isExcluded) excludes += regex else includes += regex
        }
        return SearchQuery(includes = includes, excludes = excludes)
    }

    /**
     * 按空白切词，同时处理引号短语与 `-` 排除前缀。
     *
     * 落单的 `-`（前后都没有内容）当作字面量，不去吞用户输入的减号。
     */
    fun splitTerms(raw: String): List<SearchTerm> {
        val terms = mutableListOf<SearchTerm>()
        val buffer = StringBuilder()
        var isExcluded = false

        /** 刚吃掉一个词首 `-`，等确认后面真的有内容才算"排除词"。 */
        var pendingDash = false

        /** 非 null 表示正在引号内，值为与之配对的收尾引号。 */
        var closingQuote: Char? = null

        fun flush() {
            val text = buffer.toString()
            when {
                text.isNotEmpty() -> terms += SearchTerm(text, isExcluded)
                pendingDash -> terms += SearchTerm("-")
            }
            buffer.clear()
            isExcluded = false
            pendingDash = false
        }

        for (ch in raw) {
            val closing = closingQuote
            when {
                closing != null -> if (ch == closing) closingQuote = null else buffer.append(ch)
                ch == QUOTE_OPEN -> closingQuote = QUOTE_OPEN
                ch == QUOTE_OPEN_CJK -> closingQuote = QUOTE_CLOSE_CJK
                ch in TERM_BREAKS || ch.isWhitespace() -> flush()
                ch == '-' && buffer.isEmpty() && !isExcluded -> {
                    isExcluded = true
                    pendingDash = true
                }
                else -> {
                    buffer.append(ch)
                    pendingDash = false
                }
            }
        }
        flush()
        return terms
    }

    /**
     * 编译单个词。
     *
     * **全词匹配对中文的处理**：[SearchOptions.isWholeWord] 只对不含中日韩字符的词加 `\b`。
     * JVM 的 `\b` 建立在 ASCII `\w`（`[a-zA-Z0-9_]`）之上，而中文字符不属于 `\w`，
     * 于是 `\b张三\b` 里两个边界位置**永远**不成立——它是恒假的 pattern。
     * 照搬的后果是"勾上全词匹配 + 搜中文 = 结果恒为 0"，而且界面上只说"没有找到匹配的结果"，
     * 毫无提示。中文没有分词信息，"全词"本身也无从定义，因此对中文退化为子串匹配。
     *
     * 通配符模式同样不加边界：`*` 的含义就是"前后可以有任意内容"，与全词匹配本就矛盾。
     */
    fun termRegex(term: String, options: SearchOptions): Regex? {
        val body = when {
            options.isRegex -> term
            options.isWildcard -> wildcardToRegex(term)
            else -> Regex.escape(term)
        }

        val pattern = if (options.isWholeWord && !options.isWildcard && !containsCjk(term)) {
            "\\b$body\\b"
        } else {
            body
        }

        val flags = buildSet {
            if (!options.isCaseSensitive) add(RegexOption.IGNORE_CASE)
            add(RegexOption.MULTILINE)
        }
        return runCatching { Regex(pattern, flags) }.getOrNull()
    }

    /**
     * 通配符转正则。
     *
     * `*` 用 `[\s\S]*?` 而不是靠 `(?s)` 打开 DOTALL：DOTALL 是全局开关，会把 `?`
     * 也一并变成可跨行，而 `?` 的语义应当是"任意一个非换行字符"——靠显式字符类
     * 才能让 `*` 和 `?` 各自保持自己的宽度。
     *
     * 非贪婪（`*?`）是为了让 `第*章` 这类查询尽量贴着最短匹配，预览才不会被撑开。
     */
    private fun wildcardToRegex(term: String): String {
        val out = StringBuilder()
        for (ch in term) {
            when (ch) {
                '*' -> out.append("[\\s\\S]*?")
                '?' -> out.append("[^\\n]")
                else -> {
                    if (ch in REGEX_META) out.append('\\')
                    out.append(ch)
                }
            }
        }
        return out.toString()
    }

    /** 是否含中日韩字符（含假名与谚文），用于决定要不要加 `\b`。 */
    fun containsCjk(text: String): Boolean = text.any { isCjk(it) }

    private fun isCjk(ch: Char): Boolean = when (ch.code) {
        in 0x4E00..0x9FFF -> true   // 中日韩统一表意文字
        in 0x3400..0x4DBF -> true   // 扩展 A
        in 0xF900..0xFAFF -> true   // 兼容表意文字
        in 0x3040..0x30FF -> true   // 平假名 / 片假名
        in 0xAC00..0xD7AF -> true   // 谚文音节
        else -> false
    }
}
