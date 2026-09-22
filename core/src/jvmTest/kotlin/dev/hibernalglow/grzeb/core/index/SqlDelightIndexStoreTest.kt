package dev.hibernalglow.grzeb.core.index

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SqlDelightIndexStoreTest {

    private fun newStore(): IndexStore =
        SqlDelightIndexStore.create(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY))

    private val root = "content://tree/primary%3A1NOVEL"

    private fun entry(uri: String, name: String, isDir: Boolean, modified: Long?) = IndexedEntry(
        uri = uri,
        parentUri = root,
        name = name,
        isDirectory = isDir,
        size = if (isDir) null else 128L,
        lastModified = modified,
    )

    @Test
    fun roundTripsEntriesAndText() = runBlocking {
        val store = newStore()
        store.putEntries(
            root,
            listOf(
                entry("$root/sub", "sub", isDir = true, modified = 1_000L),
                entry("$root/a.txt", "a.txt", isDir = false, modified = 2_000L),
            ),
        )

        val entries = store.entries(root)
        assertEquals(2, entries.size)
        val file = entries.first { !it.isDirectory }
        assertEquals("a.txt", file.name)
        assertEquals(128L, file.size)
        assertEquals(2_000L, file.lastModified)

        store.putText(root, file.uri, 2_000L, "第一段正文")
        val cached = store.text(root, file.uri)
        assertNotNull(cached)
        assertEquals("第一段正文", cached.text)
        assertEquals(2_000L, cached.lastModified)
    }

    @Test
    fun putEntriesOverwritesSameUri() = runBlocking {
        val store = newStore()
        store.putEntries(root, listOf(entry("$root/a.txt", "a.txt", isDir = false, modified = 1L)))
        store.putEntries(root, listOf(entry("$root/a.txt", "改名.txt", isDir = false, modified = 2L)))

        val entries = store.entries(root)
        assertEquals(1, entries.size)
        assertEquals("改名.txt", entries.single().name)
        assertEquals(2L, entries.single().lastModified)
    }

    @Test
    fun removeEntriesAlsoDropsText() = runBlocking {
        val store = newStore()
        store.putEntries(root, listOf(entry("$root/a.txt", "a.txt", isDir = false, modified = 1L)))
        store.putText(root, "$root/a.txt", 1L, "正文")

        store.removeEntries(root, listOf("$root/a.txt"))

        assertEquals(0, store.entries(root).size)
        assertNull(store.text(root, "$root/a.txt"))
    }

    @Test
    fun statusCountsFilesSeparatelyFromTexts() = runBlocking {
        val store = newStore()
        store.putEntries(
            root,
            listOf(
                entry("$root/sub", "sub", isDir = true, modified = 1L),
                entry("$root/a.txt", "a.txt", isDir = false, modified = 1L),
                entry("$root/b.bin", "b.bin", isDir = false, modified = 1L),
            ),
        )
        store.putText(root, "$root/a.txt", 1L, "正文")

        val status = store.status(root)
        assertEquals(2, status.fileCount)
        assertEquals(1, status.textCount)
        assertNull(status.warmedAt)

        store.markWarmed(root, "刘备", 9_999L)
        val warmed = store.status(root)
        assertEquals(9_999L, warmed.warmedAt)
        assertEquals(true, warmed.isWarmed)
    }

    @Test
    fun clearRemovesEverything() = runBlocking {
        val store = newStore()
        store.putEntries(root, listOf(entry("$root/a.txt", "a.txt", isDir = false, modified = 1L)))
        store.putText(root, "$root/a.txt", 1L, "正文")
        store.markWarmed(root, "刘备", 5L)

        store.clear(root)

        val status = store.status(root)
        assertEquals(0, status.fileCount)
        assertEquals(0, status.textCount)
        assertNull(status.warmedAt)
        assertEquals(0, store.entries(root).size)
    }

    @Test
    fun missingTextIsNull() = runBlocking {
        assertNull(newStore().text(root, "$root/nope.txt"))
    }
}
