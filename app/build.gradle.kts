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
        // NavSDK 发布有 complete / extended 两个变体；complete 为标准版（免仓库凭据）
        missingDimensionStrategy("tomtom-sdk-version", "complete")
        ndk {
            // 8155 为 arm64-v8a；x86_64 供模拟器开发
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }
    buildFeatures {
        viewBinding = true
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

    // UI 层依赖（View 体系）
    implementation("com.tomtom.sdk.maps:map-display-standard:$tomtomSdkVersion")
    implementation("com.tomtom.sdk.navigation:ui:$tomtomSdkVersion")

    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
