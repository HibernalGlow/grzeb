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
            packageName = "grzeb"
            description = "grzeb"
            vendor = "HibernalGlow"
            modules("java.instrument", "java.sql", "jdk.unsupported")
            // jpackage 的 macOS bundler 拒绝首位为 0 的 app-version
            // （"The first number in an app-version cannot be zero or negative"），
            // Linux/Windows 都不拦，所以只有 mac 的包版本抬到 1.0.0；
            // 应用本身仍是 0.2.2（androidApp 的 versionName、README 徽章都按 0.2.2 走），
            // CI 产物的文件名也按发布名 grzeb-<发布版本>-macos.dmg 重命名。
            packageVersion = if (System.getProperty("os.name").contains("Mac", ignoreCase = true)) "1.0.0" else "0.2.2"
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)

            macOS {
                bundleID = "dev.hibernalglow.grzeb"
                packageName = "grzeb"
                iconFile.set(project.file("src/main/resources/icons/icon.icns"))
            }
            windows {
                menuGroup = "grzeb"
                upgradeUuid = "d7e974e3-5182-4bf1-a877-3e2b20cecf61"
                iconFile.set(project.file("src/main/resources/icons/icon.ico"))
            }
            linux {
                packageName = "grzeb"
                iconFile.set(project.file("src/main/resources/icons/icon.png"))
            }
        }
    }
}
