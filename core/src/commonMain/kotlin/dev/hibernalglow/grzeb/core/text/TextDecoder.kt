package dev.hibernalglow.grzeb.core.text

/**
 * 跨平台文本解码。
 *
 * 判定顺序：
 * 1. BOM：UTF-8 / UTF-16 LE / UTF-16 BE；
 * 2. 带 NUL 字节的，看是不是"隔一个字节一个 NUL"的无 BOM UTF-16，否则判为二进制
 *    （jpg / apk / zip 都会在这里被挡掉）；
 * 3. 按 UTF-8 解，没有替换字符就采用；
 * 4. 退 GB18030（GBK / GB2312 的超集）—— 中文小说绝大多数是它。两道门槛：
 *    解出来不能有替换字符、且汉字占比够高。第一道挡住 Latin-1 正文误判（法文的
 *    重音字节后面常常跟空格或标点，不是合法 GBK 尾字节，会解出替换字符），
 *    第二道挡住"基本是 ASCII、只有零星高位字节"的文件；
 * 5. 兜底 Latin-1 逐字节映射，保证不丢字节。
 *
 * 注：legado 那套统计式编码探测（juniversalchardet 一类）留待移植。这里的启发式覆盖
 * UTF-8 / GB18030 / UTF-16（带 BOM，或 ASCII 正文的无 BOM 形态）/ Latin-1 四种；
 * 无 BOM 且正文是汉字的 UTF-16 认不出来（字节里没有可用的零字节规律），会当二进制跳过。
 */
object TextDecoder {

    /** GBK、GB2312 都是它的子集，用这一个就够了。 */
    private const val GB18030 = "GB18030"

    /** 汉字占全文比例低于此值就不认 GB18030 的判定。 */
    private const val MIN_CJK_SHARE = 0.05

    /** 控制字符占比高于此值就不认无 BOM UTF-16 的判定。 */
    private const val MAX_CONTROL_SHARE = 0.05

    /** 解码为文本；判定为二进制时返回 null。 */
    fun decode(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return ""

        decodeByBom(bytes)?.let { return it }

        if (bytes.any { it == 0.toByte() }) {
            return decodeUtf16WithoutBom(bytes)
        }

        val utf8 = bytes.decodeToString()
        if (utf8.none { it == REPLACEMENT }) return utf8

        decodeWithCharset(GB18030, bytes)?.let { gb ->
            if (gb.none { it == REPLACEMENT } && cjkShare(gb) >= MIN_CJK_SHARE) return gb
        }

        // 替换字符超过十分之一 → 大概率不是 UTF-8（也不是 GB18030）
        return if (utf8.count { it == REPLACEMENT } * 10 > utf8.length) decodeLatin1(bytes) else utf8
    }

    /** 不含 NUL 字节，或有 BOM / 呈 UTF-16 形态，即认为"有可能是文本"。 */
    fun isProbablyText(bytes: ByteArray): Boolean =
        bytes.none { it == 0.toByte() } ||
            decodeByBom(bytes) != null ||
            decodeUtf16WithoutBom(bytes) != null

    private fun decodeByBom(bytes: ByteArray): String? = when {
        bytes.startsWith(0xEF, 0xBB, 0xBF) ->
            bytes.copyOfRange(3, bytes.size).decodeToString()

        bytes.startsWith(0xFF, 0xFE) ->
            decodeWithCharset("UTF-16LE", bytes.copyOfRange(2, bytes.size))

        bytes.startsWith(0xFE, 0xFF) ->
            decodeWithCharset("UTF-16BE", bytes.copyOfRange(2, bytes.size))

        else -> null
    }

    /**
     * 无 BOM 的 UTF-16：ASCII 文本会呈"低位字节 0 / 高位字节 0"的交替形态，
     * 按哪一侧的 0 多来判断字节序。判完还要看控制字符占比，避免把二进制当文本。
     */
    private fun decodeUtf16WithoutBom(bytes: ByteArray): String? {
        val pairs = bytes.size / 2
        if (pairs < 4) return null

        var zerosAtOdd = 0
        var zerosAtEven = 0
        for (i in 0 until pairs * 2) {
            if (bytes[i] != 0.toByte()) continue
            if (i and 1 == 1) zerosAtOdd++ else zerosAtEven++
        }

        val charset = when {
            zerosAtOdd >= pairs * 0.8 -> "UTF-16LE"
            zerosAtEven >= pairs * 0.8 -> "UTF-16BE"
            else -> return null
        }

        val text = decodeWithCharset(charset, bytes)?.trimStart('\uFEFF') ?: return null
        return if (controlShare(text) <= MAX_CONTROL_SHARE) text else null
    }

    /** 逐字节升位映射：Latin-1 / Windows-1252 的近似，保证不丢字节。 */
    private fun decodeLatin1(bytes: ByteArray): String {
        val chars = CharArray(bytes.size)
        for (i in bytes.indices) {
            chars[i] = (bytes[i].toInt() and 0xFF).toChar()
        }
        return chars.concatToString()
    }

    private fun cjkShare(text: String): Double {
        if (text.isEmpty()) return 0.0
        var cjk = 0
        for (char in text) {
            if (char.isCjk()) cjk++
        }
        return cjk.toDouble() / text.length
    }

    private fun controlShare(text: String): Double {
        if (text.isEmpty()) return 0.0
        var control = 0
        for (char in text) {
            if (char.code < 0x20 && char != '\n' && char != '\r' && char != '\t') control++
        }
        return control.toDouble() / text.length
    }

    private fun Char.isCjk(): Boolean =
        code in 0x4E00..0x9FFF || code in 0x3400..0x4DBF || code in 0xF900..0xFAFF

    private fun ByteArray.startsWith(vararg prefix: Int): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) {
            if (this[i] != prefix[i].toByte()) return false
        }
        return true
    }

    private const val REPLACEMENT = '\uFFFD'
}
