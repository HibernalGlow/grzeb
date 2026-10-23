package dev.hibernalglow.grzeb.shared.ui.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchUiStateTest {

    @Test
    fun `warm switch is disabled by default`() {
        val state = SearchUiState()
        assertFalse(state.isWarmEnabled, "预热开关默认应为关闭状态")
        assertFalse(state.showSettings, "设置界面默认应为隐藏状态")
        assertTrue(state.customExtensions.isEmpty(), "初始自定义格式列表应为空")
    }

    @Test
    fun `adds and normalizes custom extensions`() {
        val state = SearchUiState()

        assertTrue(state.addCustomExtension("xyz"))
        assertTrue(state.customExtensions.contains(".xyz"))

        assertTrue(state.addCustomExtension(".log2"))
        assertTrue(state.customExtensions.contains(".log2"))

        assertTrue(state.addCustomExtension("  MYFORMAT  "))
        assertTrue(state.customExtensions.contains(".myformat"))

        // 重复添加返回 false
        assertFalse(state.addCustomExtension(".xyz"))
        assertFalse(state.addCustomExtension("xyz"))

        // 内置已有格式被拒绝
        assertFalse(state.addCustomExtension("kt"))
        assertFalse(state.addCustomExtension(".txt"))
        assertFalse(state.addCustomExtension("md"))

        assertEquals(3, state.customExtensions.size)
    }

    @Test
    fun `batch adds custom extensions from separated text`() {
        val state = SearchUiState()

        val addedCount = state.addCustomExtensions("foo, bar; .baz  mydata，other")
        assertEquals(5, addedCount)
        assertTrue(state.customExtensions.containsAll(listOf(".foo", ".bar", ".baz", ".mydata", ".other")))
    }

    @Test
    fun `removes and clears custom extensions`() {
        val state = SearchUiState()
        state.addCustomExtensions("ext1, ext2, ext3")
        assertEquals(3, state.customExtensions.size)

        state.removeCustomExtension("ext2")
        assertEquals(2, state.customExtensions.size)
        assertFalse(state.customExtensions.contains(".ext2"))

        state.clearCustomExtensions()
        assertTrue(state.customExtensions.isEmpty())
    }
}
