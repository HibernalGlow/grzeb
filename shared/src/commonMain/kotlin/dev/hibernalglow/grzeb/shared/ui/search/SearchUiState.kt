package dev.hibernalglow.grzeb.shared.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.hibernalglow.grzeb.core.fs.GrzebFileSystem
import dev.hibernalglow.grzeb.core.index.IndexStore
import dev.hibernalglow.grzeb.core.index.IndexStatus
import dev.hibernalglow.grzeb.core.index.IndexWarmer
import dev.hibernalglow.grzeb.core.index.WarmProgress
import dev.hibernalglow.grzeb.core.search.FileSearchResult
import dev.hibernalglow.grzeb.core.search.ScopePath
import dev.hibernalglow.grzeb.core.search.SearchCallbacks
import dev.hibernalglow.grzeb.core.search.SearchEngine
import dev.hibernalglow.grzeb.core.search.SearchOptions
import dev.hibernalglow.grzeb.core.search.SearchProgress
import dev.hibernalglow.grzeb.core.search.SearchTreeBuilder
import dev.hibernalglow.grzeb.core.search.SearchTreeNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

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

    /**
     * 预览选中项。
     *
     * 宽窗口下它在右侧栏显示，紧凑 / 中等窗口下整屏显示（分流见 SearchScreen）。
     */
    var preview by mutableStateOf<PreviewSelection?>(null)

    /** 是否跑过至少一轮检索：空列表时据此区分"还没搜"和"搜了没结果"。 */
    var hasSearched by mutableStateOf(false)
        private set

    private var job: Job? = null

    /** 每轮检索一个令牌：旧轮次的回调 / finally 不再动新轮次的状态。 */
    private var runToken = 0

    val totalMatches: Int get() = results.sumOf { it.matchCount }

    val canSearch: Boolean get() = directory != null && query.isNotBlank() && !isSearching

    fun search(scope: CoroutineScope, fileSystem: GrzebFileSystem, store: IndexStore) {
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
        preview = null
        progress = SearchProgress()
        error = null
        hasSearched = true
        isSearching = true
        // 新的一轮检索回到整棵树的根；旧的折叠状态也跟着作废
        currentScope = null
        collapsedFolders = emptySet()
        collapsedFiles = emptySet()
        tree = null

        val engine = SearchEngine(
            fileSystem = fileSystem,
            options = options,
            callbacks = SearchCallbacks(
                onProgress = { if (token == runToken) progress = it },
                onResult = {
                    if (token == runToken) {
                        results += it
                        // 结果一条条到，节流重建（树要能看着长出来，但不能每条都重建）
                        rebuildTree(scope, store)
                    }
                },
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
                    rebuildTree(scope, store, force = true)
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
        preview = null
        progress = null
        error = null
        tree = null
        currentScope = null
        scopePath = emptyList()
        collapsedFolders = emptySet()
        collapsedFiles = emptySet()
    }

    // ── 索引预热 ────────────────────────────────────────────────────────────
    // 加速靠 core 的 IndexWarmer：目录项与正文先入库，之后的检索直接打索引。
    // 触发点由 UI 决定（选中目录后调 warm），这里只管状态与生命周期。

    /** 预热进度；不在预热时为 null。 */
    var warmProgress by mutableStateOf<WarmProgress?>(null)
        private set

    /** 当前目录的索引状态（已入库的文件数 / 正文数）。 */
    var indexStatus by mutableStateOf<IndexStatus?>(null)
        private set

    private var warmJob: Job? = null
    private var warmToken = 0

    /**
     * 后台预热 [directory]。可重复调用（换目录、手动刷新）：新一轮会取消上一轮。
     *
     * [fileSystem] 必须传**还没套索引的那个** —— 预热本身要读真实文件系统，
     * 而套了索引的实例在预热完成前只会返回"库里现有的"，等于什么都扫不到。
     */
    fun warm(scope: CoroutineScope, store: IndexStore, fileSystem: GrzebFileSystem) {
        val root = directory ?: return
        warmJob?.cancel()
        val token = ++warmToken
        warmProgress = WarmProgress()

        warmJob = scope.launch {
            try {
                val warmer = IndexWarmer(
                    fileSystem = fileSystem,
                    store = store,
                    options = SearchOptions(maxDepth = MAX_DEPTH, contextLines = CONTEXT_LINES),
                    onProgress = { if (token == warmToken) warmProgress = it },
                )
                indexStatus = warmer.warm(root)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                if (token == warmToken) error = "预热失败：${failure.message ?: "未知错误"}"
            } finally {
                if (token == warmToken) warmProgress = null
            }
        }
    }

    // ── 结果树 / 钻取 scope ─────────────────────────────────────────────────
    // 检索结果是"可进入的层级"：目录节点带整个子树的计数，点目录名就钻进这个范围
    // （把该目录当成根的同一份结果子集），面包屑一路可退。树的层级来自索引库，
    // 所以钻取本身不产生任何文件系统 IO。

    /** 当前 scope 的目录 uri；null 表示整棵树的根（= [directory]）。 */
    var currentScope by mutableStateOf<String?>(null)
        private set

    /** 整棵树的根到当前 scope 的链路；根在最前，面包屑直接用。 */
    var scopePath by mutableStateOf<List<ScopePath>>(emptyList())
        private set

    /** 当前 scope 下的结果树；没结果时为 null。 */
    var tree by mutableStateOf<SearchTreeNode?>(null)
        private set

    /**
     * 被折叠起来的目录 uri。
     *
     * 存"折叠的"而不是"展开的"，这样空集就等于**默认全展开** ——
     * 树一出来就是完整层级，收起某一层才需要记一笔。
     */
    var collapsedFolders by mutableStateOf<Set<String>>(emptySet())
        private set

    /**
     * 被折叠的文件节点 uri。
     *
     * 与目录同一套口径：存"折叠的"，空集就等于**默认全展开** —— 一轮检索下来直接能看到
     * 各处理中，不用逐个点。单个文件的命中数由 [DEFAULT_MAX_MATCHES_PER_FILE] 兜着，
     * 所以铺开也不会淹掉整屏。
     */
    var collapsedFiles by mutableStateOf<Set<String>>(emptySet())
        private set

    /** 钻取历史（最近的在前），供"最近的范围"快速切回。只活在本次会话里。 */
    val scopeHistory = mutableStateListOf<ScopePath>()

    private var lastTreeBuild: TimeSource.Monotonic.ValueTimeMark? = null
    private var treeBuildRunning = false

    /** 钻进一个目录：scope 变成它，树按新范围重建。 */
    fun drillInto(uri: String, scope: CoroutineScope, store: IndexStore) {
        if (currentScope == uri) return
        currentScope = uri
        // 换了范围，折叠状态留着没意义（uri 是不同层的）
        collapsedFolders = emptySet()
        collapsedFiles = emptySet()
        rebuildTree(scope, store, force = true)
    }

    /** 退回上一层（面包屑/返回键用）。 */
    fun scopeUp(scope: CoroutineScope, store: IndexStore) {
        val parent = scopePath.dropLast(1).lastOrNull() ?: return
        drillInto(parent.uri, scope, store)
    }

    fun toggleFolder(uri: String) {
        collapsedFolders = if (uri in collapsedFolders) collapsedFolders - uri else collapsedFolders + uri
    }

    fun toggleFile(uri: String) {
        collapsedFiles = if (uri in collapsedFiles) collapsedFiles - uri else collapsedFiles + uri
    }

    /**
     * 按当前 [results] 与 [scope] 重建结果树与面包屑。
     *
     * [force] 为 false 时会做时间节流：检索过程中结果是一条条到的，
     * 每来一条就重建一次树会让大目录退化成 O(n²)。
     */
    @OptIn(ExperimentalTime::class)
    fun rebuildTree(scope: CoroutineScope, store: IndexStore, force: Boolean = false) {
        val root = directory
        if (root == null || treeBuildRunning) return
        val mark = TimeSource.Monotonic.markNow()
        if (!force && lastTreeBuild?.let { mark - it < TREE_REBUILD_INTERVAL } == true) return

        treeBuildRunning = true
        lastTreeBuild = mark
        val scoped = currentScope ?: root
        val snapshot = results.toList()

        scope.launch {
            try {
                val builder = SearchTreeBuilder(store, root)
                scopePath = builder.scopePath(scoped)
                // 根节点：全折叠时不显示它自己（它就是这个范围），只要它的 children
                tree = builder.build(snapshot, scoped)
                rememberScope(scopePath.lastOrNull())
            } finally {
                treeBuildRunning = false
            }
        }
    }

    private fun rememberScope(segment: ScopePath?) {
        if (segment == null) return
        scopeHistory.remove(segment)
        scopeHistory.add(0, segment)
        while (scopeHistory.size > MAX_SCOPE_HISTORY) scopeHistory.removeAt(scopeHistory.lastIndex)
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

        /** 检索过程中重建结果树的最小间隔，防止每来一条结果就重建一次。 */
        val TREE_REBUILD_INTERVAL = 400.milliseconds

        const val MAX_SCOPE_HISTORY = 20

        /** 只用于文档说明：单文件命中数上限在 core 的 SearchEngine 里。 */
        const val DEFAULT_MAX_MATCHES_PER_FILE = 500
    }
}
