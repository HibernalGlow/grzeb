import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    android {
        namespace = "dev.hibernalglow.grzeb.core"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    // iOS 本轮保持"最低兼容"：暂不声明 target。声明 iOS target 会拉起 Kotlin/Native
    // 工具链，且 framework 链接还需要 Xcode —— 属于 iOS 阶段的工作。启用时加上：
    //
    //   listOf(iosArm64(), iosSimulatorArm64()).forEach {
    //       it.binaries.framework { baseName = "Core"; isStatic = true }
    //   }
    //
    // 平台侧届时用 UnsupportedGrzebFileSystem 替换为 UIDocumentPicker 实现。

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
