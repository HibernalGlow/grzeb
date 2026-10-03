# grzeb

<p align="center">
  <img src="./assets/readme/hero.svg" width="100%"
       alt="grzeb：选一个目录全文检索整棵目录树，结果按目录钻取，正文进本地索引库。右侧是结果树面板——面包屑、目录聚合计数、命中高亮的正文预览">
</p>

<p align="center">
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.4.10-7F52FF?style=flat-square" alt="Kotlin 2.4.10"></a>
  <a href="https://www.jetbrains.com/compose-multiplatform/"><img src="https://img.shields.io/badge/Compose_Multiplatform-1.11.1-4285F4?style=flat-square" alt="Compose Multiplatform 1.11.1"></a>
  <a href="https://sqldelight.github.io/sqldelight/"><img src="https://img.shields.io/badge/SQLDelight-2.4.0-E9C46A?style=flat-square" alt="SQLDelight 2.4.0"></a>
  <img src="https://img.shields.io/badge/version-0.1.0-D0BCFF?style=flat-square" alt="版本 0.1.0">
</p>

<p align="center">简体中文 · <a href="./README_EN.md">English</a></p>

选一个目录，把整棵目录树里的纯文本全量检索出来。**结果不是扁平列表，而是一棵能钻取的结果树**：
目录行显示整棵子树的文件数与命中数，点目录换 scope，点命中直接跳到正文那一行。
正文会缓进本地 SQLDelight 库，所以第二次搜同一批文件既不用重新读盘，也不用重新解码。

## 结果树长这样

<p align="center">
  <img src="./assets/readme/showcase-tree.svg" width="100%"
       alt="结果树面板放大图：四条标注分别说明面包屑逐级回退、目录行是子树合计、点目录换 scope、点命中跳到该行">
</p>

层级不是从结果路径里猜的，而是查索引库的 `parentUri` 链上溯出来的（`core/…/search/SearchTree.kt:56-111`）。
这样 Android 的 SAF `content://` URI 和桌面的绝对路径能走同一套代码；索引还没热完时，
位置未知的文件会平铺挂在当前 scope 下——**宁可铺得不漂亮，也不能把结果丢了**。

## 怎么跑的

<p align="center">
  <img src="./assets/readme/workflow.svg" width="100%"
       alt="四个阶段的流程图：IndexWarmer 增量预热、SQLDelight 索引库三张表、SearchEngine 并行检索、SearchTreeBuilder 构建结果树，下方说明为什么不用 FTS5、进度条为什么是不确定的">
</p>

- **索引库不用 FTS5**，是刻意的：检索要支持正则、全词匹配、大小写敏感，这些语义分词索引服务不了。
  真正的收益来自「正文进了库」——`content` 表存解码后的文本，二次检索直接把文本捞出来在内存里匹配，
  语义和现网完全一致（`core/…/db/Index.sq:1-11`）。
- **并行遍历**：子目录各起一条协程，同目录下的文件再切成几批并行，用 `Semaphore(concurrency)`
  限住同时在飞的 IO 数（默认 4）。Android 上每次 SAF 调用都是跨进程查询，瓶颈在延迟不在带宽，
  并发是净赚的（`core/…/search/SearchEngine.kt:62,167,196-235`）。代价是**结果顺序不确定**：谁先扫完谁先进列表。
- **读穿缓存**：`IndexedFileSystem` 先查快照，快照没热完就回落到真实文件系统；预热完成后
  每 1 秒复查一次时间戳，自动切到缓存（`core/…/index/IndexedFileSystem.kt:21-77,100`）。
- **增量预热**：按 `lastModified` 补差，磁盘上已经消失的条目从库里剔掉（`core/…/index/IndexWarmer.kt:87-88,189`）。
  选完目录自动开跑，不用点按钮。
- **记住上次目录**：App 重开时用 `root` 表里最近一条预热记录回填目录，省得每次都重选
  （`Index.sq:45-47`、`shared/…/SearchScreen.kt:86-90`）。

### 中文不乱码是硬要求

判定顺序写在 `core/…/text/TextDecoder.kt:6-20`：

