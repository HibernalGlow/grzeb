package dev.hibernalglow.grzeb.core.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TextFileTypesTest {

    @Test
    fun `identifies built-in formats including newly added formats`() {
        // 传统格式
        assertTrue(TextFileTypes.isTextFile("main.kt"))
        assertTrue(TextFileTypes.isTextFile("script.py"))
        assertTrue(TextFileTypes.isTextFile("index.html"))
        assertTrue(TextFileTypes.isTextFile("package.json"))

        // 新增格式：现代语言、排版与字幕
        assertTrue(TextFileTypes.isTextFile("app.dart"))
        assertTrue(TextFileTypes.isTextFile("build.zig"))
        assertTrue(TextFileTypes.isTextFile("paper.typ"))
        assertTrue(TextFileTypes.isTextFile("movie.srt"))
        assertTrue(TextFileTypes.isTextFile("service.proto"))
        assertTrue(TextFileTypes.isTextFile("query.graphql"))
        assertTrue(TextFileTypes.isTextFile("song.lrc"))
        assertTrue(TextFileTypes.isTextFile("data.jsonl"))

        // 非文本文件
        assertFalse(TextFileTypes.isTextFile("image.png"))
        assertFalse(TextFileTypes.isTextFile("archive.zip"))
        assertFalse(TextFileTypes.isTextFile("binary.exe"))
        assertFalse(TextFileTypes.isTextFile("no_extension"))
    }

    @Test
    fun `supports custom extra extensions with and without leading dot`() {
        val extras = listOf(".custom", "xyz", "MYFORMAT")

        assertTrue(TextFileTypes.isTextFile("test.custom", extras))
        assertTrue(TextFileTypes.isTextFile("test.xyz", extras))
        assertTrue(TextFileTypes.isTextFile("test.myformat", extras))
        assertTrue(TextFileTypes.isTextFile("TEST.XYZ", extras))

        assertFalse(TextFileTypes.isTextFile("test.other", extras))
    }

    @Test
    fun `normalizes user input extensions correctly`() {
        assertEquals(".xyz", TextFileTypes.normalizeExtension("xyz"))
        assertEquals(".xyz", TextFileTypes.normalizeExtension(".xyz"))
        assertEquals(".custom", TextFileTypes.normalizeExtension("  .CUSTOM  "))
        assertEquals(".log2", TextFileTypes.normalizeExtension("log2"))

        // 无效输入
        assertNull(TextFileTypes.normalizeExtension(""))
        assertNull(TextFileTypes.normalizeExtension("   "))
        assertNull(TextFileTypes.normalizeExtension("."))
        assertNull(TextFileTypes.normalizeExtension("foo bar"))
        assertNull(TextFileTypes.normalizeExtension("a/b"))
    }
}
