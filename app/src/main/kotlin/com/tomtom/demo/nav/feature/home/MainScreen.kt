package com.tomtom.demo.nav.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomtom.demo.nav.R
import com.tomtom.demo.nav.core.sdk.ServiceMode
import com.tomtom.demo.nav.core.sdk.guidance.GuidanceSnapshot
import com.tomtom.demo.nav.feature.guidance.NavigationOverlay
import com.tomtom.demo.nav.feature.search.SearchPanel
import com.tomtom.demo.nav.feature.search.SearchViewModel
import com.tomtom.demo.nav.ui.theme.NavColors
import com.tomtom.sdk.map.display.ui.MapView
import kotlinx.coroutines.flow.StateFlow

private typealias SearchItem = SearchViewModel.SearchItem

/**
 * ［feature:home］导航主屏（Compose）：地图宿主 + 浏览态搜索/设置 chrome + 导航态自绘引导面板。
 *
 * 架构未变：地图仍是命令式 [MapView]（经 AndroidView 内嵌），其生命周期由宿主 Activity 转发；
 * 算路 / 引导 / 定位编排仍在 MainActivity，本组件只负责渲染与回调。状态来源：
 * 运行模式、引导快照、搜索结果均为 [StateFlow]，经 collectAsStateWithLifecycle 订阅。
 */
@Composable
fun MainScreen(
    mapView: MapView,
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
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 命令式地图（不改架构）：同一 MapView 实例由 Activity 创建并转发生命周期
        AndroidView(factory = { mapView }, modifier = Modifier.matchParentSize())

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
