package dev.hibernalglow.grzeb.core.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 查询语法的回归样本。
 *
 * 三个重点：
 * 1. 分词必须认得**全角空格**——中文输入法下极易打出 U+3000，只按半角切会把
 *    `张三　卡号` 当成一个词，于是恒不命中，而且界面上毫无提示。
 * 2. `*` 要能跨行、`?` 不许跨行。这条靠 DOTALL 实现不了（DOTALL 是全局开关，
 *    会把 `?` 也一起放开），必须显式写字符类。
 * 3. 全词匹配对中文不能加 `\b`——中文字符不属于 ASCII 的 `\w`，两侧的边界位置
 *    永远不成立，`\b张三\b` 是恒假的 pattern。
 */
class QueryParserTest {

    private fun terms(raw: String) = QueryParser.splitTerms(raw)

    private fun singleRegex(options: SearchOptions): Regex =
        QueryParser.parse(options)?.includes?.single()
            ?: error("expects exactly one term: ${options.query}")

    @Test
    fun splitsOnHalfWidthSpace() {
        assertEquals(listOf("张三", "卡号"), terms("张三 卡号").map { it.text })
    }

    /** 全角空格：中文输入法下的默认空格，必须与半角同等对待。 */
    @Test
    fun splitsOnFullWidthSpace() {
        assertEquals(listOf("张三", "卡号"), terms("张三\u3000卡号").map { it.text })
    }

    /** U+00A0 在 Java 的 `Character.isWhitespace` 里不算空白，最容易漏。 */
    @Test
    fun splitsOnTabNbspAndNewline() {
        assertEquals(listOf("a", "b", "c", "d"), terms("a\tb\u00A0c\nd").map { it.text })
    }

    @Test
    fun keepsQuotedPhraseTogether() {
        assertEquals(listOf("招商银行 卡号"), terms("\"招商银行 卡号\"").map { it.text })
    }

    @Test
    fun keepsFullWidthQuotedPhraseTogether() {
        assertEquals(listOf("招商银行 卡号"), terms("“招商银行 卡号”").map { it.text })
    }

    @Test
    fun marksDashPrefixedTermAsExcluded() {
        assertEquals(
            listOf(SearchTerm("测试", isExcluded = true), SearchTerm("张三")),
            terms("-测试 张三"),
        )
    }

    /** 引号里的减号是字面量：用户可能真的要搜 `-abc` 这串东西。 */
    @Test
    fun quotedDashIsLiteralNotExclusion() {
        assertEquals(listOf(SearchTerm("-测试")), terms("\"-测试\""))
    }

    /** 落单的减号不能被吞掉。 */
    @Test
    fun loneDashStaysLiteral() {
        assertEquals(listOf(SearchTerm("-")), terms("-"))
    }

    /** 词中间的减号本来就不是排除前缀，`some-flag` 是一个词。 */
    @Test
    fun dashInsideTermIsNotExclusion() {
        assertEquals(listOf(SearchTerm("some-flag")), terms("some-flag"))
    }

    /** 字面量模式：`.` 只是句点，不是"任意字符"。 */
    @Test
    fun literalModeEscapesRegexMetacharacters() {
        val regex = singleRegex(SearchOptions(query = "3.14"))
        assertTrue(regex.containsMatchIn("圆周率 3.14"))
        assertFalse(regex.containsMatchIn("3x14"))
    }

    /** `*` 跨行：这是"多关键词可以不在同一行"的通配符表达。 */
    @Test
    fun wildcardStarSpansLines() {
        val regex = singleRegex(SearchOptions(query = "张三*卡号", isWildcard = true))
        assertTrue(regex.containsMatchIn("张三\n开户行\n卡号"))
    }

    /** `?` 只能吃一个非换行字符，不能沾到 `*` 的跨行能力。 */
    @Test
    fun wildcardQuestionMarkDoesNotSpanLines() {
        val regex = singleRegex(SearchOptions(query = "张三?卡号", isWildcard = true))
        assertFalse(regex.containsMatchIn("张三\n卡号"))
        assertTrue(regex.containsMatchIn("张三 卡号"))
    }

    /** 通配符模式其余的元字符仍然要转义，`1+1` 不能变成"1 重复一次"。 */
    @Test
    fun wildcardEscapesOtherMetacharacters() {
        val regex = singleRegex(SearchOptions(query = "1+1", isWildcard = true))
        assertTrue(regex.containsMatchIn("1+1=2"))
        assertFalse(regex.containsMatchIn("111=2"))
    }

    /** 修复用例：全词匹配 + 中文此前恒不命中（`\b张三\b` 在任何位置都是 false）。 */
    @Test
    fun wholeWordIgnoredForCjk() {
        val regex = singleRegex(SearchOptions(query = "张三", isWholeWord = true))
        assertTrue(regex.containsMatchIn("这是张三的书"))
    }

    /** 纯 ASCII 词仍然保留词边界语义。 */
    @Test
    fun wholeWordStillAppliesToAscii() {
        val regex = singleRegex(SearchOptions(query = "out", isWholeWord = true))
        assertFalse(regex.containsMatchIn("timeout"))
        assertTrue(regex.containsMatchIn("超时 out 了"))
    }

    /** 默认（未开多关键词）时整条 query 是一个词，空格按字面量处理——旧行为不变。 */
    @Test
    fun multiKeywordIsOffByDefault() {
        assertEquals(1, QueryParser.parse(SearchOptions(query = "张三 李四"))!!.includes.size)
    }

    @Test
    fun multiKeywordSplitsIntoAndTerms() {
        val plan = QueryParser.parse(SearchOptions(query = "张三 卡号 招商银行", isMultiKeyword = true))!!
        assertEquals(3, plan.includes.size)
        assertTrue(plan.excludes.isEmpty())
    }

    @Test
    fun multiKeywordSeparatesExclusions() {
        val plan = QueryParser.parse(SearchOptions(query = "张三 -测试", isMultiKeyword = true))!!
        assertEquals(1, plan.includes.size)
        assertEquals(1, plan.excludes.size)
    }

    /** 正则模式不切词：`\d+ \w+` 里的空格是表达式的一部分，切了就废了。 */
    @Test
    fun regexModeDoesNotSplitTerms() {
        val plan = QueryParser.parse(
            SearchOptions(query = "\\d+ \\w+", isRegex = true, isMultiKeyword = true),
        )!!
        assertEquals(1, plan.includes.size)
    }

    /** 正则非法时整条计划作废，UI 据此提示"表达式无效"。 */
    @Test
    fun invalidRegexMakesPlanNull() {
        assertNull(QueryParser.parse(SearchOptions(query = "[", isRegex = true)))
    }

    @Test
    fun emptyQueryYieldsNoIncludes() {
        assertTrue(QueryParser.parse(SearchOptions(query = ""))!!.includes.isEmpty())
    }
}
