package dev.hibernalglow.grzeb.shared.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.hibernalglow.grzeb.core.fs.GrzebFileSystem
import dev.hibernalglow.grzeb.core.search.FileSearchResult
import dev.hibernalglow.grzeb.core.search.SearchCallbacks
import dev.hibernalglow.grzeb.core.search.SearchEngine
import dev.hibernalglow.grzeb.core.search.SearchOptions
import dev.hibernalglow.grzeb.core.search.SearchProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 检索界面的状态。
 *
 * 对应 react 版 `app/search.tsx` 里的那堆 useState；检索本身留给 core 的
 * [SearchEngine]，这里只负责「开始 / 取消 / 清空」和结果累积。
 */
class SearchUiState {

    var query by mutableStateOf("")
    var directory by mutableStateOf<String?>(null)
    var isCaseSensitive by mutableStateOf(false)
    var isRegex by mutableStateOf(false)
    var isWholeWord by mutableStateOf(false)
    var isOnlyFirstMatch by mutableStateOf(false)
    var showOptions by mutableStateOf(false)

    /** 累积结果。用 SnapshotStateList 而不是 `list + item`，否则大目录下会退化成 O(n²)。 */
    val results = mutableStateListOf<FileSearchResult>()

    var progress by mutableStateOf<SearchProgress?>(null)
    var isSearching by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    /** 是否跑过至少一轮检索：空列表时据此区分"还没搜"和"搜了没结果"。 */
    var hasSearched by mutableStateOf(false)
        private set

    private var job: Job? = null

    /** 每轮检索一个令牌：旧轮次的回调 / finally 不再动新轮次的状态。 */
    private var runToken = 0

    val totalMatches: Int get() = results.sumOf { it.matchCount }

    val canSearch: Boolean get() = directory != null && query.isNotBlank() && !isSearching

    fun search(scope: CoroutineScope, fileSystem: GrzebFileSystem) {
        val root = directory ?: return
        val text = query.trim()
        if (text.isEmpty()) return

        val options = SearchOptions(
            query = text,
            isRegex = isRegex,
            isCaseSensitive = isCaseSensitive,
            isWholeWord = isWholeWord,
            isOnlyFirstMatch = isOnlyFirstMatch,
            maxDepth = MAX_DEPTH,
            contextLines = CONTEXT_LINES,
        )
        // react 版没有这一步：正则写错了只会静默返回空结果，这里直接告诉用户
        if (SearchEngine.buildRegex(options) == null) {
            error = "正则表达式无效"
            return
        }

        job?.cancel()
        val token = ++runToken
        results.clear()
        progress = SearchProgress()
        error = null
        hasSearched = true
        isSearching = true

        val engine = SearchEngine(
            fileSystem = fileSystem,
            options = options,
            callbacks = SearchCallbacks(
                onProgress = { if (token == runToken) progress = it },
                onResult = { if (token == runToken) results += it },
            ),
        )

        job = scope.launch {
            try {
                engine.search(root)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                if (token == runToken) error = failure.message ?: "检索失败"
            } finally {
                if (token == runToken) {
                    isSearching = false
                    progress = null
                }
            }
        }
    }

    fun cancel() {
        runToken++
        job?.cancel()
        job = null
        isSearching = false
        progress = null
    }

    fun clearResults() {
        results.clear()
        progress = null
        error = null
    }

    fun toggleOption(option: Option) {
        when (option) {
            Option.CaseSensitive -> isCaseSensitive = !isCaseSensitive
            Option.Regex -> isRegex = !isRegex
            Option.WholeWord -> isWholeWord = !isWholeWord
            Option.OnlyFirstMatch -> isOnlyFirstMatch = !isOnlyFirstMatch
        }
    }

    enum class Option { CaseSensitive, Regex, WholeWord, OnlyFirstMatch }

    private companion object {
        /** react 版检索页用的是 15 层、2 行上下文。 */
        const val MAX_DEPTH = 15
        const val CONTEXT_LINES = 2
    }
}
