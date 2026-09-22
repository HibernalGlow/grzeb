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
    }
}
