package dev.hibernalglow.grzeb.core.fs

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dev.hibernalglow.grzeb.core.text.TextDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Android 实现：基于 SAF（Storage Access Framework）。
 *
 * react 版把 `DocumentsContract` 的调用直接写死在搜索引擎里，导致整条检索链路
 * 无法离开 Android。这里把"列目录 / 读文本"收成一层，搜索引擎只认 [GrzebFileSystem]。
 *
 * `uri` 一律是 SAF 文档 URI（`content://…/tree/…/document/…`），需要 [treeUri]
 * 才能列子项——也就是必须先由用户在系统选择器里授权一个目录树。
 */
class AndroidGrzebFileSystem(
    private val context: Context,
    /** 已授权的目录树 URI（`content://…/tree/…`）；null 表示尚未授权。 */
    private val treeUri: String? = null,
) : GrzebFileSystem {

    private val resolver: ContentResolver get() = context.contentResolver

    /** 未授权前不具备能力，UI 据此提示"请先选择目录"。 */
    override val isSupported: Boolean get() = treeUri != null

    override suspend fun list(uri: String): List<FileEntry> = withContext(Dispatchers.IO) {
        val docUri = Uri.parse(uri)
        val docId = documentIdOf(docUri) ?: return@withContext emptyList()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(docUri, docId)

        val entries = mutableListOf<FileEntry>()
        runCatching {
            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val childId = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    val mime = cursor.getString(2)
                    val size = if (cursor.isNull(3)) null else cursor.getLong(3)

                    entries += FileEntry(
                        uri = DocumentsContract.buildDocumentUriUsingTree(docUri, childId).toString(),
                        name = name,
                        isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                        size = size,
                    )
                }
            }
        }

        entries.sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    override suspend fun readText(uri: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            resolver.openInputStream(Uri.parse(uri))?.use { stream ->
                TextDecoder.decode(stream.readAtMost(MAX_TEXT_BYTES))
            }
        }.getOrNull()
    }

    /**
     * 展示名：优先向 provider 查 DISPLAY_NAME，失败则退回 URI 末段
     * （SAF 的 document id 里末段通常就是文件名）。
     */
    override fun displayName(uri: String): String {
        val docUri = Uri.parse(uri)
        runCatching {
            resolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getString(0)
            }
        }
        return runCatching { DocumentsContract.getDocumentId(docUri).substringAfterLast('/') }
            .getOrDefault(uri)
    }

    override fun rootLocation(): String? = treeUri

    /** 返回一个绑定了新目录树的实例（授权后调用）。 */
    fun withTreeUri(newTreeUri: String?): AndroidGrzebFileSystem =
        AndroidGrzebFileSystem(context, newTreeUri)

    private fun documentIdOf(docUri: Uri): String? = runCatching {
        if (DocumentsContract.isTreeUri(docUri)) {
            DocumentsContract.getTreeDocumentId(docUri)
        } else {
            DocumentsContract.getDocumentId(docUri)
        }
    }.getOrNull()

    private companion object {
        /** 单文件读取上限 8 MB，与桌面端一致。 */
        const val MAX_TEXT_BYTES = 8L * 1024 * 1024
    }
}

/**
 * 限量读取：Android 上没有 `InputStream.readNBytes`（API 33+ 才有），
 * 自己按缓冲循环读，超过上限即停——既防 OOM，也不至于因为一个巨文件卡死检索。
 */
private fun InputStream.readAtMost(limit: Long): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (total < limit) {
        val want = minOf(buffer.size.toLong(), limit - total).toInt()
        val read = read(buffer, 0, want)
        if (read <= 0) break
        out.write(buffer, 0, read)
        total += read
    }
    return out.toByteArray()
}
