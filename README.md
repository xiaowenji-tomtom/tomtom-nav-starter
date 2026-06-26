# TomTom Nav Demo Starter（View 体系 · TomTom NavSDK 2.x）

基于 **View/XML 体系**实现的 TomTom NavSDK 2.x 导航 Demo 起步工程（不使用 Compose），
覆盖《应用层架构》《授权架构与运行模式》《多屏引导数据流》等核心设计，可直接作为正式工程的骨架。

> 首次打开工程？先看 [架构总览 docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)（含分层架构图与核心数据流，GitHub 可直接渲染）。

## 快速开始

1. 在 `gradle.properties` 中填入 `tomtomApiKey`（或构建时 `-PtomtomApiKey=xxx` 传入；量产时 Key 由 AMS 鉴权下发）；
2. `./gradlew :app:assembleDebug`（首次构建需访问 `repositories.tomtom.com`，公司代理需放行）；
3. 安装运行：警告页同意 → **系统定位权限弹窗**（首装会弹；拒绝不影响启动，仅无法显示当前位置与后台保活）→ 地图（路况层自动开启）→ 搜索目的地（或聚焦输入框查看收藏/历史）→ 选结果出多路线 → **点击任一路线**开始模拟导航。

### 已验证（Pixel 5 API 35 模拟器，2026-06）

完整闭环实测通过：警告页/隐私同意 → 定位权限弹窗（授予/拒绝两条分支均不崩溃，量产车机通常预授）→
OnlineFirst 横幅（Stub 鉴权）→ 悉尼地图 + 实时路况层 →
搜索 "central station" 返回真实结果 → 多路线绘制（主路线 + 灰色备选）→ 点击路线开始模拟导航
（跟随视角、限速牌 40/车速 54、TBT 面板与语音）→ 回桌面后导航后台运行（前台通知存活）。
logcat 可观测的多屏/ISA 数据流：
`VehicleBus: ISA speedLimit=40.0 speed=54.0`（限速下发整车链路）、
`ClusterChannel: In 200 meters turn left onto Park Street, remain 1731m/572s`（仪表通道）。

环境：JDK 17 · compileSdk 35 · minSdk 26 · NavSDK 2.3.1（`complete` 变体，需 `missingDimensionStrategy`，已配置）· ABI: arm64-v8a（8155）/ x86_64（模拟器）。

## 九大能力域 → 代码位置（与 Workshop《模块总览》页一一对应）

| PPT 模块 | SDK 封装（:core:sdk） | UI / 消费侧（:app） | 状态 |
|---|---|---|---|
| 地图显示 Map Display | `map/MapDisplayService` | `feature/home/MainActivity`（地图宿主） | ✅ 可用 |
| 定位 Location | `location/LocationEngine`（GPS/吸附/模拟） | MainActivity 装配 | ✅ 可用（CAN 注入 TODO） |
| 搜索 Search | `search/SearchService` | `feature/search/`（VM + 列表） | ✅ 在线关键字（周边/沿途/EV TODO） |
| 路径规划 Routing | `routing/RoutingService`（多备选+偏好+EV） | MainActivity 多路线绘制 | ✅ 在线（途经点/充电规划 TODO） |
| 导航引导 Navigation | `navigation/NavigationEngine` | MainActivity 引导宿主 + NavigationFragment | ✅ 可用 |
| 实时路况 Traffic | `traffic/TrafficLayerService`（地图层） | MainActivity 按模式开关 | ✅ 地图层（光柱图 TODO，授权★） |
| 语音播报 TTS | `tts/TtsService`（语言/开关） | 设置页开关 + 引导 UI 注入 | ✅ 内置语音（音频焦点/自定义引擎 TODO） |
| 离线数据 Data Management | `mapdata/MapDataService`（NDS 接口） | 设置页"离线地图管理"入口 | 🔶 接口就绪，待离线授权与数据包（★） |
| 安全提醒 Safety Locations | `safety/SafetyLocationsService` | —（合规矩阵驱动） | 🔶 接口就绪，待授权（★）+ 逐国合规（⚠） |

每个服务类的文件头都标注了：对应 PPT 模块、官方文档路径、所属 Workstream 的 TODO。
横切架构组件（不属于单一 SDK 模块）：

