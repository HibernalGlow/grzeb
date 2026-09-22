import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "grzeb"
        browser {
            commonWebpackConfig {
                outputFileName = "grzeb.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(projects.shared)
            // 入口用的是 ComposeViewport，属于 compose.ui，得显式带上
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
        }
    }
}
