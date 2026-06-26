package com.tomtom.demo.nav.core.sdk.mapdata

import com.tomtom.demo.nav.core.sdk.OnboardDataNotProvisioned

/** 离线地图区域（领域模型，UI 用）。 */
data class OfflineRegion(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val installed: Boolean,
)

/**
 * ［PPT 模块总览 · 离线数据 Data Management］
 * NDS 离线地图、区域下载、增量更新、产线灌装。
 *
 * 离线数据是 OnboardOnly 兜底的前提（授权架构），也是"优先离线"体验的地基。
 *
 * 文档：docs.tomtom.com → guides/offline/quickstart · offline-map-setup · manual-map-management
 * 授权：离线数据包 + 更新服务为单独授权（★，含产线灌装与年度更新条款 —— 讲义 2.6/第四章）。
 *
 * TODO(WS4，离线数据授权与测试数据包到位后)：
 * - NdsStore 初始化（本地地图存储路径 + 密钥）：com.tomtom.sdk.datamanagement.nds.NdsStore；
 * - 区域枚举/下载/删除（本类三个方法的真实现）→ 设置中心"离线地图管理"页；
 * - 在线增量更新（NdsStoreUpdateConfiguration）与更新策略（仅 WIFI、后台时机）；
 * - 产线灌装流程（阶段 4 专题）；
 * - NavServiceFactory 的 OnboardOnly 分支接 OfflineSearch / OfflineRoutePlanner（同一 NdsStore）。
 */
class MapDataService internal constructor() {

    fun listRegions(): List<OfflineRegion> = throw notProvisioned()

    fun downloadRegion(regionId: String): Nothing = throw notProvisioned()

    fun checkForUpdates(): Nothing = throw notProvisioned()

    private fun notProvisioned() = OnboardDataNotProvisioned(
        "NDS 离线数据未灌装 / 离线授权未开通 —— 接入步骤见本类 KDoc 与讲义 2.6",
    )
}
