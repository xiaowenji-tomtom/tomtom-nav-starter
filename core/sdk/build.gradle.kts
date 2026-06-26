plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val tomtomSdkVersion: String = project.property("tomtomSdkVersion") as String

android {
    namespace = "com.tomtom.demo.nav.core.sdk"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        // NavSDK 发布有 complete / extended 两个变体；extended 提供纯离线与 Personal Data 等扩展能力
        missingDimensionStrategy("tomtom-sdk-version", "extended")
        // 开发期 Key 注入；量产时 Key 由 AMS 鉴权下发，此字段仅供 StubAmsClient 使用
        buildConfigField(
            "String",
            "TOMTOM_API_KEY",
            "\"${project.findProperty("tomtomApiKey") ?: ""}\"",
        )
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api(project(":core:platform"))

    // —— onboard-switch 附加模块：NavSdk 门面（在线 / 纯离线运行模式切换）——
    // 由 go-sdk-android :onboard-switch 模块编译产出的本地 AAR（extended 变体），
    // 经工程内本地 Maven 仓库 local-repo/ 以坐标方式消费（见 local-repo/README.md）。
    // NavSdk 是 TomTomSdk 的 drop-in 替代。
    api("com.tomtom.sdk.addon:onboard-switch:2.3.1")

    // —— TomTom NavSDK（api 暴露给上层模块）——
    api("com.tomtom.sdk.maps:map-display-standard:$tomtomSdkVersion")
    api("com.tomtom.sdk:init:$tomtomSdkVersion")
    api("com.tomtom.sdk.location:provider-default:$tomtomSdkVersion")
    api("com.tomtom.sdk.location:provider-map-matched:$tomtomSdkVersion")
    api("com.tomtom.sdk.location:provider-simulation:$tomtomSdkVersion")
    api("com.tomtom.sdk.navigation:navigation:$tomtomSdkVersion")
    // 引导语音播报引擎（自绘导航 UI 的语音，取代 NavigationFragment 内置 TTS）
    api("com.tomtom.sdk:tts:$tomtomSdkVersion")
    api("com.tomtom.sdk.routing:route-planner:$tomtomSdkVersion")
    api("com.tomtom.sdk.search:search:$tomtomSdkVersion")
    // NavSdk 门面在线分支签名引用 ReverseGeocoder —— 本地 AAR 不带传递依赖，需显式声明
    api("com.tomtom.sdk.search:reverse-geocoder:$tomtomSdkVersion")
    api("com.tomtom.sdk.datamanagement:data-store:$tomtomSdkVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
