package com.tomtom.demo.nav.feature.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.tomtom.demo.nav.App
import com.tomtom.demo.nav.R
import com.tomtom.demo.nav.databinding.ActivityMainBinding
import com.tomtom.demo.nav.feature.guidance.NavigationForegroundService
import com.tomtom.demo.nav.feature.search.SearchResultAdapter
import com.tomtom.demo.nav.feature.search.SearchViewModel
import com.tomtom.demo.nav.feature.settings.SettingsActivity
import com.tomtom.demo.nav.core.sdk.ServiceMode
import com.tomtom.demo.nav.core.sdk.navigation.NavigationEngine
import com.tomtom.demo.nav.core.sdk.routing.RoutePreferences
import com.tomtom.demo.nav.core.sdk.routing.RoutingService
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
 * 各能力域的代码位置（对应 Workshop《模块总览》页，详见 :core:sdk NavServiceFactory 注释表）：
 * 搜索 → feature/search + core/sdk/search；算路 → core/sdk/routing；引导 → core/sdk/navigation；
 * 路况层 → core/sdk/traffic；TTS → core/sdk/tts；离线数据 → core/sdk/mapdata；
 * 多屏/ISA 消费 → feature/guidance（前台 Service）。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

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

    private val resultAdapter = SearchResultAdapter(
        onClick = { item ->
            binding.searchResults.isVisible = false
            binding.searchInput.clearFocus()
            searchViewModel.recordSelection(item)
            searchViewModel.clearResults()
            tomTomMap?.clear()
            planRouteTo(item.position)
        },
        onFavorite = { item ->
            searchViewModel.addFavorite(item)
            toast(getString(R.string.favorite_added))
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initSearchUi()
        initNavigationUi()
        observeServiceMode()
        initLocation()
        ensureOnlineServices()
        initMap(savedInstanceState)
        requestLocationPermissionsIfNeeded()
        handleDeepLink(intent)
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
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
                    when (mode) {
                        ServiceMode.ONLINE_FIRST -> {
                            binding.modeBanner.text = getString(R.string.mode_online_first)
                            binding.modeBanner.setBackgroundResource(R.color.mode_online)
                        }
                        ServiceMode.ONBOARD_ONLY -> {
                            binding.modeBanner.text = getString(R.string.mode_onboard_only)
                            binding.modeBanner.setBackgroundResource(R.color.mode_onboard)
                        }
                    }
                    searchViewModel.onServiceModeChanged()
                    // 首启鉴权完成（或恢复授权）后补装在线服务
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

    // —— 地图显示 Map Display 域 ——

    private fun initMap(savedInstanceState: Bundle?) {
        // 自定义 MapView（不使用 MapFragment）：程序化创建 + Activity 生命周期转发
        mapView = MapView(
            this,
            container.navServiceFactory.mapDisplay.mapOptions(initialCenter = DEFAULT_CENTER),
        )
        binding.mapContainer.addView(mapView)
        mapView.onCreate(savedInstanceState)
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

    // —— 搜索 Search 域（UI 部分见 feature/search）——

    private fun initSearchUi() {
        binding.searchResults.layoutManager = LinearLayoutManager(this)
        binding.searchResults.adapter = resultAdapter
        binding.searchInput.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchViewModel.search(
                    query = v.text.toString(),
                    bias = tomTomMap?.currentLocation?.position ?: DEFAULT_CENTER,
                )
                true
            } else {
                false
            }
        }
        binding.searchInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && binding.searchInput.text.isNullOrBlank()) {
                showSavedList()
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                searchViewModel.results.collect { items ->
                    if (items.isNotEmpty()) {
                        resultAdapter.submitList(items)
                        binding.searchResults.isVisible = true
                    } else if (!binding.searchInput.hasFocus()) {
                        binding.searchResults.isVisible = false
                    }
                }
            }
        }
    }

    private fun showSavedList() {
        val saved = searchViewModel.saved.value
        resultAdapter.submitList(saved)
        binding.searchResults.isVisible = saved.isNotEmpty()
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
                binding.driveButton.isVisible = true
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

    /**
     * 自绘引导面板装配：开始/结束按钮 + 订阅 GuidanceBus 快照渲染面板 + 语音播报。
     * 面板与播报都只消费 GuidanceBus（[GuidanceSnapshot] / announcements），不依赖 SDK 导航 UI 组件。
     */
    /** 平移地图即解除跟随（浏览），并显示统一的"回中"按钮（浏览/导航两态共用）。 */
    private val mapPanningListener = object : MapPanningListener {
        override fun onMapPanningStarted() {
            tomTomMap?.cameraTrackingMode = CameraTrackingMode.None
            binding.recenterButton.isVisible = true
        }

        override fun onMapPanningOngoing() = Unit
        override fun onMapPanningEnded() = Unit
    }

    private fun initNavigationUi() {
        binding.navigationView.onStop = { stopNavigation() }
        // 统一的"回中"按钮：导航中恢复跟路视角，非导航时回到当前位置
        binding.recenterButton.setOnClickListener { recenter() }
        // 规划完成后点"开始导航"按钮发车（不再点地图上的路线）
        binding.driveButton.setOnClickListener {
            plannedRoutes.firstOrNull()?.let { startNavigation(it) }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.guidanceBus.snapshot.collect { binding.navigationView.render(it) }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 语音播报 TTS 域：朗读引导文本（开关在 TtsService 内判断）
                container.guidanceBus.announcements.collect { container.navServiceFactory.tts.speak(it) }
            }
        }
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
        // 导航期间隐藏顶部模式横幅 / 搜索框 / 设置按钮，避免遮挡引导面板
        binding.modeBanner.isVisible = false
        binding.settingsButton.isVisible = false
        binding.searchCard.isVisible = false
        binding.driveButton.isVisible = false
        binding.recenterButton.isVisible = false
        binding.navigationView.show()
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
        binding.navigationView.hide()
        tomTomMap?.apply {
            cameraTrackingMode = CameraTrackingMode.None
            enableLocationMarker(LocationMarkerOptions(LocationMarkerOptions.Type.Pointer))
            setPadding(Padding(0, 0, 0, 0))
            clear()
        }
        binding.modeBanner.isVisible = true
        binding.settingsButton.isVisible = true
        binding.searchCard.isVisible = true
        binding.driveButton.isVisible = false
        binding.recenterButton.isVisible = false
        plannedRoutes = emptyList()
        locationProvider = container.navServiceFactory.locationEngine.gps()
        if (hasLocationPermissions()) locationProvider.enable()
        navEngine?.navigation?.locationProvider = locationProvider
        enableUserLocation()
    }

    /** 统一回中：导航中恢复跟路视角；非导航时把相机移回当前位置。 */
    private fun recenter() {
        val map = tomTomMap ?: return
        if (binding.navigationView.isVisible) {
            map.cameraTrackingMode = CameraTrackingMode.FollowRouteDirection
        } else {
            map.currentLocation?.position?.let { map.animateCamera(CameraOptions(it, zoom = DEFAULT_ZOOM)) }
        }
        binding.recenterButton.isVisible = false
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
        tomTomMap?.setLocationProvider(null)
        container.navServiceFactory.tts.shutdown()
        navEngine?.close()
        locationProvider.close()
        if (::mapView.isInitialized) mapView.onDestroy()
        super.onDestroy()
    }

    private companion object {
        /** 演示用初始位置（悉尼（演示用初始位置））。 */
        val DEFAULT_CENTER = GeoPoint(-33.8688, 151.2093)
        const val DEFAULT_ZOOM = 12.0
        const val ROUTE_PADDING_PX = 100

        /** 仅规划主路线（不出备选）。 */
        const val MAX_ALTERNATIVES = 0
    }
}
