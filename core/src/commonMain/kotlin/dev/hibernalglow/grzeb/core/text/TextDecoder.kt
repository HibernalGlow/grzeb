package dev.hibernalglow.grzeb.core.text

/**
 * 跨平台文本解码。
 *
 * react 版直接用了 RN 提供的 `TextDecoder`，一到 Kotlin 侧就没有对应物了。
 * 这里给出一份不依赖平台 API 的实现：
 * 1. 含 NUL 字节 → 判为二进制，返回 null（否则 jpg / apk / zip 会被当文本检索）；
 * 2. 剥掉 UTF-8 BOM；
 * 3. 先按 UTF-8 解码，若替换字符（U+FFFD）占比过高，退化为 Latin-1 逐字节映射，
 *    这样 GBK / Windows-1252 之类的文本至少不会整篇乱码。
 *
 * 中文编码的完整识别（legado 那套 编码探测器 + 智能探测）留在移植清单里。
 */
object TextDecoder {

    /** 解码为文本；判定为二进制时返回 null。 */
    fun decode(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return ""
        if (bytes.any { it == 0.toByte() }) return null

        val start = if (hasUtf8Bom(bytes)) 3 else 0
        val body = if (start == 0) bytes else bytes.copyOfRange(start, bytes.size)

        val utf8 = body.decodeToString()
        val broken = utf8.count { it == REPLACEMENT }
        if (broken == 0) return utf8

        // 替换字符超过十分之一 → 大概率不是 UTF-8
        return if (broken * 10 > utf8.length) decodeLatin1(body) else utf8
    }

    /** 不含 NUL 字节即认为"有可能是文本"。 */
    fun isProbablyText(bytes: ByteArray): Boolean = bytes.none { it == 0.toByte() }

    private fun hasUtf8Bom(bytes: ByteArray): Boolean =
        bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()

    /** 逐字节升位映射：Latin-1 / Windows-1252 的近似，保证不丢字节。 */
    private fun decodeLatin1(bytes: ByteArray): String {
        val chars = CharArray(bytes.size)
        for (i in bytes.indices) {
            chars[i] = (bytes[i].toInt() and 0xFF).toChar()
        }
        return chars.concatToString()
    }

    private const val REPLACEMENT = '\uFFFD'
}
