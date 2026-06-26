package com.tomtom.demo.nav.core.sdk

import android.content.Context
import com.tomtom.demo.nav.core.sdk.auth.AuthManager
import com.tomtom.demo.nav.core.sdk.auth.PRODUCT_NAVIGATION
import com.tomtom.demo.nav.core.sdk.guidance.GuidanceBus
import com.tomtom.demo.nav.core.sdk.location.LocationEngine
import com.tomtom.demo.nav.core.sdk.map.MapDisplayService
import com.tomtom.demo.nav.core.sdk.mapdata.MapDataService
import com.tomtom.demo.nav.core.sdk.navigation.NavigationEngine
import com.tomtom.demo.nav.core.sdk.routing.RoutingService
import com.tomtom.demo.nav.core.sdk.safety.SafetyLocationsService
import com.tomtom.demo.nav.core.sdk.search.SearchService
import com.tomtom.demo.nav.core.sdk.traffic.TrafficLayerService
import com.tomtom.demo.nav.core.sdk.tts.TtsService
import com.tomtom.sdk.addon.onboard.NavMode
import com.tomtom.sdk.addon.onboard.NavSdk
import com.tomtom.sdk.annotations.BetaSdkInitializationApi
import com.tomtom.sdk.common.configuration.buildSdkConfiguration
import com.tomtom.sdk.routing.online.OnlineRoutePlanner
import com.tomtom.sdk.search.online.OnlineSearch

/**
 * SDK 服务统一工厂 —— 本架构的核心规则（见 Workshop《应用层架构》页）：
 *
 *   UI / ViewModel 不直接持有 SDK 句柄，一律经本工厂获取各能力域服务；
 *   授权状态变化 → ServiceModeController 切模式 → 调用方重建服务，业务代码零感知。
 *
 * 九大能力域（与 Workshop《模块总览》页一一对应）：
 *
 * | PPT 模块            | 代码位置（本模块内）                  | 模式相关 |
 * |---------------------|---------------------------------------|----------|
 * | 地图显示 Map Display| map/MapDisplayService                 | 否       |
 * | 定位 Location       | location/LocationEngine               | 否       |
 * | 搜索 Search         | search/SearchService                  | 是 ①     |
 * | 路径规划 Routing    | routing/RoutingService                | 是 ①     |
 * | 导航引导 Navigation | navigation/NavigationEngine           | 是 ②     |
 * | 实时路况 Traffic    | traffic/TrafficLayerService           | 是 ③     |
 * | 语音播报 TTS        | tts/TtsService                        | 否       |
 * | 离线数据 DataMgmt   | mapdata/MapDataService                | —— 离线兜底前提 |
 * | 安全提醒 Safety     | safety/SafetyLocationsService         | 是 ③＋合规 |
 *
 * ① OnboardOnly 模式在 NDS 数据 + 离线授权到位后返回离线实现，接口不变；
 * ② 离线导航初始化路径随离线数据接入（讲义 2.6）；
 * ③ 在线数据服务，OnboardOnly 下不可用。
 */
class NavServiceFactory(
    private val context: Context,
    private val authManager: AuthManager,
    private val modeController: ServiceModeController,
) {
    val currentMode: ServiceMode get() = modeController.mode.value

    /** 当前导航产品的 API Key（由 AMS 鉴权下发；Stub 模式下来自 gradle.properties）。 */
    private fun navigationApiKey(): String =
        authManager.state.value.productsOrNull()?.get(PRODUCT_NAVIGATION)?.apiKey
            ?: throw OnboardDataNotProvisioned("无有效导航授权（OnboardOnly 模式）")

    // —— 地图显示 Map Display ——
    val mapDisplay: MapDisplayService = MapDisplayService(apiKeyProvider = { navigationApiKey() })

    // —— 定位 Location ——
    val locationEngine: LocationEngine = LocationEngine(context)

    // —— 实时路况 Traffic（地图层操作；在线服务，OnboardOnly 下勿开启）——
    val trafficLayers: TrafficLayerService = TrafficLayerService()

    // —— 语音播报 TTS ——
    val tts: TtsService = TtsService(context)

    // —— 离线数据 Data Management（NDS；接入 TODO 见类内）——
    val mapData: MapDataService = MapDataService()

    // —— 安全提醒 Safety Locations（合规约束；接入 TODO 见类内）——
    val safetyLocations: SafetyLocationsService = SafetyLocationsService()

    // —— 搜索 Search（按运行模式创建）——
    fun createSearchService(): SearchService = when (currentMode) {
        ServiceMode.ONLINE_FIRST ->
            SearchService(OnlineSearch.create(context = context, apiKey = navigationApiKey()))
        ServiceMode.ONBOARD_ONLY -> throw OnboardDataNotProvisioned(
            "离线搜索需 NDS 数据灌装与离线授权 —— TODO: OfflineSearch（mapdata/MapDataService KDoc 与讲义 2.3）",
        )
    }

    // —— 路径规划 Routing（按运行模式创建）——
    fun createRoutingService(): RoutingService = when (currentMode) {
        ServiceMode.ONLINE_FIRST ->
            RoutingService(OnlineRoutePlanner.create(context = context, apiKey = navigationApiKey()))
        ServiceMode.ONBOARD_ONLY -> throw OnboardDataNotProvisioned(
            "离线算路需 NDS 数据灌装与离线授权 —— TODO: OfflineRoutePlanner（mapdata/MapDataService KDoc 与讲义 2.4）",
        )
    }

    // —— 导航引导 Navigation（经 NavSdk 门面，进程内一次初始化）——
    //
    // NavSdk（onboard-switch 附加模块）是 TomTomSdk 的 drop-in 替代：在线模式下转发
    // 给原生 TomTomSdk，纯离线模式下基于 NDS 构建离线栈。本工程当前仅走在线分支
    // （OnboardOnly 需 NDS 数据灌装与离线授权，尚未具备）；待数据就位后，下方
    // OnboardOnly 分支即可改为 NavSdk.initialize(context, NavMode.OnboardOnly(...))。
    @OptIn(BetaSdkInitializationApi::class)
    fun createNavigationEngine(telemetryManager: TelemetryManager, guidanceBus: GuidanceBus): NavigationEngine {
        if (currentMode == ServiceMode.ONBOARD_ONLY) {
            throw OnboardDataNotProvisioned(
                "离线导航需 NDS 数据灌装与离线授权 —— TODO: NavSdk.initialize(ctx, NavMode.OnboardOnly(...))（讲义 2.6）",
            )
        }
        if (!NavSdk.isInitialized) {
            NavSdk.initialize(
                context = context.applicationContext,
                mode = NavMode.OnlineFirst(
                    buildSdkConfiguration(
                        context = context.applicationContext,
                        apiKey = navigationApiKey(),
                        // Telemetry 同意经 TelemetryManager 注入 —— 用户未同意前为 TelemetryOff
                        telemetryUserConsent = { telemetryManager.currentConsent() },
                    ),
                ),
            )
        }
        return NavigationEngine(NavSdk.navigation, guidanceBus)
    }
}
