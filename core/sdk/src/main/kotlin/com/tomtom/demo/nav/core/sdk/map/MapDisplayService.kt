package com.tomtom.demo.nav.core.sdk.map

import com.tomtom.sdk.location.GeoPoint
import com.tomtom.sdk.map.display.MapOptions
import com.tomtom.sdk.map.display.camera.InitialCameraOptions

/**
 * ［PPT 模块总览 · 地图显示 Map Display］
 * 矢量地图渲染、手势、相机、标记、路线绘制。
 *
 * 职责：集中提供地图初始化配置（Key、初始相机、样式）；
 * 地图对象（TomTomMap）的运行时操作仍在 UI 宿主（MapFragment 回调）中进行。
 *
 * 文档：docs.tomtom.com → guides/map-display/map-display-for-views/quickstart
 * TODO(WS2)：品牌化地图样式（StyleDescriptor）、昼夜样式切换策略、比例尺档位映射。
 */
class MapDisplayService internal constructor(
    private val apiKeyProvider: () -> String,
) {
    fun mapOptions(initialCenter: GeoPoint): MapOptions =
        MapOptions(
            mapKey = apiKeyProvider(),
            initialCameraOptions = InitialCameraOptions.LocationBased(position = initialCenter),
        )
}
