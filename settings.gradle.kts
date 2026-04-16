pluginManagement {
    repositories {
        // 优先使用官方插件仓库，避免镜像缺失新版本或元数据不完整时影响同步。
        google()
        mavenCentral()
        gradlePluginPortal()
        // 保留国内镜像作为回退源，兼顾下载速度与可用性。
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 先查官方仓库，确保 alpha 依赖和 AAR 元数据能完整命中。
        google()
        mavenCentral()
        // 国内镜像保留在后面，作为网络较慢时的补充下载源。
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}

rootProject.name = "autobook"

include(
    ":app",
    ":core:model",
    ":core:common",
    ":core:network",
    ":core:database",
    ":core:data",
    ":feature:onboarding",
    ":feature:ledger",
    ":feature:pending",
    ":service:notification",
    ":service:automation",
)