```text
1. BOM：UTF-8 / UTF-16 LE / UTF-16 BE
2. 含 NUL 字节 → 呈「隔一个字节一个 NUL」的判为无 BOM UTF-16，否则判二进制
   （jpg / apk / zip 都在这一步被挡掉）
3. 按 UTF-8 解，没有替换字符就采用
4. 退 GB18030（GBK / GB2312 的超集）—— 中文小说绝大多数是它。两道门槛：
   解出来不能有替换字符，且汉字占比 ≥ 5%
5. 替换字符超过全文十分之一，才退到 Latin-1 逐字节映射；
   否则仍返回带替换字符的 UTF-8（也就是说：这一步之外，坏字节是真的会丢）
```

第 4 步的第一道门槛挡住 Latin-1 正文被误判成 GBK（法文重音字节后面常跟空格或标点，不是合法 GBK 尾字节），
第二道挡住「基本是 ASCII、只有零星高位字节」的文件。

## 快速开始

环境：JDK 17+（Gradle 9.6.1 由 wrapper 提供，JDK 得自己装）、Android 目标需要 compileSdk 37 的 Android SDK。
`settings.gradle.kts` 把阿里云镜像排在最前，国内网络直连 Maven Central 有时会卡在 `CLOSE_WAIT` 上。

```bash
./gradlew :desktopApp:run                              # 桌面端，直接起窗口
./gradlew :androidApp:installDebug                     # Android：连上设备后装调试包
./gradlew :webApp:wasmJsBrowserDevelopmentRun          # Web(wasmJs)：只有界面能跑，见下文
```

第一次搜就四步：

1. 点「点击选择搜索目录...」——桌面弹文件对话框（macOS 走原生 `FileDialog`，其他系统走 Swing
   `JFileChooser`，标题都是「选择搜索目录」），Android 走 SAF 授权一棵树；
2. 等「正在建索引...」的预热条走完（也可以不等，检索会自己回落到真实文件系统）；
3. 输入关键词，点「搜索」；
4. 在结果树里点目录钻取、点面包屑回退、点某处命中跳到正文对应行。

打包：`:desktopApp:packageDistributionForCurrentOS`（或 `:packageUberJarForCurrentOS`）、
`:webApp:wasmJsBrowserDistribution`。

## 三端各自的真实状态

<p align="center">
  <img src="./assets/readme/platform-matrix.svg" width="100%"
       alt="三端支持矩阵：桌面与 Android 标为可检索，Web 标为仅界面，逐项列出目录访问、检索、索引库落点与系统级动作">
</p>

| | 桌面 (jvm) | Android | Web (wasmJs) |
| --- | --- | --- | --- |
| 目录访问 | 文件对话框（macOS 原生 / 其他 Swing） | SAF tree uri | **未接入** |
| 索引库 | `~/.grzeb/index.db` | `grzeb-index.db` | 仅内存实现 |
| 真实检索 | 是 | 是 | 否，直接返回空结果 |
| 在文件管理器中定位 | 是（`open -R` / `explorer /select,` / `xdg-open`） | 系统无此概念 | 否 |

三端共用同一份 UI，平台差异全收在 `PlatformServices` 这一个接口里
（`shared/…/platform/PlatformServices.kt:14-43`，`expect fun rememberPlatformServices()` 配三个 actual）。
Web 端**能构建、能跑、界面完整**，但没有目录访问，`isSupported=false` 时检索短路返回空
（`shared/…/PlatformServices.wasmJs.kt:19-33`、`core/…/search/SearchEngine.kt:160`）——现在只能当界面演示。

布局按 M3 窗口尺寸分类分流：断点 600 / 840dp，**按内容宽度判定**。只有展开窗口才并排两栏
（中等窗口扣掉 400dp 侧栏后列表放不下），侧栏可拖、双击复位（`shared/…/ui/AdaptiveLayout.kt:24-60,77-99`），
宽度存进 `rememberSaveable`（`shared/…/SearchScreen.kt:107`）。桌面窗口默认 1120×780，正好落在展开档，一开就是两栏。

## 界面里的检索选项

「搜索选项」里只有四个开关（`shared/…/SearchScreen.kt:381-392`）：

| 开关 | 语义 |
| --- | --- |
| 区分大小写 | 默认不区分 |
| 正则表达式 | 整条 query 原样编译、**不切词**；编译失败提示「正则表达式无效」 |
| 全词匹配 | 只对**不含中日韩字符**的词生效——中文两侧永远构不成 `\b`，硬加会得到恒假的 pattern |
| 每文件首个 | 每个文件只留第一处命中 |

