package dev.hibernalglow.grzeb.core.text

import java.nio.charset.Charset

internal actual fun decodeWithCharset(charsetName: String, bytes: ByteArray): String? =
    runCatching { String(bytes, Charset.forName(charsetName)) }.getOrNull()
