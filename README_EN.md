# Grzeb (File Viewer & Search)

[![React Native](https://img.shields.io/badge/React_Native-0.81-blue.svg)](https://reactnative.dev/)
[![Expo](https://img.shields.io/badge/Expo-52-black.svg)](https://expo.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.9-blue.svg)](https://www.typescriptlang.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

[**阅读中文版 (Read in Chinese)**](./README.md)

**Grzeb** is a high-performance native file viewer and full-text search tool built for Android using React Native and Expo. It delivers a silky-smooth reading and searching experience through a **Truly Native Viewer**, completely ditching traditional WebView rendering solutions.

## ✨ Key Features

### 1. Truly Native Viewer

- **Pure Native Rendering**: Powered by `FlatList` virtualization, effortlessly handling millions of lines of code or massive novels with minimal memory footprint.
- **Smart Formatting**:
  - **Dynamic Word Wrap**: Perfectly wraps long lines of text, making logs or minified code readable without horizontal scrolling.
  - **Precise Line Numbers**: Matches line numbers accurately to the start of each line, even when wrapping occurs.
  - **Instant Jump**: Supports millisecond-level positioning to any line in large files.

### 2. Deep Search Engine

- **Fast & Powerful**: Supports regex, case sensitivity, and whole-word matching.
- **Smart Preview**:
  - **Dynamic Context**: Automatically detects line length. Short lines show ±2 lines of context, while long lines (>50 chars) focus only on the match to keep the interface clean.
  - **Seamless Highlighting**: Refactored with nested Text components to ensure search keywords remain physically continuous across line breaks, eliminating visual glitches.

### 3. Android SAF Integration

- **System-Level Access**: Full support for the **Android Storage Access Framework (SAF)**.
- **Unrestricted Access**: Directly read/write to protected directories like `Android/data`, external SD cards, and OTG devices (Android 11+ compatible).

### 4. Modern Stack

- **Modern UI**: Built with **NativeWind v4 (Tailwind CSS)**, featuring native dark mode support.
- **File Support**: Native handling for UTF-8 text, EPUB ebooks (text extraction mode), and various code formats.

## 🛠 Tech Stack

- **Framework**: [React Native](https://reactnative.dev/) + [Expo](https://expo.dev/)
- **Router**: [Expo Router](https://docs.expo.dev/router/introduction/)
- **Styling**: [NativeWind](https://www.nativewind.dev/) + [Lucide Icons](https://lucide.dev/)
- **Core Libs**:
  - `expo-file-system`: SAF file stream handling
  - `jszip`: EPUB/ZIP structure parsing
  - `@rn-primitives`: Accessible UI primitives

## 🚀 Installation & Build

### Prerequisites

- Node.js >= 18
- pnpm / npm / yarn
- JDK 17+
- Android Studio & Android SDK

### Run Locally

```bash
# 1. Clone repo
git clone https://github.com/your-repo/grzeb.git
cd grzeb

# 2. Install dependencies
pnpm install

# 3. Start Metro bundler
pnpm start

# 4. Run on Android (Device/Emulator required)
pnpm android
```

### Build APK

Recommended using EAS Build for cloud or local builds:

```bash
# Install EAS CLI
npm install -g eas-cli

# Build Preview APK
eas build -p android --profile preview --local
```

## 📖 Usage Guide

1. **Grant Permission**: On first launch, grant SAF permission to access target folders (root directory supported).
2. **Browse Files**: Tap files to open in the native viewer; tap EPUBs to auto-parse chapters.
3. **Global Search**: Tap the search bar at the top, enter keywords (regex supported). Tap results to jump directly to the line with highlighting.

## 📄 License

This project is licensed under the [MIT License](LICENSE).
