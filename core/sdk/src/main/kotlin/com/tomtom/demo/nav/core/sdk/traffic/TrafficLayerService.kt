package com.tomtom.demo.nav.core.sdk.traffic

import com.tomtom.sdk.map.display.TomTomMap

/**
 * ［PPT 模块总览 · 实时路况 Traffic］
 * 交通流与交通事件显示（地图层 + 路线上）。
 *
 * - 地图层：交通流染色 + 事件图标（本类，一行开启）；
 * - 路线上：沿途拥堵与延误（光柱图/拥堵播报的数据源）经导航引擎与 GuidanceBus 获取。
 *
 * 文档：docs.tomtom.com → guides/navigation/traffic
 * 授权：Traffic 为单独授权服务（★）；土耳其/新加坡覆盖需商务确认（⚠，讲义第四章）。
 * TODO(WS3)：
 * - 光柱图 UI（数据：路线 sections 的交通段 + RouteProgress）；
 * - 路况概览模式切换（规格书"概览模式"需求）。
 */
class TrafficLayerService internal constructor() {

    /** 在地图上开启/关闭路况层（OnlineFirst 模式下调用）。 */
    fun setEnabled(map: TomTomMap, enabled: Boolean) {
        if (enabled) {
            map.showTrafficFlow()
            map.showTrafficIncidents()
        } else {
            map.hideTrafficFlow()
            map.hideTrafficIncidents()
        }
    }
}
