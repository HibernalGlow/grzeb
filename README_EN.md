# grzeb

<p align="center">
  <img src="./assets/readme/hero.svg" width="100%"
       alt="grzeb: Full-text search across local directories, drill-down result tree, and SQLDelight local index store.">
</p>

<p align="center">
  <a href="https://github.com/HibernalGlow/grzeb/releases/latest"><img src="https://img.shields.io/github/v/release/HibernalGlow/grzeb?style=flat-square&color=8A2BE2" alt="Latest Release"></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.4.10-7F52FF?style=flat-square" alt="Kotlin 2.4.10"></a>
  <a href="https://www.jetbrains.com/compose-multiplatform/"><img src="https://img.shields.io/badge/Compose_Multiplatform-1.11.1-4285F4?style=flat-square" alt="Compose Multiplatform 1.11.1"></a>
  <a href="https://sqldelight.github.io/sqldelight/"><img src="https://img.shields.io/badge/SQLDelight-2.4.0-E9C46A?style=flat-square" alt="SQLDelight 2.4.0"></a>
  <a href="./LICENSE"><img src="https://img.shields.io/badge/license-MIT-green?style=flat-square" alt="MIT License"></a>
</p>

<p align="center"><a href="./README.md">简体中文</a> · English</p>

**grzeb** is a high-performance cross-platform local code and text search utility built with **Compose Multiplatform**, targeting **Desktop (macOS / Windows / Linux)** and **Android**.

Designed with **Material 3 Expressive (M3E)** guidelines and offering a **VSCode-style** multi-workspace hierarchical search experience, grzeb replaces flat lists with collapsible directory trees, multi-folder concurrent search, incremental warm-up, and rich search history.

---

### 📥 Quick Downloads

