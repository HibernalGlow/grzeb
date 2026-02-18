# Tauri Alpha for Desktop & Android + React + Typescript

This template should help get you started trying out Tauri for Android. I have not tested this project for IOS, feel free to try and contribute your changes here :)

## Recommended IDE Setup

- [VS Code](https://code.visualstudio.com/) + [Tauri](https://marketplace.visualstudio.com/items?itemName=tauri-apps.tauri-vscode) + [rust-analyzer](https://marketplace.visualstudio.com/items?itemName=rust-lang.rust-analyzer)

## Prerequisites

https://beta.tauri.app/2/guide/prerequisites/

Windows:
With Visual Studio Installer install Visual Studio 2022 with Desktop development with C++ selected.

Install Nodejs

Install yarn

Install Rust with rustup

Add rustup targets:
```
rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
```

Install Android Studio

Start Android Studio and go to settings and search for Android SDK

There install:
- An android SDK Platform
- Android SDK Platform-Tools
- NDK (Side by side)
- Android SDK Build-Tools
- Android SDK Command-line Tools

Set Environment Variables:
- JAVA_HOME => C:\Program Files\Android\Android Studio\jbr
- ANDROID_HOME => $env:LocalAppData\Android\Sdk
- NDK_HOME => $env:LocalAppData\Android\Sdk\ndk\25.0.8775105

Install tauri-cli:
```
cargo install tauri-cli --version "^2.0.0-alpha"
```

## Usage
create android project with:
```
yarn tauri android init
```

Start in dev mode with (only works when no android device is connected in emulator mode!!!):
```
yarn tauri android dev
```

## Building
```
yarn tauri android build
```

Then the build app must be signed.

Create a key to sign the android app
```
keytool -genkey -v -keystore ./src-tauri/gen/android/release-keystore.jks -alias alias_name -keyalg RSA -keysize 2048 -validity 10000
```

Now sign the app
```
& $env:ANDROID_HOME/build-tools/34.0.0/apksigner sign --ks ./src-tauri/gen/android/release-keystore.jks ./src-tauri/gen/android/app/build/outputs/apk/universal/release/app-universal-release-unsigned.apk
```

You can find the build app under:
```
./src-tauri/gen/android/app/build/outputs/apk/universal/release/app-universal-release-unsigned.apk
```

## Helpful Resources to get started with tauri for andriod

- https://beta.tauri.app/2/guide/prerequisites/
- https://beta.tauri.app/2/guide/create/mobile/
- https://dev.to/lynxgsm/tauri-the-flutter-killer-5h27
- https://studioterabyte.nl/en/blog/tauri-mobile-app-development

