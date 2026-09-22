rootProject.name = "grzeb"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// 国内镜像放前面：这台上网要走代理，直连 maven central / plugin portal 时不时卡在
// CLOSE_WAIT 上（SQLDelight 的 jar 就卡过十几分钟）。镜像缺版本时 Gradle 会自动往后找，
// 所以原来的 google / mavenCentral / gradlePluginPortal 一个都不删。
// 注意 pluginManagement 必须排在脚本最前面，且块内看不到脚本级的 val，只能内联地址。
pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":core")
include(":shared")
include(":androidApp")
include(":desktopApp")
include(":webApp")