Latest Release: **[v0.2.1](https://github.com/HibernalGlow/grzeb/releases/tag/v0.2.1)**

| Platform | Installer / Package | Details |
| :--- | :--- | :--- |
| 🍎 **macOS** | [`grzeb-0.2.1-macos.dmg`](https://github.com/HibernalGlow/grzeb/releases/download/v0.2.1/grzeb-0.2.1-macos.dmg) | Native DMG image (run `xattr -cr` or allow via *System Settings → Privacy & Security*) |
| 🪟 **Windows** | [`grzeb-0.2.1-windows.msi`](https://github.com/HibernalGlow/grzeb/releases/download/v0.2.1/grzeb-0.2.1-windows.msi) | Native MSI Windows Installer |
| 🐧 **Linux** | [`grzeb-0.2.1-linux.deb`](https://github.com/HibernalGlow/grzeb/releases/download/v0.2.1/grzeb-0.2.1-linux.deb) | Debian / Ubuntu Package |
| 📱 **Android** | [`grzeb-0.2.1-android-debug.apk`](https://github.com/HibernalGlow/grzeb/releases/download/v0.2.1/grzeb-0.2.1-android-debug.apk) / [`*-release.apk`](https://github.com/HibernalGlow/grzeb/releases/download/v0.2.1/grzeb-0.2.1-android-release.apk) | Direct install for Android 7.0+ (API 24~36, signed) |

---

## 🌟 Key Features

### 1. VSCode-Style Hierarchical Search Tree & Multi-Workspace Support
- **Multi-Workspace Search**: Search multiple directories in parallel, with separate root nodes for each workspace.
- **True Relative Paths**: Folder hierarchy reconstructed cleanly from relative paths across both desktop filesystems and Android SAF URIs.
- **Batch & In-Place Folding**: Expand/collapse folders in place with one-click "Expand All" and "Collapse All" actions. Each file item displays its relative folder and a match count badge.

<p align="center">
  <img src="./assets/readme/screenshot_vscode_search_tree.png" width="92%"
       alt="VSCode-style search tree: multi-workspace support, expand/collapse all, highlighted previews">
</p>

### 2. Material 3 Expressive (M3E) Design Language
- **Rhythm Visual Aesthetics**: 36dp primaryContainer brand badge, bottom-docked search bar, expressive spring shapes, and cursor feedback.
- **Pill Filters**: Quick toggle chips for case sensitivity, regular expressions, whole word matching, and warm-only search.
- **Expressive Empty State**: Category format matrix (Code, Web, Text, Config, Docs) alongside recent search history with quick-refill and single/batch deletion.

<p align="center">
  <img src="./assets/readme/screenshot_history_and_filters.png" width="92%"
       alt="Material 3 Expressive search home: bottom search bar, search history chips, format matrix">
</p>

### 3. Rhythm-Style Grouped Settings Card
- **Structured Categories**: Four distinct sections for Workspaces, Index Prewarming, Formats & Extensions, and Search Preferences.
- **Prewarming Switch & Wavy Progress Indicator**: Switch prewarming on or off on demand; visual feedback provided by `LinearWavyProgressIndicator`.
- **Custom Extension Management**: View 50+ built-in supported extensions and easily register or remove custom extensions (e.g., `.custom`, `.vue`, `.proto`).
- **Live Settings Filter**: Top search field within settings to instantly filter through cards and preferences.

<p align="center">
  <img src="./assets/readme/screenshot_settings_rhythm.png" width="92%"
       alt="Rhythm-style Settings: grouped cards, prewarming control, custom extension management">
</p>

---

## ⚙️ Architecture & Under the Hood

<p align="center">
  <img src="./assets/readme/workflow.svg" width="100%"
       alt="Architecture diagram: IndexWarmer, SQLDelight database, SearchEngine, and SearchTreeBuilder">
</p>

- **Text Caching Instead of FTS5**: Code search demands exact substring matching, regex, word boundaries, and case sensitivity that tokenizers break. Decoded text is cached in SQLite, and subsequent searches run lightning fast in memory.
- **High-Concurrency Coroutines**: Parallel directory and file batch scanning throttled via `Semaphore(concurrency)` to avoid IO saturation. Essential on Android SAF where IPC round-trip latency is high.
- **Read-Through Filesystem**: `IndexedFileSystem` checks the cache snapshot first, smoothly falling back to the live filesystem if warming is still in progress.
- **Incremental Synchronization**: Automatically detects changes via `lastModified` and cleans up deleted files.

### CJK and Encoding Integrity

Text decoding order implemented in `core/src/commonMain/kotlin/dev/hibernalglow/grzeb/core/text/TextDecoder.kt`:

```text
1. BOM priority: UTF-8 / UTF-16 LE / UTF-16 BE
2. NUL bytes check: alternating NULs detected as BOM-less UTF-16; otherwise rejected as binary
3. UTF-8 decoding; accepted if no replacement characters appear
4. Fallback to GB18030 (superset of GBK / GB2312):
   - Zero replacement characters permitted
   - Chinese characters must comprise ≥ 5% of text (prevents Latin-1 misdetection)
5. Latin-1 fallback if replacement characters exceed 10%; otherwise preserved UTF-8
```

---

## 🖥️ Platform Support Matrix

| Feature | Desktop (JVM: macOS / Windows / Linux) | Mobile (Android) |
| :--- | :--- | :--- |
| **Directory Picker** | Native macOS `FileDialog` / Swing `JFileChooser` | Android Storage Access Framework (SAF) Tree URI |
| **Index Persistence** | Local disk at `~/.grzeb/index.db` | App sandbox at `grzeb-index.db` |
| **Full-Text Search** | Yes (multi-workspace parallel search) | Yes (concurrent SAF scanning) |
| **Reveal in File Manager** | Native (`open -R` / `explorer /select,` / `xdg-open`) | N/A (opens inside in-app preview) |
| **Adaptive Layout** | Dual-pane resizable sidebar with saved width | Single-pane adaptive navigation |
| **System Back** | No system back gesture (use the close button) | Predictive back dismisses the preview: it slides with the gesture and reveals the results underneath (Android 13+, on by default on Android 15+) |

---

## 🛠️ Development & Building

### Prerequisites
- **JDK 17+** (JDK 21 recommended)
- **Android SDK** (configured with AGP 9.1+ and compileSdk 37)

### Common Gradle Commands
```bash
# Run the Desktop application
./gradlew :desktopApp:run

# Install debug APK on connected Android device/emulator
./gradlew :androidApp:installDebug

# Run multiplatform unit tests
./gradlew test :core:jvmTest :shared:jvmTest

# Package native desktop installer for current OS (.dmg / .msi / .deb)
./gradlew :desktopApp:packageDistributionForCurrentOS
```

### Module Structure
```text
core/        Platform-agnostic search logic: filesystem abstraction, decoding, SQLDelight DB, tree builder (Zero UI)
shared/      Shared Compose UI, M3 Expressive components, and PlatformServices
desktopApp/  Desktop window entry point & AWT lifecycle
androidApp/  Android Activity entry point & SAF permissions
```

---

## 📌 License

Distributed under the [MIT License](./LICENSE).
