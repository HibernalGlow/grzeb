package dev.hibernalglow.grzeb.core.index

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import dev.hibernalglow.grzeb.core.index.db.GrzebIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SQLDelight 落盘实现（Android / 桌面）。
 *
 * 所有查询都切到 [Dispatchers.Default]：SQLDelight 生成的是阻塞调用，
 * 而这些方法会被 UI 层的协程直接调用，留在主线程上就是掉帧。
 * （commonMain 里拿不到 [Dispatchers.IO]，Default 对数据库这种短时阻塞足够用。）
 */
class SqlDelightIndexStore(private val database: GrzebIndex) : IndexStore {

    private val queries = database.indexQueries

    override suspend fun status(treeUri: String): IndexStatus = db {
        IndexStatus(
            warmedAt = queries.warmedAt(treeUri).executeAsOneOrNull(),
            fileCount = queries.countFiles(treeUri).executeAsOne().toInt(),
            textCount = queries.countTexts(treeUri).executeAsOne().toInt(),
        )
    }

    override suspend fun entries(treeUri: String): List<IndexedEntry> = db {
        queries.entriesOf(treeUri).executeAsList().map { row ->
            IndexedEntry(
                uri = row.uri,
                parentUri = row.parent_uri,
                name = row.name,
                isDirectory = row.is_dir != 0L,
                size = row.size,
                lastModified = row.last_modified,
            )
        }
    }

    override suspend fun putEntries(treeUri: String, entries: List<IndexedEntry>) = db {
        queries.transaction {
            for (entry in entries) {
                queries.upsertEntry(
                    tree_uri = treeUri,
                    uri = entry.uri,
                    parent_uri = entry.parentUri,
                    name = entry.name,
                    is_dir = if (entry.isDirectory) 1L else 0L,
                    size = entry.size,
                    last_modified = entry.lastModified,
                )
            }
        }
    }

    /**
     * 逐条删而不是 `IN (…)`：SQLite 的绑定参数个数有上限（老版本 999），
     * 一次刷新可能要清掉上万个失效条目，用 IN 反而要自己切块。
     * 整批放在一个事务里，逐条删一样快。
     */
    override suspend fun removeEntries(treeUri: String, uris: Collection<String>) = db {
        if (uris.isEmpty()) return@db
        queries.transaction {
            for (uri in uris) {
                queries.deleteEntry(treeUri, uri)
                queries.dropText(treeUri, uri)
            }
        }
    }

    override suspend fun text(treeUri: String, uri: String): CachedText? = db {
        queries.textOf(treeUri, uri).executeAsOneOrNull()?.let {
            CachedText(text = it.text, lastModified = it.last_modified)
        }
    }

    override suspend fun putText(
        treeUri: String,
        uri: String,
        lastModified: Long?,
        text: String,
    ): Unit = db {
        queries.putText(tree_uri = treeUri, uri = uri, last_modified = lastModified, text = text)
    }

    override suspend fun dropText(treeUri: String, uri: String): Unit = db {
        queries.dropText(treeUri, uri)
    }

    override suspend fun markWarmed(treeUri: String, displayName: String?, at: Long): Unit = db {
        queries.markWarmed(tree_uri = treeUri, display_name = displayName, warmed_at = at)
    }

    override suspend fun clear(treeUri: String): Unit = db {
        queries.transaction {
            queries.clearContent(treeUri)
            queries.clearEntries(treeUri)
            queries.clearRootRow(treeUri)
        }
    }

    private suspend fun <T> db(block: () -> T): T = withContext(Dispatchers.Default) { block() }

    companion object {
        /** 打开数据库；库不存在时建表。driver 由各平台提供。 */
        fun create(driver: SqlDriver): SqlDelightIndexStore {
            ensureSchema(driver)
            return SqlDelightIndexStore(GrzebIndex(driver))
        }

        /** 建表。已经在用的库不能重复 create —— 表已存在会直接报错。 */
        internal fun ensureSchema(driver: SqlDriver) {
            val hasTables = driver.executeQuery(
                identifier = null,
                sql = "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = 'entry'",
                mapper = { cursor ->
                    // 与生成的查询同构：同步 driver 下 next() 的返回值可以忽略
                    cursor.next()
                    QueryResult.Value(cursor.getLong(0) ?: 0L)
                },
                parameters = 0,
            ).value > 0L

            if (!hasTables) GrzebIndex.Schema.create(driver)
        }
    }
}
