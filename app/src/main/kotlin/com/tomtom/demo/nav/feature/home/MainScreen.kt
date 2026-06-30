package com.tomtom.demo.nav.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomtom.demo.nav.R
import com.tomtom.demo.nav.core.sdk.ServiceMode
import com.tomtom.demo.nav.core.sdk.guidance.GuidanceSnapshot
import com.tomtom.demo.nav.feature.guidance.NavigationOverlay
import com.tomtom.demo.nav.feature.search.SearchPanel
import com.tomtom.demo.nav.feature.search.SearchViewModel
import com.tomtom.demo.nav.ui.theme.NavColors
import com.tomtom.sdk.location.GeoPoint
import com.tomtom.sdk.map.display.camera.CameraOptions
import com.tomtom.sdk.map.display.camera.CameraTrackingMode
import com.tomtom.sdk.map.display.camera.InitialCameraOptions
import com.tomtom.sdk.map.display.compose.TomTomMap
import com.tomtom.sdk.map.display.compose.model.MapDisplayInfrastructure
import com.tomtom.sdk.map.display.compose.nodes.CurrentLocationMarker
import com.tomtom.sdk.map.display.compose.properties.CurrentLocationMarkerProperties
import com.tomtom.sdk.map.display.compose.state.rememberMapViewState
import com.tomtom.sdk.map.display.location.LocationMarkerOptions
import com.tomtom.sdk.map.display.visualization.navigation.annotations.BetaHorizonVisualizationApi
import com.tomtom.sdk.map.display.visualization.navigation.compose.BetterRouteVisualization
import com.tomtom.sdk.map.display.visualization.navigation.compose.HorizonVisualization
import com.tomtom.sdk.map.display.visualization.navigation.compose.NavigationVisualization
import com.tomtom.sdk.map.display.visualization.navigation.compose.model.NavigationVisualizationInfrastructure
import com.tomtom.sdk.map.display.visualization.navigation.compose.state.rememberBetterRouteVisualizationState
import com.tomtom.sdk.map.display.visualization.routing.compose.TrafficVisualization
import com.tomtom.sdk.map.display.visualization.routing.compose.state.rememberTrafficVisualizationState
import kotlinx.coroutines.flow.StateFlow

private typealias SearchItem = SearchViewModel.SearchItem

/**
 * ［feature:home］导航主屏（Compose）：声明式 TomTomMap + 浏览态搜索/设置 chrome + 导航态自绘引导面板。
 *
 * 地图为 TomTom 声明式 Compose 地图：路线预览由 [navVizInfra] 的路由可视化数据源驱动绘制，主动导航的
 * 车标/主动路线由导航可视化数据源驱动；相机经 [cameraTrackingMode] / [cameraTarget] 状态驱动。
 * 基础设施（[mapInfra]）需 SDK 就绪才非空，就绪前显示加载态。算路/引导编排在 MainActivity。
 */
