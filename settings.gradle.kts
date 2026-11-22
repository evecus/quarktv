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
        maven { url = uri("https://maven.aliyun.com/repository/public") }  // DanmakuFlameMaster 镜像（jitpack 冷启动慢）
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "QuarkTV"
include(":app")
