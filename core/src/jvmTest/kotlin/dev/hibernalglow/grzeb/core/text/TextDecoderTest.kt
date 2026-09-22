package dev.hibernalglow.grzeb.core.text

import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 解码判定的回归样本。
 *
 * 重点不是"能解 UTF-8"，而是 [textDecoderFallsBackToLatin1ForFrench]：
 * 法文这类 Latin-1 正文不能被误判成 GB18030 而变成一屏汉字。
 */
class TextDecoderTest {

    private fun encode(text: String, charset: String): ByteArray =
        text.toByteArray(Charset.forName(charset))

    @Test
    fun decodesUtf8Chinese() {
        val text = "第一行\n刘备 踩脚袜\n第三行"
        assertEquals(text, TextDecoder.decode(text.toByteArray()))
    }

    @Test
    fun stripsUtf8Bom() {
        val text = "带 BOM 的正文"
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.toByteArray()
        assertEquals(text, TextDecoder.decode(bytes))
    }

    /** 中文小说的主战场：GBK / GB18030。 */
    @Test
    fun decodesGb18030Chinese() {
        val text = "他站在廊下，看着院子里的雪一片一片落下来。\n第二段：踩脚袜。"
        assertEquals(text, TextDecoder.decode(encode(text, "GB18030")))
    }

    /** GBK 是 GB18030 的子集，单独验一遍。 */
    @Test
    fun decodesGbkChinese() {
        val text = "轻小说 粮草 字体"
        assertEquals(text, TextDecoder.decode(encode(text, "GBK")))
    }

    /** Latin-1 正文不能被当成中文：重音后面跟空格时不是合法 GBK 尾字节。 */
    @Test
    fun textDecoderFallsBackToLatin1ForFrench() {
        val text = "C'était déjà l'été, à Paris. Où êtes-vous ? Très bien."
        val bytes = encode(text, "ISO-8859-1")
        val decoded = TextDecoder.decode(bytes)
        assertEquals(text, decoded)
    }

    /** 无 BOM 的 UTF-16 只有"ASCII 正文"这种形态能靠零字节规律认出来。 */
    @Test
    fun decodesUtf16LeWithoutBomWhenAscii() {
        val text = "UTF-16 without BOM, ASCII only."
        assertEquals(text, TextDecoder.decode(encode(text, "UTF-16LE")))
    }

    @Test
    fun decodesUtf16WithBom() {
        val text = "带 BOM 的 UTF-16 文本。"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + encode(text, "UTF-16LE")
        assertEquals(text, TextDecoder.decode(bytes))
    }

    /**
     * 已知边界：无 BOM 且正文是汉字时，字节里没有零字节规律可用，会被当成二进制跳过。
     * 真要认这类文件得上统计式探测（legado 那套），留待移植。
     */
    @Test
    fun utf16WithoutBomAndNonAsciiIsTreatedAsBinary() {
        val text = "没有任何 BOM 的 UTF-16 文本。"
        assertNull(TextDecoder.decode(encode(text, "UTF-16LE")))
    }

    @Test
    fun rejectsBinary() {
        // JPEG 头 + 一堆 NUL
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) +
            ByteArray(64) + byteArrayOf(0x01, 0x02, 0x03)
        assertNull(TextDecoder.decode(bytes))
    }

    @Test
    fun emptyFileIsEmptyString() {
        assertEquals("", TextDecoder.decode(ByteArray(0)))
    }
}
