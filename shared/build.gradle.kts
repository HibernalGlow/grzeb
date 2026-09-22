import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    android {
        namespace = "dev.hibernalglow.grzeb.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    // iOS 与 core 保持一致：本轮不声明 target（见 core/build.gradle.kts 的说明）。

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.kotlinx.coroutines.core)
        }

        androidMain.dependencies {
            // SAF 目录选择走 ActivityResultLauncher
            implementation(libs.androidx.activity.compose)
        }

        jvmMain.dependencies {
            // AWT 文件对话框必须在 EDT 上开
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}
