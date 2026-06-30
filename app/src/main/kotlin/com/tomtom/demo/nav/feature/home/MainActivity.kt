package com.tomtom.demo.nav.feature.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tomtom.demo.nav.App
import com.tomtom.demo.nav.R
import com.tomtom.demo.nav.feature.guidance.NavigationForegroundService
import com.tomtom.demo.nav.feature.search.SearchViewModel
import com.tomtom.demo.nav.feature.settings.SettingsActivity
import com.tomtom.demo.nav.core.sdk.ServiceMode
import com.tomtom.demo.nav.core.sdk.navigation.NavigationEngine
import com.tomtom.demo.nav.core.sdk.routing.RoutePreferences
import com.tomtom.demo.nav.core.sdk.routing.RoutingService
import com.tomtom.demo.nav.ui.theme.TomTomNavDemoTheme
import com.tomtom.sdk.addon.onboard.NavSdk
import com.tomtom.sdk.init.TomTomSdk
import com.tomtom.sdk.location.GeoPoint
import com.tomtom.sdk.location.LocationProvider
import com.tomtom.sdk.location.OnLocationUpdateListener
import com.tomtom.sdk.map.display.MapLocationInfrastructure
import com.tomtom.sdk.map.display.camera.CameraOptions
import com.tomtom.sdk.map.display.camera.CameraTrackingMode
import com.tomtom.sdk.map.display.compose.model.MapDisplayInfrastructure
import com.tomtom.sdk.map.display.visualization.navigation.NavigationVisualizationDataProvider
import com.tomtom.sdk.map.display.visualization.navigation.compose.model.NavigationVisualizationInfrastructure
import com.tomtom.sdk.map.display.visualization.routing.RoutingVisualizationDataProvider
import com.tomtom.sdk.navigation.NavigationOptions
import com.tomtom.sdk.navigation.RoutePlan
import com.tomtom.sdk.routing.options.RoutePlanningOptions
import com.tomtom.sdk.routing.route.Route
import com.tomtom.sdk.routing.route.RouteId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * ［feature:home］导航主屏：声明式 Compose 地图宿主 + 路线展示 + 引导宿主。
 *
 * 地图为 TomTom 声明式 Compose 地图（[com.tomtom.sdk.map.display.compose.TomTomMap]，自管理生命周期，
 * 不再需要 Activity 转发）。架构其余部分不变：算路 / 引导 / 定位编排经 NavServiceFactory，本 Activity
 * 持有编排逻辑与状态，[MainScreen] 仅渲染并回调。
 *
 * 关键约束：Compose 地图需 SDK 已初始化（`TomTomSdk.sdkContext`）。本工程经 NavSdk 门面初始化
 * （在线模式下转发原生 `TomTomSdk.initialize`），故地图基础设施在 `ensureOnlineServices()` 装配在线
 * 服务（含 NavSdk 初始化）后才创建；就绪前 UI 显示加载态。
 */
class MainActivity : ComponentActivity() {

    private val container by lazy { (application as App).container }
    private val searchViewModel: SearchViewModel by viewModels {
        SearchViewModel.factory(container.navServiceFactory, container.placesRepository)
    }

    private var appLocationProvider: LocationProvider? = null
    private var onLocationUpdateListener: OnLocationUpdateListener? = null
    private var routingService: RoutingService? = null
    private var navEngine: NavigationEngine? = null
    private var simulationProvider: LocationProvider? = null
    private var plannedRoutes: List<Route> = emptyList()
    private var routePlanningOptions: RoutePlanningOptions? = null
    private var pendingDestination: GeoPoint? = null

    // 路线可视化数据源（喂给 NavigationVisualization 绘制预览路线，取代命令式 map.addRoute）
    private val routesFlow = MutableStateFlow<List<Route>>(emptyList())
    private val selectedRouteIdFlow = MutableStateFlow<RouteId?>(null)

