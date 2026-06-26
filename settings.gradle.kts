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
        // TomTom NavSDK 公开 Maven 仓库（公司网络代理需放行此域名）
        maven("https://repositories.tomtom.com/artifactory/maven")
        // 工程内本地仓库：分发 onboard-switch 附加模块 AAR（见 local-repo/README.md）
        maven { url = uri("$rootDir/local-repo") }
    }
}

rootProject.name = "tomtom-nav-starter"
include(":app", ":core:sdk", ":core:platform", ":core:data")
