package com.tomtom.demo.nav.core.sdk

/**
 * 应用运行模式 —— 整个应用只有这一个开关（见 Workshop《应用层架构》页关键规则）。
 *
 * - [ONLINE_FIRST]：持有有效授权（或宽限期内），在线服务可用，混合数据源在线优先。
 * - [ONBOARD_ONLY]：无有效授权或授权过期，仅离线能力可用；
 *   导航核心功能（离线地图/搜索/算路/引导）不受影响 —— 前提是 NDS 离线数据已灌装。
 */
enum class ServiceMode {
    ONLINE_FIRST,
    ONBOARD_ONLY,
}

/** OnboardOnly 模式下请求了尚未具备的离线能力（NDS 数据未灌装 / 离线授权未开通）。 */
class OnboardDataNotProvisioned(message: String) : IllegalStateException(message)
