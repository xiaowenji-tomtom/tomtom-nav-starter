plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val tomtomSdkVersion: String = project.property("tomtomSdkVersion") as String

android {
    namespace = "com.tomtom.demo.nav.core.data"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        // 收藏/历史改由 TomTom Personal Data SDK 存储，该 API 仅在 extended 变体提供
        missingDimensionStrategy("tomtom-sdk-version", "extended")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // —— 个性化数据（收藏 / 历史 / 家与公司）：TomTom Personal Data 模块（离线本地存储）——
    // 见 https://docs.tomtom.com/navigation/android/guides/personalization/personal-data
    api("com.tomtom.sdk.personaldata:personal-data-common:$tomtomSdkVersion")
    api("com.tomtom.sdk.personaldata:personal-data:$tomtomSdkVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
