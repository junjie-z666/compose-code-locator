rootProject.name = "locator-gradle-plugin"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

// 允许 Gradle 自动下载缺失版本的 JDK；included build 有独立 settings，必须与主仓同步启用。
// 注意 plugins 块必须在 pluginManagement 之后（settings 脚本的语法顺序要求）
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
