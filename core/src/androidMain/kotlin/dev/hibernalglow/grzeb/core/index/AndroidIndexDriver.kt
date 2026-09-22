package dev.hibernalglow.grzeb.core.index

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.hibernalglow.grzeb.core.index.db.GrzebIndex

/**
 * Android 端的索引库 driver。
 *
 * schema 交给 [AndroidSqliteDriver] 自己管：库文件在应用私有目录里，卸载即清空，
 * 不需要（也不该）跨版本迁移用户数据。
 */
fun createAndroidIndexDriver(context: Context, name: String = "grzeb-index.db"): SqlDriver =
    AndroidSqliteDriver(GrzebIndex.Schema, context.applicationContext, name)
