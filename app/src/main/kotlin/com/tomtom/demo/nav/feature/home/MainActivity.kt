package com.tomtom.demo.nav.feature.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
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
import androidx.core.view.isVisible
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
import com.tomtom.sdk.location.GeoPoint
import com.tomtom.sdk.location.LocationProvider
import com.tomtom.sdk.location.OnLocationUpdateListener
import com.tomtom.sdk.map.display.TomTomMap
import com.tomtom.sdk.map.display.camera.CameraOptions
import com.tomtom.sdk.map.display.camera.CameraTrackingMode
import com.tomtom.sdk.map.display.common.screen.Padding
import com.tomtom.sdk.map.display.gesture.MapPanningListener
import com.tomtom.sdk.map.display.ui.compass.CompassButton
import com.tomtom.sdk.map.display.location.LocationMarkerOptions
import com.tomtom.sdk.map.display.route.Instruction
import com.tomtom.sdk.map.display.route.RouteOptions
import com.tomtom.sdk.map.display.ui.MapView
import com.tomtom.sdk.map.display.ui.currentlocation.CurrentLocationButton.VisibilityPolicy
import com.tomtom.sdk.navigation.NavigationOptions
import com.tomtom.sdk.navigation.RoutePlan
import com.tomtom.sdk.routing.options.RoutePlanningOptions
import com.tomtom.sdk.routing.route.Route
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * ［feature:home］导航主屏：地图宿主 + 路线展示 + 引导宿主。
 *
 * UI 体系为 Compose（见 [MainScreen]），但架构未变：地图仍是命令式 [MapView]（其生命周期由本
 * Activity 转发），算路 / 引导 / 定位编排全部留在此处，经 NavServiceFactory 装配；Compose 仅渲染
 * 状态并回调。各能力域代码位置见 :core:sdk NavServiceFactory 注释表。
 */
class MainActivity : ComponentActivity() {

    private val container by lazy { (application as App).container }
    private val searchViewModel: SearchViewModel by viewModels {
        SearchViewModel.factory(container.navServiceFactory, container.placesRepository)
    }

    private lateinit var mapView: MapView
    private var tomTomMap: TomTomMap? = null
    private lateinit var locationProvider: LocationProvider
    private lateinit var onLocationUpdateListener: OnLocationUpdateListener
    private var routingService: RoutingService? = null
    private var navEngine: NavigationEngine? = null
    private var plannedRoutes: List<Route> = emptyList()
    private var routePlanningOptions: RoutePlanningOptions? = null
    private var pendingDestination: GeoPoint? = null

    // —— Compose UI 状态（取代原 View 的可见性切换）——
    private var hasRoute by mutableStateOf(false)
    private var isNavigating by mutableStateOf(false)
    private var showRecenter by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 自定义 MapView（不使用 MapFragment）：程序化创建 + Activity 生命周期转发；交给 Compose 内嵌渲染
        mapView = MapView(
            this,
            container.navServiceFactory.mapDisplay.mapOptions(initialCenter = DEFAULT_CENTER),
        )
        mapView.onCreate(savedInstanceState)
        initMapAsync()

        observeServiceMode()
        initLocation()
        ensureOnlineServices()
        observeGuidanceAnnouncements()
        requestLocationPermissionsIfNeeded()
        handleDeepLink(intent)