@Composable
fun MainScreen(
    mapInfra: MapDisplayInfrastructure?,
    navVizInfra: NavigationVisualizationInfrastructure?,
    initialCenter: GeoPoint,
    cameraTrackingMode: CameraTrackingMode,
    cameraTarget: CameraOptions?,
    serviceModeFlow: StateFlow<ServiceMode>,
    snapshotFlow: StateFlow<GuidanceSnapshot>,
    resultsFlow: StateFlow<List<SearchItem>>,
    savedFlow: StateFlow<List<SearchItem>>,
    hasRoute: Boolean,
    isNavigating: Boolean,
    showRecenter: Boolean,
    onSearch: (String) -> Unit,
    onResultClick: (SearchItem) -> Unit,
    onFavorite: (SearchItem) -> Unit,
    onClearResults: () -> Unit,
    onDrive: () -> Unit,
    onRecenter: () -> Unit,
    onStopNav: () -> Unit,
    onOpenSettings: () -> Unit,
    onMapPanning: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (mapInfra != null && navVizInfra != null) {
            MapHost(
                mapInfra = mapInfra,
                navVizInfra = navVizInfra,
                initialCenter = initialCenter,
                cameraTrackingMode = cameraTrackingMode,
                cameraTarget = cameraTarget,
                isNavigating = isNavigating,
                onMapPanning = onMapPanning,
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(text = stringResource(R.string.map_initializing))
                }
            }
        }

        if (isNavigating) {
            val snapshot by snapshotFlow.collectAsStateWithLifecycle()
            NavigationOverlay(snapshot = snapshot, onStop = onStopNav)
        } else {
            val serviceMode by serviceModeFlow.collectAsStateWithLifecycle()
            val results by resultsFlow.collectAsStateWithLifecycle()
            val saved by savedFlow.collectAsStateWithLifecycle()

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding(),
            ) {
                ModeBanner(serviceMode)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    SearchPanel(
                        results = results,
                        saved = saved,
                        onSearch = onSearch,
                        onResultClick = onResultClick,
                        onFavorite = onFavorite,
                        onClearResults = onClearResults,
                        modifier = Modifier.weight(1f),
                    )
                    Surface(
                        onClick = onOpenSettings,
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 4.dp,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                            tint = NavColors.Panel,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
            }

            if (hasRoute) {
                Button(
                    onClick = onDrive,
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 28.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_m_straight),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.nav_start),
                        fontSize = 16.sp,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }

        // 统一的自绘"回中"按钮：浏览/导航两态共用，位置一致
        if (showRecenter) {
            Surface(
                onClick = onRecenter,
                shape = CircleShape,
                color = NavColors.Panel,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 120.dp)
                    .size(52.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_recenter),
                    contentDescription = stringResource(R.string.nav_recenter_desc),
                    tint = Color.White,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

@OptIn(BetaHorizonVisualizationApi::class)
@Composable
private fun MapHost(
    mapInfra: MapDisplayInfrastructure,
    navVizInfra: NavigationVisualizationInfrastructure,
    initialCenter: GeoPoint,
    cameraTrackingMode: CameraTrackingMode,
    cameraTarget: CameraOptions?,
    isNavigating: Boolean,
    onMapPanning: () -> Unit,
) {
    val mapViewState = rememberMapViewState(
        initialCameraOptions = InitialCameraOptions.LocationBased(position = initialCenter, zoom = INITIAL_ZOOM),
    )

    // 导航态给底部引导面板留白，避免车标被遮挡（等价于命令式 map.setPadding）；仅在状态变化时更新
    LaunchedEffect(isNavigating) {
        mapViewState.safeArea = PaddingValues(bottom = if (isNavigating) NAV_BOTTOM_SAFE_AREA.dp else 0.dp)
    }

    LaunchedEffect(cameraTrackingMode) { mapViewState.cameraState.trackingMode = cameraTrackingMode }
    LaunchedEffect(cameraTarget) { cameraTarget?.let { mapViewState.cameraState.animateCamera(it) } }

    TomTomMap(
        modifier = Modifier.fillMaxSize(),
        infrastructure = mapInfra,
        state = mapViewState,
        onMapPanningListener = { onMapPanning() },
    ) {
        CurrentLocationMarker(
            CurrentLocationMarkerProperties { type = LocationMarkerOptions.Type.Chevron },
        )
        // 预览路线（路由数据源）+ 主动导航（导航数据源）+ 沿途要素，统一由 NavigationVisualization 渲染
        NavigationVisualization(infrastructure = navVizInfra) {
            // 路况事件：在路线上着色拥堵/事故段
            TrafficVisualization(state = rememberTrafficVisualizationState(trafficIncidentsEnabled = true))
            // 更优路线提示（行程中出现更快路线时高亮）
            BetterRouteVisualization(state = rememberBetterRouteVisualizationState(enabled = true))
            // 沿途 Horizon 要素（默认全开）：危险预警 / 安全提醒点（测速等）/ 交通标志 / 红绿灯 / 铁道口
            HorizonVisualization()
        }
    }
}

@Composable
private fun ModeBanner(mode: ServiceMode) {
    val (color, textRes) = when (mode) {
        ServiceMode.ONLINE_FIRST -> NavColors.ModeOnline to R.string.mode_online_first
        ServiceMode.ONBOARD_ONLY -> NavColors.ModeOnboard to R.string.mode_onboard_only
    }
    Box(
        modifier = Modifier.fillMaxWidth().background(color).padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = stringResource(textRes), color = Color.White, fontSize = 13.sp)
    }
}

private const val INITIAL_ZOOM = 14.0
private const val NAV_BOTTOM_SAFE_AREA = 220
