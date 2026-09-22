package dev.hibernalglow.grzeb.core.text

/**
 * 平台字符集解码。
 *
 * Android / JVM 走 `java.nio.charset`；Web 没有字符集库，返回 null，
 * 调用方退回 Latin-1 逐字节映射（至少不丢字节）。
 */
internal expect fun decodeWithCharset(charsetName: String, bytes: ByteArray): String?