        setContent {
            TomTomNavDemoTheme {
                MainScreen(
                    mapView = mapView,
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
                            bias = tomTomMap?.currentLocation?.position ?: DEFAULT_CENTER,
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
        if (tomTomMap != null) {
            tomTomMap?.clear()
            planRouteTo(destination)
        } else {
            pendingDestination = destination
        }
    }

    // —— 服务装配（全部经 NavServiceFactory，架构关键规则）——

    private fun initLocation() {
        // 定位 Location 域；enable() 注册系统定位，必须等运行时权限到位（量产车机通常预授）
        locationProvider = container.navServiceFactory.locationEngine.gps()
        if (hasLocationPermissions()) locationProvider.enable()
    }

    /**
     * 在线服务装配（幂等）：首启时 AMS 鉴权是异步的，模式可能短暂为 OnboardOnly；
     * 模式切到 OnlineFirst 时由 observeServiceMode 再次调用本方法补装 —— 即"模式驱动重建"。
     */
    private fun ensureOnlineServices() {
        if (navEngine != null) return
        if (container.modeController.mode.value != ServiceMode.ONLINE_FIRST) return
        val factory = container.navServiceFactory
        runCatching {
            // 路径规划 Routing 域
            routingService = factory.createRoutingService()
            // 导航引导 Navigation 域（含 GuidanceBus 接线）
            navEngine = factory.createNavigationEngine(container.telemetryManager, container.guidanceBus)
                .also { engine ->
                    engine.navigation.locationProvider = locationProvider
                    // 语音播报 TTS 域：引导语言
                    factory.tts.applyLanguage(engine.navigation, Locale.US)
                }
        }.onFailure { e ->
            toast(e.message ?: getString(R.string.onboard_only_hint))
        }
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

    // —— 地图显示 Map Display 域 ——

    private fun initMapAsync() {
        mapView.getMapAsync { map ->
            tomTomMap = map
            enableUserLocation()
            // 默认隐藏路况流（用户偏好）；TrafficLayerService 仍保留，需要时再开
            container.navServiceFactory.trafficLayers.setEnabled(map, false)
            // 隐藏 MapView 自带的 UI 组件（指南针 / 定位 / 比例尺）——本工程用自绘控件，
            // 仅保留必须的 TomTom logo。注意：这些组件属于 MapView（与 MapFragment 同源），非 MapFragment 专有。
            mapView.compassButton.visibilityPolicy = CompassButton.VisibilityPolicy.Invisible
            mapView.currentLocationButton.visibilityPolicy = VisibilityPolicy.Invisible
            mapView.scaleView.isVisible = false
            // 用户平移即解除相机跟随（可自由浏览），导航中显示自绘"回中"按钮
            map.addMapPanningListener(mapPanningListener)
            pendingDestination?.let {
                pendingDestination = null
                planRouteTo(it)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::mapView.isInitialized) mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        if (::mapView.isInitialized) mapView.onResume()
    }

    override fun onPause() {
        if (::mapView.isInitialized) mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        if (::mapView.isInitialized) mapView.onStop()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::mapView.isInitialized) mapView.onSaveInstanceState(outState)
    }

    // —— 搜索 Search 域（UI 见 feature/search/SearchPanel）——

    private fun onResultClick(item: SearchViewModel.SearchItem) {
        searchViewModel.recordSelection(item)
        searchViewModel.clearResults()
        tomTomMap?.clear()
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
            origin = tomTomMap?.currentLocation?.position ?: DEFAULT_CENTER,
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
                routes.forEachIndexed { index, route -> drawRoute(route, primary = index == 0) }
                tomTomMap?.zoomToRoutes(ROUTE_PADDING_PX)
                hasRoute = true
                toast(getString(R.string.tap_route_to_navigate))
            },
            onError = { message -> toast(message) },
        )
    }

    private fun drawRoute(route: Route, primary: Boolean) {
        val map = tomTomMap ?: return
        val instructions = route.legs
            .flatMap { it.instructions }
            .map { Instruction(routeOffset = it.routeOffset) }
        map.addRoute(
            RouteOptions(
                geometry = route.geometry,
                destinationMarkerVisible = primary,
                departureMarkerVisible = primary,
                instructions = instructions,
                routeOffset = route.routePoints.map { it.routeOffset },
                color = if (primary) RouteOptions.DEFAULT_COLOR else Color.GRAY,
                tag = route.id.toString(),
            ),
        )
    }

    // —— 导航引导 Navigation 域 ——

    /** 平移地图即解除跟随（浏览），并显示统一的"回中"按钮（浏览/导航两态共用）。 */
    private val mapPanningListener = object : MapPanningListener {
        override fun onMapPanningStarted() {
            tomTomMap?.cameraTrackingMode = CameraTrackingMode.None
            showRecenter = true
        }

        override fun onMapPanningOngoing() = Unit
        override fun onMapPanningEnded() = Unit
    }

    private fun startNavigation(route: Route) {
        val engine = navEngine ?: return
        val options = routePlanningOptions ?: return
        // 引导数据分发（仪表/Widget/ISA/自绘面板）先接通，再启动引擎
        engine.onNavigationStarted()
        engine.navigation.start(NavigationOptions(RoutePlan(route, options)))
        // 跟随视角 + 车标（Chevron）+ 底部留白，给引导面板让位
        tomTomMap?.apply {
            cameraTrackingMode = CameraTrackingMode.FollowRouteDirection
            enableLocationMarker(LocationMarkerOptions(LocationMarkerOptions.Type.Chevron))
            setPadding(Padding(0, 0, 0, resources.getDimensionPixelOffset(R.dimen.map_padding_bottom)))
        }
        // Demo 模拟行驶（实车联调删除，直接消费 GPS/融合定位）
        useSimulationLocationProvider(route)
        // location 型前台 Service 在 API 34+ 同样要求定位权限；拒绝授权时跳过（仅失去后台保活）
        if (hasLocationPermissions()) NavigationForegroundService.start(this)
        // 导航期间隐藏浏览态 chrome（模式横幅 / 搜索框 / 设置 / 发车按钮），由 Compose 据状态切换
        isNavigating = true
        hasRoute = false
        showRecenter = false
    }

    /** Demo 用模拟行驶（定位 Location 域）；实车联调时删除，导航直接消费 GPS/融合定位。 */
    private fun useSimulationLocationProvider(route: Route) {
        val engine = navEngine ?: return
        val old = engine.navigation.locationProvider
        locationProvider = container.navServiceFactory.locationEngine.simulation(route)
        engine.navigation.locationProvider = locationProvider
        tomTomMap?.setLocationProvider(locationProvider)
        old.close()
        locationProvider.enable()
    }

    private fun stopNavigation() {
        navEngine?.navigation?.stop()
        navEngine?.onNavigationStopped()
        NavigationForegroundService.stop(this)
        tomTomMap?.apply {
            cameraTrackingMode = CameraTrackingMode.None
            enableLocationMarker(LocationMarkerOptions(LocationMarkerOptions.Type.Pointer))
            setPadding(Padding(0, 0, 0, 0))
            clear()
        }
        isNavigating = false
        hasRoute = false
        showRecenter = false
        plannedRoutes = emptyList()
        locationProvider = container.navServiceFactory.locationEngine.gps()
        if (hasLocationPermissions()) locationProvider.enable()
        navEngine?.navigation?.locationProvider = locationProvider
        enableUserLocation()
    }

    /** 统一回中：导航中恢复跟路视角；非导航时把相机移回当前位置。 */
    private fun recenter() {
        val map = tomTomMap ?: return
        if (isNavigating) {
            map.cameraTrackingMode = CameraTrackingMode.FollowRouteDirection
        } else {
            map.currentLocation?.position?.let { map.animateCamera(CameraOptions(it, zoom = DEFAULT_ZOOM)) }
        }
        showRecenter = false
    }

    // —— 权限 ——

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

    private fun enableUserLocation() {
        if (hasLocationPermissions()) showUserLocation()
    }

    private fun showUserLocation() {
        val map = tomTomMap ?: return
        // 避免重复注册：定位到来前若多次调用（授权回调 / 结束导航后回中），先移除上一个一次性监听
        if (::onLocationUpdateListener.isInitialized) {
            locationProvider.removeOnLocationUpdateListener(onLocationUpdateListener)
        }
        onLocationUpdateListener = OnLocationUpdateListener { location ->
            map.moveCamera(CameraOptions(location.position, zoom = DEFAULT_ZOOM))
            locationProvider.removeOnLocationUpdateListener(onLocationUpdateListener)
        }
        locationProvider.addOnLocationUpdateListener(onLocationUpdateListener)
        map.setLocationProvider(locationProvider)
        map.enableLocationMarker(LocationMarkerOptions(type = LocationMarkerOptions.Type.Pointer))
    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            locationProvider.enable()
            showUserLocation()
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
        tomTomMap?.removeMapPanningListener(mapPanningListener)
        tomTomMap?.setLocationProvider(null)
        container.navServiceFactory.tts.shutdown()
        navEngine?.close()
        locationProvider.close()
        if (::mapView.isInitialized) mapView.onDestroy()
        super.onDestroy()
    }

    private companion object {
        /** 演示用初始位置（悉尼）。 */
        val DEFAULT_CENTER = GeoPoint(-33.8688, 151.2093)
        const val DEFAULT_ZOOM = 12.0
        const val ROUTE_PADDING_PX = 100

        /** 仅规划主路线（不出备选）。 */
        const val MAX_ALTERNATIVES = 0
    }
}
