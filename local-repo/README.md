# `local-repo/` — 工程内本地 Maven 仓库

存放随工程分发的本地 AAR，以 Maven 仓库布局提供（而非 `files(...)` 直接文件依赖）——
Android 库模块（`:core:sdk`）打包自身 AAR 时不支持直接的本地 `.aar` 文件依赖，故以
坐标方式消费。在 [settings.gradle.kts](../settings.gradle.kts) 中声明为
`maven { url = uri("local-repo") }`。

## `com.tomtom.sdk.addon:onboard-switch:2.3.1`

TomTom NavSDK 的 **onboard-switch** 附加模块（门面），提供 `NavSdk` —— `TomTomSdk` 的
drop-in 替代，可在 **在线**（转发原生 `TomTomSdk`）与 **纯离线 / NDS**（OnboardOnly）两种
运行模式间切换。

- 入口：`com.tomtom.sdk.addon.onboard.NavSdk` / `com.tomtom.sdk.addon.onboard.NavMode`
- 编译来源：`go-sdk-android` 仓库 `:onboard-switch` 模块的 `assembleExtendedRelease` 产物
  （extended 变体；离线分支依赖 `@RestrictToExtendedFlavor` API）
- 对应 SDK 版本：**2.3.1**（须与 `gradle.properties` 的 `tomtomSdkVersion` 一致）
- POM 不声明传递依赖；门面所需的 SDK 模块由 `core/sdk/build.gradle.kts` 显式声明

本工程以 `complete` 变体构建、当前仅使用在线分支（OnlineFirst）。若需启用纯离线导航，
应改用 extended 变体并灌装 NDS 数据。

### 更新 AAR

```bash
cd ~/Developer/go-sdk-android
./gradlew :onboard-switch:assembleExtendedRelease
cp onboard-switch/build/outputs/aar/onboard-switch-extended-release.aar \
   <本工程>/local-repo/com/tomtom/sdk/addon/onboard-switch/2.3.1/onboard-switch-2.3.1.aar
```