    // —— Compose UI 状态 ——
    private var mapInfra by mutableStateOf<MapDisplayInfrastructure?>(null)
    private var navVizInfra by mutableStateOf<NavigationVisualizationInfrastructure?>(null)
    private var initialCenter by mutableStateOf(DEFAULT_CENTER)
    private var cameraTrackingMode by mutableStateOf<CameraTrackingMode>(CameraTrackingMode.None)
    private var cameraTarget by mutableStateOf<CameraOptions?>(null)
    private var hasRoute by mutableStateOf(false)
    private var isNavigating by mutableStateOf(false)
    private var showRecenter by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        observeServiceMode()
        ensureOnlineServices()
        observeGuidanceAnnouncements()
        requestLocationPermissionsIfNeeded()
        handleDeepLink(intent)

        setContent {
            TomTomNavDemoTheme {
                MainScreen(
                    mapInfra = mapInfra,
                    navVizInfra = navVizInfra,
                    initialCenter = initialCenter,
                    cameraTrackingMode = cameraTrackingMode,
                    cameraTarget = cameraTarget,
                    serviceModeFlow = container.modeController.mode,
                    snapshotFlow = container.guidanceBus.snapshot,
                    resultsFlow = searchViewModel.results,
                    savedFlow = searchViewModel.saved,
                    hasRoute = hasRoute,
                    isNavigating = isNavigating,
                    showRecenter = showRecenter,
                    onSearch = { query ->
                        searchViewModel.search(
                            query = query,
                            bias = appLocationProvider?.lastKnownLocation?.position ?: DEFAULT_CENTER,
                        )
                    },
                    onResultClick = ::onResultClick,
                    onFavorite = { item ->
                        searchViewModel.addFavorite(item)
                        toast(getString(R.string.favorite_added))
                    },
                    onClearResults = { searchViewModel.clearResults() },
                    onDrive = { plannedRoutes.firstOrNull()?.let { startNavigation(it) } },
                    onRecenter = ::recenter,
                    onStopNav = ::stopNavigation,
                    onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onMapPanning = ::onMapPanning,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    /**
     * 外部坐标发起导航的统一入口（Send to Car / 语音助手对接点）：
     * 支持 geo:lat,lng 形式的 deeplink —— 贵司云端/语音功能经此集成。
     */
    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "geo") return
        val coords = data.schemeSpecificPart.substringBefore('?').split(',')
        val lat = coords.getOrNull(0)?.toDoubleOrNull() ?: return
        val lon = coords.getOrNull(1)?.toDoubleOrNull() ?: return
        val destination = GeoPoint(lat, lon)
        if (routingService != null) planRouteTo(destination) else pendingDestination = destination
    }

    // —— 服务装配（全部经 NavServiceFactory，架构关键规则）——

    /**
     * 在线服务装配（幂等）：首启 AMS 鉴权异步，模式可能短暂为 OnboardOnly；模式切到 OnlineFirst 时由
     * observeServiceMode 再次调用补装。NavSdk 初始化后，构建 Compose 地图基础设施（需 sdkContext）。
     */
    private fun ensureOnlineServices() {
        if (navEngine != null) return
        if (container.modeController.mode.value != ServiceMode.ONLINE_FIRST) return
        val factory = container.navServiceFactory
        runCatching {
            // 路径规划 Routing 域
            routingService = factory.createRoutingService()
            // 导航引导 Navigation 域（含 GuidanceBus 接线）—— 内部完成 NavSdk/TomTomSdk 初始化
            navEngine = factory.createNavigationEngine(container.telemetryManager, container.guidanceBus)
                .also { engine ->
                    // 语音播报 TTS 域：引导语言
                    factory.tts.applyLanguage(engine.navigation, Locale.US)
                }
            // SDK 已就绪：定位 + Compose 地图基础设施
            val provider = NavSdk.locationProvider
            appLocationProvider = provider
            if (hasLocationPermissions()) {
                provider.enable()
                provider.lastKnownLocation?.position?.let { initialCenter = it }
                centerOnFirstFix(provider)
            }
            buildMapInfrastructure(provider)
            pendingDestination?.let {
                pendingDestination = null
                planRouteTo(it)
            }
        }.onFailure { e ->
            toast(e.message ?: getString(R.string.onboard_only_hint))
        }
    }

    /** 声明式地图所需的两套基础设施：地图显示（含定位源）+ 路线/导航可视化（预览路线 + 主动导航）。 */
    private fun buildMapInfrastructure(provider: LocationProvider) {
        mapInfra = MapDisplayInfrastructure(sdkContext = TomTomSdk.sdkContext) {
            locationInfrastructure = MapLocationInfrastructure { locationProvider = provider }
        }
        navVizInfra = NavigationVisualizationInfrastructure(
            // 预览：把算路结果喂给可视化即绘制（取代命令式 addRoute）
            routingVisualizationDataProvider = flowOf(
                RoutingVisualizationDataProvider(routes = routesFlow, selectedRouteId = selectedRouteIdFlow),
            ),
            // 主动导航：跟随 TomTomNavigation 进度绘制车标/主动路线
            navigationVisualizationDataProvider = flowOf(
                NavigationVisualizationDataProvider(tomtomNavigation = NavSdk.navigation),
            ),
        )
    }

    private fun observeServiceMode() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.modeController.mode.collect { mode ->
                    searchViewModel.onServiceModeChanged()
                    // 首启鉴权完成（或恢复授权）后补装在线服务（横幅文案在 Compose 端按模式渲染）
                    if (mode == ServiceMode.ONLINE_FIRST) ensureOnlineServices()
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                searchViewModel.errors.collect { toast(it) }
            }
        }
    }

    /** 语音播报 TTS 域：朗读引导文本（开关在 TtsService 内判断）。 */
    private fun observeGuidanceAnnouncements() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.guidanceBus.announcements.collect { container.navServiceFactory.tts.speak(it) }
            }
        }
    }

    // —— 搜索 Search 域（UI 见 feature/search/SearchPanel）——

    private fun onResultClick(item: SearchViewModel.SearchItem) {
        searchViewModel.recordSelection(item)
        searchViewModel.clearResults()
        planRouteTo(item.position)
    }

    // —— 路径规划 Routing 域 ——

    private fun planRouteTo(destination: GeoPoint) {
        val routing = routingService ?: run {
            toast(getString(R.string.onboard_only_hint))
            return
        }
        val prefs = container.settingsRepository.routePreferences.value
        routing.planRoutes(
            origin = appLocationProvider?.lastKnownLocation?.position ?: DEFAULT_CENTER,
            destination = destination,
            preferences = RoutePreferences(
                avoidTolls = prefs.avoidTolls,
                avoidMotorways = prefs.avoidMotorways,
                maxAlternatives = MAX_ALTERNATIVES,
            ),
            // EV 算路：profile 见 core/sdk/ev（EV 专题页）
            vehicle = container.evProfileProvider.vehicle(evMode = prefs.evRouting),
            onResult = { routes, options ->
                plannedRoutes = routes
                routePlanningOptions = options
                // 声明式绘制：更新数据源即由 NavigationVisualization 渲染
                routesFlow.value = routes
                selectedRouteIdFlow.value = routes.firstOrNull()?.id
                cameraTrackingMode = CameraTrackingMode.RouteOverview
                hasRoute = true
                toast(getString(R.string.tap_route_to_navigate))
            },
            onError = { message -> toast(message) },
        )
    }

    // —— 导航引导 Navigation 域 ——

    /** 平移地图即解除跟随（浏览），并显示统一的"回中"按钮（浏览/导航两态共用）。 */
    private fun onMapPanning() {
        cameraTrackingMode = CameraTrackingMode.None
        showRecenter = true
    }

    private fun startNavigation(route: Route) {
        val engine = navEngine ?: return
        val options = routePlanningOptions ?: return
        // 引导数据分发（仪表/Widget/ISA/自绘面板）先接通，再启动引擎
        engine.onNavigationStarted()
        engine.navigation.start(NavigationOptions(RoutePlan(route, options)))
        // Demo 模拟行驶（实车联调删除，直接消费 GPS/融合定位）：
        // 相机 FollowRouteDirection 与 NavigationVisualization 跟随导航引擎进度，故只需切换导航定位源
        val sim = container.navServiceFactory.locationEngine.simulation(route)
        simulationProvider = sim
        engine.navigation.locationProvider = sim
        sim.enable()
        cameraTrackingMode = CameraTrackingMode.FollowRouteDirection
        // location 型前台 Service 在 API 34+ 同样要求定位权限；拒绝授权时跳过（仅失去后台保活）
        if (hasLocationPermissions()) NavigationForegroundService.start(this)
        isNavigating = true
        hasRoute = false
        showRecenter = false
    }

    private fun stopNavigation() {
        val engine = navEngine
        engine?.navigation?.stop()
        engine?.onNavigationStopped()
        NavigationForegroundService.stop(this)
        // 还原导航定位源为默认 GPS，关闭模拟
        appLocationProvider?.let { engine?.navigation?.locationProvider = it }
        simulationProvider?.close()
        simulationProvider = null
        // 清空预览路线
        routesFlow.value = emptyList()
        selectedRouteIdFlow.value = null
        plannedRoutes = emptyList()
        isNavigating = false
        hasRoute = false
        showRecenter = false
        cameraTrackingMode = CameraTrackingMode.None
        appLocationProvider?.lastKnownLocation?.position?.let {
            cameraTarget = CameraOptions(position = it, zoom = DEFAULT_ZOOM)
        }
    }

    /** 统一回中：导航中恢复跟路视角；非导航时把相机移回当前位置。 */
    private fun recenter() {
        if (isNavigating) {
            cameraTrackingMode = CameraTrackingMode.FollowRouteDirection
        } else {
            cameraTrackingMode = CameraTrackingMode.None
            appLocationProvider?.lastKnownLocation?.position?.let {
                cameraTarget = CameraOptions(position = it, zoom = DEFAULT_ZOOM)
            }
        }
        showRecenter = false
    }

    // —— 权限 / 定位 ——

    /** 启动即发起授权（首装路径）；地图就绪与授权完成的先后顺序均已覆盖。 */
    private fun requestLocationPermissionsIfNeeded() {
        if (hasLocationPermissions()) return
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        locationPermissionRequest.launch(permissions.toTypedArray())
    }

    /** 首个定位到来时把相机移到当前位置（一次性）。 */
    private fun centerOnFirstFix(provider: LocationProvider) {
        onLocationUpdateListener?.let { provider.removeOnLocationUpdateListener(it) }
        val listener = object : OnLocationUpdateListener {
            override fun onLocationUpdate(location: com.tomtom.sdk.location.GeoLocation) {
                if (!isNavigating && !showRecenter) {
                    cameraTarget = CameraOptions(position = location.position, zoom = DEFAULT_ZOOM)
                }
                provider.removeOnLocationUpdateListener(this)
            }
        }
        onLocationUpdateListener = listener
        provider.addOnLocationUpdateListener(listener)
    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            appLocationProvider?.let { provider ->
                provider.enable()
                centerOnFirstFix(provider)
            }
        } else {
            toast(getString(R.string.location_permission_denied))
        }
    }

    private fun hasLocationPermissions(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    // —— 杂项 ——

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        onLocationUpdateListener?.let { appLocationProvider?.removeOnLocationUpdateListener(it) }
        container.navServiceFactory.tts.shutdown()
        simulationProvider?.close()
        navEngine?.close()
        // 与 enable() 配平：销毁时停用定位，避免 GPS/传感器后台空转耗电
        appLocationProvider?.disable()
        super.onDestroy()
    }

    private companion object {
        /** 演示用初始位置（悉尼）。 */
        val DEFAULT_CENTER = GeoPoint(-33.8688, 151.2093)
        const val DEFAULT_ZOOM = 14.0

        /** 仅规划主路线（不出备选）。 */
        const val MAX_ALTERNATIVES = 0
    }
}