`SearchOptions` 里的 `isWildcard`（`*` 跨行、`?` 单个非换行）和 `isMultiKeyword`
（空白分词的无序 AND，支持 `"短语"` 与 `-排除词`）**在 core 里已经实现并有测试，但 UI 没有开关**，
`search()` 只传上面那四个开关（外加两个写死的常量）——所以现在用不到。同理，`maxDepth`、`ignoredDirs`、
`includeExtensions`、并发数都只有代码里的值，**没有设置界面可改**：界面把 `maxDepth` 写死成 **15**
（`shared/…/SearchUiState.kt:320`），core 的默认值才是 10。

## 项目结构

```text
core/       文件系统抽象 · 解码 · 索引库与预热 · 检索引擎 · 结果树构建器（不含任何 UI 依赖）
shared/     共用的 Compose UI + PlatformServices（expect / actual）
desktopApp/ 窗口入口        androidApp/ Activity 入口        webApp/ ComposeViewport 入口
```

依赖只有一条：`shared → core`，三个 app 模块都只依赖 `shared`。
DI 是手工的——`rememberPlatformServices()` 加 `remember {}`，没有 Koin / Hilt。

| 想改的东西 | 去哪儿 |
| --- | --- |
| 加一种扩展名 | `core/…/text/TextFileTypes.kt:9-23`（52 种纯文本 + 4 种电子书扩展名在册） |
| 改查询语法 | `core/…/search/QueryParser.kt` |
| 改索引表结构 | `core/…/sqldelight/…/Index.sq` |
| 加一个平台 | 实现 `PlatformServices` 的 actual，再实现 `GrzebFileSystem` |

## 开发

```bash
./gradlew :core:jvmTest        # 7 个 JVM 测试类：解析 / 引擎 / 建树 / 解码 / 索引三件套
./scripts/dev-desktop.sh       # 改 .kt / .kts / .sq 就自动重启桌面端，一轮约 10~30s
```

`dev-desktop.sh` 用 1 秒轮询而不是 `fswatch`：后者往管道里写事件时是 4KB 块缓冲的，
单条改动根本传不到 `while read`。它只结束可执行文件是 `java` 的匹配进程——按命令行匹配会误伤
任何恰好提到这两个字符串的 shell。

## 当前限制

不打算含糊过去的部分：

- **Web 端不能检索**，没有目录访问；只有界面是真的。
- **iOS 不是当前目标**：`core` 与 `shared` 都还没声明 Kotlin/Native 目标，也没有 `iosMain`。
- **不支持电子书正文**。`.epub` / `.mobi` / `.fb2` / `.rtf` 在扩展名白名单里，但解析模块还没接：
  epub 是 zip、mobi 是二进制，含 NUL 会在解码第 2 步被判成二进制直接跳过；
  fb2（XML）和 rtf 本身是文本，会被**原样检索**——命中的是标签和转义串，不是正文。别当成「支持 EPUB」。
- **统计式编码探测还没做**：无 BOM 且正文是汉字的 UTF-16 认不出来，会被当二进制跳过。
- **没有设置界面、没有手动重建索引的入口**（`IndexedFileSystem.invalidate` 无人调用，
  `IndexStore.clear` 只有测试在调），**也没有搜索历史界面**（`scopeHistory` 只在状态里维护，UI 从不显示它）。
- **进度条是不确定的**：core 只报「扫过了多少」，没有总数，所以不给百分比。
  react 版那个 50% → 100% 是写死的假值，这里没有照搬。
- 单文件读取上限 **8 MB**，每个文件最多记 **500 处**命中，界面按 **15 层**深度遍历（core 默认 10），
  跳过 `.` 开头的目录与 `node_modules` 等默认忽略项。
- **界面文案是硬编码的简体中文**，没有字符串资源，也没有多语言。
- 本分支还没有 `LICENSE` 文件。

## 来源

`compose` 分支是 Compose Multiplatform 的重写版，同一份产品的 React Native 实现留在 `react` 分支
（那边的 README 见 `origin/react:README.md`），更早的起点是一个 Tauri + React 的 Android 模板
（在 `origin/main:README.md`，本地没有 `main` 分支）。这一版把检索与索引逻辑收进了与平台无关的 `core`，
UI 只写一份。
