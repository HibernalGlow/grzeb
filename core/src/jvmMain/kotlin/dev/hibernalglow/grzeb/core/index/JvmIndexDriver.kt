package dev.hibernalglow.grzeb.core.index

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

/**
 * 桌面端的索引库 driver：落在 [path] 指定的文件上（默认 `~/.grzeb/index.db`）。
 *
 * 空文件要当成"还没建表"处理：JdbcSqliteDriver 打开一个 0 字节文件不会报错，
 * 但后续查询会以 "no such table" 失败，所以这里按长度判断而不是只看 exists。
 */
fun createJvmIndexDriver(path: String): SqlDriver {
    val file = File(path)
    val isFresh = !file.exists() || file.length() == 0L
    file.parentFile?.mkdirs()

    val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
    if (isFresh) SqlDelightIndexStore.ensureSchema(driver)
    return driver
}

/** 默认索引库路径。 */
fun defaultJvmIndexPath(): String =
    File(System.getProperty("user.home") ?: ".", ".grzeb/index.db").absolutePath
