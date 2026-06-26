package com.tomtom.demo.nav

import android.app.Application
import com.tomtom.demo.nav.core.data.PlacesRepository
import com.tomtom.demo.nav.core.data.SettingsRepository
import com.tomtom.demo.nav.core.platform.ClusterChannel
import com.tomtom.demo.nav.core.platform.LogClusterChannel
import com.tomtom.demo.nav.core.platform.LogVehicleBus
import com.tomtom.demo.nav.core.platform.StubVehicleSignalSource
import com.tomtom.demo.nav.core.platform.StubVinProvider
import com.tomtom.demo.nav.core.platform.VehicleBus
import com.tomtom.demo.nav.core.platform.VehicleSignalSource
import com.tomtom.demo.nav.core.platform.VinProvider
import com.tomtom.demo.nav.core.sdk.NavServiceFactory
import com.tomtom.demo.nav.core.sdk.ServiceModeController
import com.tomtom.demo.nav.core.sdk.TelemetryManager
import com.tomtom.demo.nav.core.sdk.auth.AuthManager
import com.tomtom.demo.nav.core.sdk.auth.StubAmsClient
import com.tomtom.demo.nav.core.sdk.ev.DefaultEvProfileProvider
import com.tomtom.demo.nav.core.sdk.ev.EvProfileProvider
import com.tomtom.demo.nav.core.sdk.guidance.GuidanceBus
import com.tomtom.demo.nav.core.sdk.guidance.IsaSpeedLimitForwarder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class App : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * 手工依赖装配（starter 简化版）。
 * TODO(工程化)：量产工程建议替换为 Hilt（见 Workshop《工程结构与关键设计决策》页），
 * 各组件的构造关系与此处一一对应。
 */
class AppContainer(app: Application) {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // —— 平台适配层（WS5 替换为真实主机实现）——
    val vinProvider: VinProvider = StubVinProvider()
    val vehicleBus: VehicleBus = LogVehicleBus()
    val vehicleSignals: VehicleSignalSource = StubVehicleSignalSource()
    val clusterChannel: ClusterChannel = LogClusterChannel()

    // —— 数据层 ——
    val placesRepository = PlacesRepository(app, appScope)
    val settingsRepository = SettingsRepository(app)

    // —— 服务封装层 ——
    val telemetryManager = TelemetryManager(app)

    /** TODO(集成)：替换为真实 TomTomAuth SDK 客户端。 */
    private val amsClient = StubAmsClient(com.tomtom.demo.nav.core.sdk.BuildConfig.TOMTOM_API_KEY)

    val authManager = AuthManager(app, amsClient, appScope)
    val modeController = ServiceModeController(authManager, appScope)
    val navServiceFactory = NavServiceFactory(app, authManager, modeController)

    val evProfileProvider: EvProfileProvider = DefaultEvProfileProvider()

    // —— 引导分发（多屏 / ISA）——
    val guidanceBus = GuidanceBus()
    val isaForwarder = IsaSpeedLimitForwarder(guidanceBus, vehicleBus)

    init {
        // 启动即异步鉴权（不阻塞 UI）；VIN/车型 ID 来自平台适配层
        authManager.refresh(vin = vinProvider.vin, vehicleModelId = vinProvider.vehicleModelId)
    }
}
