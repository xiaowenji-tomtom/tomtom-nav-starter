package com.tomtom.demo.nav.core.sdk.location

import android.annotation.SuppressLint
import android.content.Context
import com.tomtom.sdk.location.DefaultLocationProviderFactory
import com.tomtom.sdk.location.GeoLocation
import com.tomtom.sdk.location.LocationProvider
import com.tomtom.sdk.location.mapmatched.MapMatchedLocationProviderFactory
import com.tomtom.sdk.location.simulation.SimulationLocationProvider
import com.tomtom.sdk.location.simulation.strategy.InterpolationStrategy
import com.tomtom.sdk.navigation.TomTomNavigation
import com.tomtom.sdk.routing.route.Route

/**
 * ［PPT 模块总览 · 定位 Location］
 * GNSS/系统定位接入、自定义 LocationProvider、地图吸附、模拟定位。
 *
 * SDK 不直接读 GPS —— 位置统一经 LocationProvider 注入（车机集成的关键边界）。
 *
 * 文档：docs.tomtom.com → guides/location/quickstart
 * TODO(WS5，定位 automotive-grade 专题页)：
 * - 8155 上以 GNSS-INS 融合输出替换/增强默认定位；
 * - 经 LocationInterceptor 注入 CAN 信号（轮速→distanceTraveled、档位 R→drivingDirection、俯仰角），
 *   信号来源：core/platform VehicleSignalSource；
 * - 质量要求：≥1Hz、单调时钟、开阔精度 ≤15m（核对清单见 Workshop 定位专题页）。
 */
class LocationEngine internal constructor(private val context: Context) {

    /** 常规 GPS/系统定位（开发期；量产替换见上方 TODO）。 */
    fun gps(): LocationProvider = DefaultLocationProviderFactory.create(context = context)

    /** 地图吸附定位（车标贴路 + 隧道软 DR），导航中供地图使用。 */
    fun mapMatched(navigation: TomTomNavigation): LocationProvider =
        MapMatchedLocationProviderFactory.create(navigation)

    /** 沿路线模拟行驶（模拟导航需求 + 日常联调）。 */
    @SuppressLint("RestrictedApi")
    fun simulation(route: Route): LocationProvider =
        SimulationLocationProvider.create(
            strategy = InterpolationStrategy(route.geometry.map { GeoLocation(it) }),
        )
}
