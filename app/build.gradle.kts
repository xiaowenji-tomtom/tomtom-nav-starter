plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val tomtomSdkVersion: String = project.property("tomtomSdkVersion") as String

android {
    namespace = "com.tomtom.demo.nav"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tomtom.demo.nav.starter"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // NavSDK 发布有 complete / extended 两个变体；extended 提供纯离线与 Personal Data 等扩展能力
        missingDimensionStrategy("tomtom-sdk-version", "extended")
        ndk {
            // 8155 为 arm64-v8a；x86_64 供模拟器开发
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        // Compose 编译器扩展与 Kotlin 1.9.24 对应
        kotlinCompilerExtensionVersion = "1.5.14"
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
    implementation(project(":core:sdk"))
    implementation(project(":core:data"))

    // 地图显示仍是命令式 MapView（经 AndroidView 内嵌到 Compose）——不使用声明式 compose 地图，保持架构不变
    implementation("com.tomtom.sdk.maps:map-display-standard:$tomtomSdkVersion")

    // —— UI 层（Compose 体系）——
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // XML 窗口主题（Theme.TomTomNavDemo）仍由 Material Components 提供；应用内 UI 全 Compose
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