```
:core:sdk    ServiceMode / ServiceModeController / NavServiceFactory   授权驱动的运行模式（架构核心）
             auth/（AmsClient·AuthManager）                            AMS 对接 + 缓存 + 宽限期
             guidance/（GuidanceBus·IsaSpeedLimitForwarder）           多屏分发 + ISA 链路
             ev/EvProfileProvider                                     EV 车辆 profile
             TelemetryManager                                          隐私同意
:core:platform  VinProvider · VehicleBus · VehicleSignalSource · ClusterChannel   主机集成点（WS5）
:core:data      Places(Room) · SettingsRepository                                数据层
:app            feature/home · search · settings · onboarding · guidance · widget  按 feature 分包
```

**关键规则**：UI / ViewModel 一律经 `NavServiceFactory` 获取各能力域服务；
授权变化 → `ServiceModeController` 切模式 → 调用方重建服务，业务代码零感知。

## 已实现（对应阶段 2 MVP + 部分阶段 3 骨架）

- 开机警告页 + 隐私同意（Telemetry 默认关闭，设置页可改，即时生效）
- 地图显示（手势、车标、昼夜跟随系统）+ 设置入口
- 在线搜索（关键字 + 位置偏置）、收藏 / 历史目的地（Room，输入框聚焦展示）
- 多路线规划（主路线 + ≤2 备选，点选任一开始导航）；算路偏好（避收费/不走高速）即时生效
- EV 算路开关：开启后以电动车能耗模型算路（占位参数，待整车标定数据替换）
- TBT 引导（官方导航 UI 组件，View 体系）+ 模拟行驶 + 路线进度置灰
- 导航前台 Service：后台运行 + 通知栏引导信息
- GuidanceBus 多屏分发：桌面 Widget、仪表通道（Log 实现）、**ISA 限速转发**（Log 实现，含限速/不限速/车速）
- `geo:` deeplink：外部坐标发起导航（Send to Car / 语音助手的对接入口）
- 授权链路：启动读缓存（不等网络）→ 异步鉴权（超时 10s）→ 宽限期（默认 7 天）→ 模式横幅实时展示

## 集成 TODO（按 Workshop 材料逐项替换，对应 Workstream 分工）

| 位置 | TODO | WS | 对应材料 |
|---|---|---|---|
| `auth/AmsClient.kt` | 替换 Stub 为真实 TomTomAuth SDK | WS1 | 授权时序页 |
| `auth/AuthManager.kt` | 缓存改 EncryptedSharedPreferences；宽限期/热切换产品定义 | WS1 | 状态机页 |
| `platform/VinProvider` | 主机接口读取真实 VIN / 车型 ID | WS5 | AMS 设计文档 |
| `platform/VehicleBus` | CAN 写通道（ISA 信号协议与整车签订） | WS5 | 总体架构页 |
| `platform/VehicleSignalSource` | CAN 读信号 → LocationInterceptor 注入定位 | WS5 | 定位 automotive 专题页 |
| `platform/ClusterChannel` | 主机投屏方案实现（字段/刷新率按《引导数据契约》） | WS5 | 多屏数据流页 |
| `NavServiceFactory.kt` | OnboardOnly 分支接入 OfflineSearch / OfflineRoutePlanner / NdsStore | WS4 | 讲义 2.3/2.4/2.6 |
| `ev/EvProfileProvider.kt` | 整车标定能耗/充电曲线 + 实时 SOC；充电站自动规划（授权后） | WS3 | EV 算路专题页 |
| `SettingsActivity` | 离线地图管理页（NDS 区域下载/更新 UI） | WS4 | 讲义 2.6 |
| `App.kt` | 手工 DI 替换为 Hilt | WS1 | 工程决策表 |

## 说明

- 官方示例 App 为 Compose 实现，本工程是其 **View 体系等价物** + 授权/模式/多屏架构，SDK 调用方式与官方教程一致。
- NavSDK 发布为 complete / extended 双变体：本工程使用 `complete`（标准版，免仓库凭据）。
- `NavigationFragment`（导航 UI 组件）当前版本带 deprecation 告警，功能可用；可逐步替换为自绘引导面板（订阅 GuidanceBus）。
- 文档入口：<https://docs.tomtom.com/navigation/android/introduction/introduction>
