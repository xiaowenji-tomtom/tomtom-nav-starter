package com.tomtom.demo.nav.core.platform

import android.util.Log

/**
 * 整车数据通道（平台适配层，WS5）—— ISA 限速信息经此下发到整车 ISA 模块。
 *
 * 数据流（见 Workshop《总体架构》页）：
 * NavSDK 限速输出 → 应用层转发（IsaSpeedLimitForwarder）→ 本接口 → CAN → 整车 ISA 模块。
 *
 * TODO(车机集成)：替换为主机提供的 CAN 写通道；ISA 属法规功能，
 * 信号格式 / 周期 / 失效行为须与整车团队签订接口协议。
 */
interface VehicleBus {
    /**
     * @param speedLimitKmh 当前位置限速（km/h）；null 表示无限速或未知（区分方式见接口协议）。
     * @param unlimited 明确为"不限速"路段（如部分德国高速）。
     * @param currentSpeedKmh 车辆当前速度（km/h），来自导航引擎位置流。
     */
    fun sendSpeedLimitToIsa(speedLimitKmh: Double?, unlimited: Boolean, currentSpeedKmh: Double?)
}

/** 开发期实现：仅打日志，便于台架联调前验证数据流。 */
class LogVehicleBus : VehicleBus {
    override fun sendSpeedLimitToIsa(speedLimitKmh: Double?, unlimited: Boolean, currentSpeedKmh: Double?) {
        Log.d(TAG, "ISA speedLimit=${speedLimitKmh ?: if (unlimited) "unlimited" else "unknown"} speed=$currentSpeedKmh")
    }

    private companion object {
        const val TAG = "VehicleBus"
    }
}
