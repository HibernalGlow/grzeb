# Grzeb (File Viewer & Search)

[![React Native](https://img.shields.io/badge/React_Native-0.81-blue.svg)](https://reactnative.dev/)
[![Expo](https://img.shields.io/badge/Expo-52-black.svg)](https://expo.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.9-blue.svg)](https://www.typescriptlang.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

[**Read in English**](./README_EN.md)

**Grzeb** 是一款专为 Android 打造的高性能本地文件查看与全文检索工具。并在 React Native 架构下实现了极致的 **Native Viewer** 体验，彻底摒弃了传统的 WebView 渲染方案，带来丝滑流畅的阅读与搜索体验。

## ✨ 核心特性

### 1. 极致原生查看器 (Truly Native Viewer)

- **纯原生渲染**：基于 `FlatList` 的虚拟化列表技术，轻松承载数万行代码或百万字小说，内存占用极低。
- **智能排版**：
  - **自动换行**：完美支持长行文本的自动折行，阅读日志或 minified 代码不再困难。
  - **精准行号**：即便发生折行，行号依然能精准对齐每行的起始位置。
  - **秒级跳转**：支持大文件任意行号的毫秒级定位跳转。

### 2. 深度全文搜索 (Deep Search)

- **极速引擎**：支持正则表达式、大小写敏感、全词匹配等专业搜索选项。
- **智能预览**：
  - **动态上下文**：自动识别匹配行长度。短行显示上下各 2 行语境，长行（>50字符）自动聚焦匹配行，保持界面整洁。
  - **物理连贯高亮**：通过嵌套 Text 组件重构高亮逻辑，确保搜索关键词在折行处依然连续，无视觉断裂。

### 3. Android SAF 深度支持

- **系统级访问**：完整支持 Android **Storage Access Framework (SAF)**。
- **无障碍读写**：可直接访问 `Android/data`、OTG 设备及外部 SD 卡等受限目录，解决高版本 Android 的文件访问难题。

### 4. 现代技术栈

- **UI 设计**：采用 **NativeWind v4 (Tailwind CSS)** 构建，支持深色模式，界面美观现代。
- **文件支持**：原生支持 UTF-8 文本、EPUB 电子书（纯文本提取模式）及各类代码文件。

## 🛠 技术栈

- **框架**: [React Native](https://reactnative.dev/) + [Expo](https://expo.dev/)
- **路由**: [Expo Router](https://docs.expo.dev/router/introduction/)
- **样式**: [NativeWind](https://www.nativewind.dev/) + [Lucide Icons](https://lucide.dev/)
- **核心库**:
  - `expo-file-system`: 处理 SAF 文件流
  - `jszip`: 解析 EPUB/ZIP 结构
  - `@rn-primitives`: 无障碍原语组件

## 🚀 安装与构建

### 环境要求

- Node.js >= 18
- pnpm / npm / yarn
- JDK 17+
- Android Studio & Android SDK

### 本地运行

```bash
# 1. 克隆项目
git clone https://github.com/your-repo/grzeb.git
cd grzeb

# 2. 安装依赖
pnpm install

# 3. 启动 Metro 服务
pnpm start

# 4. 运行 Android 构建 (需连接真机或模拟器)
pnpm android
```

### 打包 APK

推荐使用 EAS Build 进行云端或本地构建：

```bash
# 安装 EAS CLI
npm install -g eas-cli

# 构建 Preview 版本 APK
eas build -p android --profile preview --local
```

## 📖 使用指南

1. **授权目录**：首次启动需通过 SAF 授权应用访问目标文件夹（支持授权根目录）。
2. **浏览文件**：点击文件直接通过原生查看器打开；点击 EPUB 自动解析章节。
3. **全局搜索**：点击顶部搜索栏，输入关键词。支持正则语法，点击结果可直接跳转至对应行并高亮。

## 📄 许可证

本项目采用 [MIT License](LICENSE) 许可证。
