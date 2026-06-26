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
import com.tomtom.sdk.map.display.location.LocationMarkerOptions
import com.tomtom.sdk.map.display.route.Instruction
import com.tomtom.sdk.map.display.route.RouteClickListener
import com.tomtom.sdk.map.display.route.RouteOptions
import com.tomtom.sdk.map.display.ui.MapFragment
import com.tomtom.sdk.map.display.ui.currentlocation.CurrentLocationButton.VisibilityPolicy
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

    private lateinit var mapFragment: MapFragment
    private var tomTomMap: TomTomMap? = null
    private lateinit var locationProvider: LocationProvider
    private lateinit var onLocationUpdateListener: OnLocationUpdateListener
    private var routingService: RoutingService? = null
    private var navEngine: NavigationEngine? = null
    private var plannedRoutes: List<Route> = emptyList()
    private var routePlanningOptions: RoutePlanningOptions? = null
    private var pendingDestination: GeoPoint? = null

    @Suppress("DEPRECATION")
    private var navigationFragment: com.tomtom.sdk.navigation.ui.NavigationFragment? = null

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
        observeServiceMode()
        initLocation()
        ensureOnlineServices()
        initMap()
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
                    // 路况层为在线服务，模式切换时同步开关
                    tomTomMap?.let {
                        container.navServiceFactory.trafficLayers.setEnabled(it, mode == ServiceMode.ONLINE_FIRST)
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

    private fun initMap() {
        mapFragment = MapFragment.newInstance(
            container.navServiceFactory.mapDisplay.mapOptions(initialCenter = DEFAULT_CENTER),
        )
        supportFragmentManager.beginTransaction()
            .replace(R.id.map_container, mapFragment)
            .commit()
        mapFragment.getMapAsync { map ->
            tomTomMap = map
            enableUserLocation()
            map.addRouteClickListener(routeClickListener)
            // 实时路况 Traffic 域：地图路况层（仅 OnlineFirst）
            container.navServiceFactory.trafficLayers.setEnabled(
                map,
                container.modeController.mode.value == ServiceMode.ONLINE_FIRST,
            )
            pendingDestination?.let {
                pendingDestination = null
                planRouteTo(it)
            }
        }
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

    private val routeClickListener = RouteClickListener { clicked ->
        val engine = navEngine ?: return@RouteClickListener
        if (!engine.isNavigating) {
            // 点击哪条（含备选）就用哪条导航
            plannedRoutes.firstOrNull { it.id.toString() == clicked.tag }?.let { startNavigation(it) }
        }
    }

    private fun startNavigation(route: Route) {
        val engine = navEngine ?: return
        val options = routePlanningOptions ?: return
        initNavigationFragment()
        @Suppress("DEPRECATION")
        navigationFragment?.let { fragment ->
            fragment.setTomTomNavigation(engine.navigation)
            fragment.startNavigation(RoutePlan(route, options))
            fragment.addNavigationListener(navigationListener)
        }
        // 引导数据分发（仪表/Widget/ISA）+ 前台 Service（后台运行，feature/guidance）
        engine.onNavigationStarted()
        // location 型前台 Service 在 API 34+ 同样要求定位权限；拒绝授权时跳过（仅失去后台保活）
        if (hasLocationPermissions()) NavigationForegroundService.start(this)
        mapFragment.currentLocationButton.visibilityPolicy = VisibilityPolicy.Invisible
        binding.searchCard.isVisible = false
    }

    @Suppress("DEPRECATION")
    private val navigationListener =
        object : com.tomtom.sdk.navigation.ui.NavigationFragment.NavigationListener {
            @Deprecated("This will be removed from future releases after 2026-07-26.")
            override fun onStarted() {
                tomTomMap?.apply {
                    cameraTrackingMode = CameraTrackingMode.FollowRouteDirection
                    enableLocationMarker(LocationMarkerOptions(LocationMarkerOptions.Type.Chevron))
                    setPadding(Padding(0, 0, 0, resources.getDimensionPixelOffset(R.dimen.map_padding_bottom)))
                }
                plannedRoutes.firstOrNull()?.let { useSimulationLocationProvider(it) }
            }

            @Deprecated("This will be removed from future releases after 2026-07-26.")
            override fun onStopped() {
                stopNavigation()
            }
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
        @Suppress("DEPRECATION")
        navigationFragment?.let { fragment ->
            fragment.stopNavigation()
            fragment.removeNavigationListener(navigationListener)
        }
        navEngine?.onNavigationStopped()
        NavigationForegroundService.stop(this)
        mapFragment.currentLocationButton.visibilityPolicy = VisibilityPolicy.InvisibleWhenRecentered
        tomTomMap?.apply {
            cameraTrackingMode = CameraTrackingMode.None
            enableLocationMarker(LocationMarkerOptions(LocationMarkerOptions.Type.Pointer))
            setPadding(Padding(0, 0, 0, 0))
            clear()
        }
        binding.searchCard.isVisible = true
        plannedRoutes = emptyList()
        locationProvider = container.navServiceFactory.locationEngine.gps()
        if (hasLocationPermissions()) locationProvider.enable()
        navEngine?.navigation?.locationProvider = locationProvider
        enableUserLocation()
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

    private fun initNavigationFragment() {
        if (navigationFragment == null) {
            @Suppress("DEPRECATION")
            navigationFragment = com.tomtom.sdk.navigation.ui.NavigationFragment.newInstance(
                com.tomtom.sdk.navigation.ui.NavigationUiOptions(
                    keepInBackground = true,
                    // 语音播报 TTS 域：开关来自设置中心
                    isSoundEnabled = container.navServiceFactory.tts.voiceGuidanceEnabled,
                ),
            )
        }
        navigationFragment?.let {
            if (!it.isAdded) {
                supportFragmentManager.beginTransaction()
                    .add(R.id.navigation_fragment_container, it)
                    .commitNow()
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        tomTomMap?.setLocationProvider(null)
        navigationFragment?.let {
            supportFragmentManager.beginTransaction().remove(it).commitNowAllowingStateLoss()
        }
        navEngine?.close()
        locationProvider.close()
        super.onDestroy()
    }

    private companion object {
        /** 演示用初始位置（悉尼（演示用初始位置））。 */
        val DEFAULT_CENTER = GeoPoint(-33.8688, 151.2093)
        const val DEFAULT_ZOOM = 12.0
        const val ROUTE_PADDING_PX = 100
        const val MAX_ALTERNATIVES = 2
    }
}
