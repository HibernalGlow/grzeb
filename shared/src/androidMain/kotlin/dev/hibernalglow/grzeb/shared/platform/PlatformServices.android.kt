package dev.hibernalglow.grzeb.shared.platform

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.hibernalglow.grzeb.core.fs.AndroidGrzebFileSystem
import dev.hibernalglow.grzeb.core.fs.GrzebFileSystem
import dev.hibernalglow.grzeb.core.index.IndexStore
import dev.hibernalglow.grzeb.core.index.SqlDelightIndexStore
import dev.hibernalglow.grzeb.core.index.createAndroidIndexDriver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android 侧：SAF 目录选择 + content uri 的外部打开。
 *
 * OpenDocumentTree 给的授权默认只活到进程结束，所以回调里立刻
 * takePersistableUriPermission —— 否则重启后列表就再也读不到了。
 */
@Composable
actual fun rememberPlatformServices(): PlatformServices {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<CompletableDeferred<String?>?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        pending?.complete(uri?.toString())
        pending = null
    }

    return remember(context, launcher) {
        object : PlatformServices {

            override val isDirectoryPickerSupported: Boolean = true

            override suspend fun pickDirectory(): String? {
                // 上一次若还挂着，先放掉，免得那个 await 永远不返回
                pending?.complete(null)
                val deferred = CompletableDeferred<String?>()
                pending = deferred
                launcher.launch(null)
                return deferred.await()
            }

            override fun createFileSystem(treeUri: String?): GrzebFileSystem =
                AndroidGrzebFileSystem(context, treeUri)

            /** 惰性开库：不预热就永远不建这个文件。 */
            private val index by lazy { SqlDelightIndexStore.create(createAndroidIndexDriver(context)) }

            override fun createIndexStore(): IndexStore = index

            override suspend fun openExternally(uri: String): Boolean = withContext(Dispatchers.IO) {
                runCatching {
                    val parsed = Uri.parse(uri)
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(parsed, context.contentResolver.getType(parsed) ?: "*/*")
                            addFlags(
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK,
                            )
                        },
                    )
                    true
                }.getOrDefault(false)
            }

            /**
             * SAF 没有"在文件管理器里选中某个文件"的意图：要自己从 document uri 反推父目录
             * （`DocumentsContract.findDocumentPath` 还要 API 26+），跨 provider 不保证可用，
             * 所以这里先声明不支持，UI 会直接不给这个入口。
             */
            override val isRevealSupported: Boolean = false

            override suspend fun revealInFileManager(uri: String): Boolean = false
        }
    }
}
