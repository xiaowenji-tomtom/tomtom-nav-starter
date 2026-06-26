package com.tomtom.demo.nav.core.sdk.safety

/**
 * ［PPT 模块总览 · 安全提醒 Safety Locations］
 * 电子眼/危险路段数据（受各国法规约束）。
 *
 * 合规要求（⚠，Workshop 授权与合规页）：电子眼提醒在法国/德国/瑞士等市场受法律限制，
 * 产品行为必须按国家配置 —— 建议以《国家 × 功能可用性矩阵》为唯一事实来源。
 *
 * 文档：docs.tomtom.com → guides/navigation/safety-locations
 * 授权：Safety Locations 为单独授权服务（★）。
 *
 * TODO(WS3，授权开通后)：
 * - 引入 com.tomtom.sdk.safetylocations:safetylocations 依赖并接入导航配置；
 * - 提醒展示与播报（经 GuidanceBus 进入多屏链路）；
 * - [isAllowedIn] 接入合规矩阵（当前保守返回 false，全部市场默认关闭）。
 */
class SafetyLocationsService internal constructor() {

    /** 目标市场是否允许电子眼提醒（合规矩阵驱动；当前保守默认关闭）。 */
    fun isAllowedIn(countryIso2: String): Boolean = false
}
