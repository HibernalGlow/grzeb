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

    @Test
    fun `manages search history correctly`() {
        val state = SearchUiState()
        assertTrue(state.searchHistory.isEmpty())

        state.addSearchQuery("hello")
        state.addSearchQuery("world")
        assertEquals(listOf("world", "hello"), state.searchHistory.toList())

        // Re-adding existing query brings it to the top
        state.addSearchQuery("hello")
        assertEquals(listOf("hello", "world"), state.searchHistory.toList())

        // Blank or whitespace queries are ignored
        state.addSearchQuery("   ")
        assertEquals(2, state.searchHistory.size)

        // Trimmed
        state.addSearchQuery("  rust  ")
        assertEquals(listOf("rust", "hello", "world"), state.searchHistory.toList())

        // Remove item
        state.removeSearchQuery("hello")
        assertEquals(listOf("rust", "world"), state.searchHistory.toList())

        // Capacity capped at 10 items
        for (i in 1..15) {
            state.addSearchQuery("query_$i")
        }
        assertEquals(10, state.searchHistory.size)
        assertEquals("query_15", state.searchHistory.first())

        // Clear history
        state.clearSearchHistory()
        assertTrue(state.searchHistory.isEmpty())
    }

    @Test
    fun `manages multiple directories and compatibility property correctly`() {
        val state = SearchUiState()
        assertTrue(state.directories.isEmpty())
        assertEquals(null, state.directory)

        // Setting directory updates directories list
        state.directory = "/root/dir1"
        assertEquals("/root/dir1", state.directory)
        assertEquals(listOf("/root/dir1"), state.directories.toList())

        // Adding more directories
        assertTrue(state.addDirectory("/root/dir2"))
        assertFalse(state.addDirectory("/root/dir2")) // Duplicate rejected
        assertTrue(state.addDirectory("/root/dir3"))
        assertEquals(listOf("/root/dir1", "/root/dir2", "/root/dir3"), state.directories.toList())
        assertEquals("/root/dir1", state.directory) // Primary directory remains first

        // Remove a directory
        state.removeDirectory("/root/dir2")
        assertEquals(listOf("/root/dir1", "/root/dir3"), state.directories.toList())

        // Clearing directories
        state.clearDirectories()
        assertTrue(state.directories.isEmpty())
        assertEquals(null, state.directory)
    }

    @Test
    fun `expandAll and collapseAll updates collapsed states`() {
        val state = SearchUiState()
        state.toggleFolder("/folder/a")
        state.toggleFile("/folder/a/file1.kt")
        assertEquals(setOf("/folder/a"), state.collapsedFolders)
        assertEquals(setOf("/folder/a/file1.kt"), state.collapsedFiles)

        state.expandAll()
        assertTrue(state.collapsedFolders.isEmpty())
        assertTrue(state.collapsedFiles.isEmpty())
    }
}
