package dev.hibernalglow.grzeb.core.text

/** Web 端没有字符集库，只保留 common 里的 UTF-8 与 Latin-1 两条路。 */
internal actual fun decodeWithCharset(charsetName: String, bytes: ByteArray): String? = null
