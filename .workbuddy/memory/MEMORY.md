# grzeb 项目长期笔记

## 架构决策

- **不引入 FTS5 / 倒排索引引擎；检索保持"自建扫描 + 正文入 SQLite 缓存"**
  （2026-09-22 实测确认，过程见 `.workbuddy/memory/2026-09-22.md`）。理由：
  1. 需求核心是**中文子串**（`卡号` ∈ `银行卡号`）。而 FTS5 默认分词器 unicode61 把
     **整段连续 CJK 当成 1 个 token**（中文查询恒 0 命中）；唯一能做子串的 trigram 有
     **3 字门槛**，2 字查询零收益且比全表扫更慢。中文里 2 字查询正是最高频（卡号/失败/密码/张三）。
  2. `core` 必须 `commonMain` 三端通用（jvm / wasmJs / android），JVM-only 的 Lucene 出局。
  3. 收益（避开 SAF 重读/重解码）已由 `content` 表拿到，索引的边际收益只剩 CPU；
     而实测全表扫 6.0M 字符 ≈ 40ms。
- 若日后真需要索引：形态选**自建 n-gram 倒排表当预筛 + 扫描兜底**，不要 FTS5。
  触发条件 = 实测证明"二次检索 CPU"才是瓶颈。
- **真正该"用现成的"是商品化部分**：编码探测（juniversalchardet / ICU4J）、
  文件名模糊的打分算法、正则（平台自带的 `java.util.regex` / JS `RegExp`）。

## 并发写入方（重要）

本仓库长期有多个会话同时改，已实测被并发修改的路径：
- `shared/src/commonMain/.../ui/search/{SearchResults,SearchScreen,SearchUiState}.kt`、`FilePreview.kt`、`AdaptiveLayout.kt`
- **`core/src/commonMain/.../search/SearchEngine.kt`**（实测：我正要修的编译错已被对方改掉）

⇒ 动手前先 Read 盘上最新内容（别信几分钟前的快照）；core 改动保持**纯增量 + 带默认值**
（`SearchOptions` 全命名参数构造 ⇒ 加字段必须给默认值）。

## 环境与命令

- `core` targets：`jvm()` / `wasmJs { browser() }` / `android()`（**无 iOS**，见 build.gradle.kts 注释）。
- 跑 core 测试（**不依赖 `:shared`**，UI 未对齐时也能验）：
  ```
  env -u HTTP_PROXY -u HTTPS_PROXY -u http_proxy -u https_proxy ./gradlew :core:jvmTest
  ```
  实测约 50s；结果在 `core/build/test-results/jvmTest/*.xml`。
- `SearchEngine.buildRegex(options)` 是**给 UI 做表达式校验的公开 API，不要重构掉**
  （`SearchUiState` 依赖 `== null` 这个语义）。
- `Index.sq`（sqldelight）目前**只有 schema、零接线**：没有 `DriverFactory`，
  `libs.versions.toml` 里也没有 wasmJs 的 SQLite driver。
