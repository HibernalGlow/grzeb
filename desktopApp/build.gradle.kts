import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

// 不写这一段，compileJava 会跟着 JDK 走（21），与 compileKotlin 的 17 撞校验收
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(projects.shared)
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "dev.hibernalglow.grzeb.desktop.MainKt"

        // 不写这个块，packageDistributionForCurrentOS 是个空任务：
        // 按格式（dmg/msi/deb）的打包任务压根不会注册，构建「成功」但什么都不产出。
        nativeDistributions {
            vendor = "HibernalGlow"
            packageVersion = "0.1.0"
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
        }
    }
}
