pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "autobook"

include(
    ":app",
    ":core:model",
    ":core:common",
    ":core:llm",
    ":core:network",
    ":core:database",
    ":core:data",
    ":feature:onboarding",
    ":feature:ledger",
    ":feature:pending",
    ":service:notification",
    ":service:automation",
)
