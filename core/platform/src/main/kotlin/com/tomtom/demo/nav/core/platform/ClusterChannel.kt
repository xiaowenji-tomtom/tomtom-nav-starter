package com.tomtom.demo.nav.core.platform

import android.util.Log

/**
 * 仪表 / HUD 投屏通道（平台适配层，WS5）。
 *
 * GuidanceBus 把统一的引导快照推到本接口；通道实现（Surface 共享 / 视频流 / 私有协议）
 * 由主机系统提供 —— 见 Workshop《多屏引导数据流》页，需在阶段 3 前冻结《引导数据契约》。
 *
 * TODO(车机集成)：按主机投屏方案实现；字段与刷新率以《引导数据契约》为准。
 */
interface ClusterChannel {
    fun publishGuidance(
        nextInstruction: String?,
        distanceToNextMeters: Double?,
        remainingDistanceMeters: Double?,
        remainingTimeSeconds: Long?,
        speedLimitKmh: Double?,
    )
}

/** 开发期实现：仅打日志。 */
class LogClusterChannel : ClusterChannel {
    override fun publishGuidance(
        nextInstruction: String?,
        distanceToNextMeters: Double?,
        remainingDistanceMeters: Double?,
        remainingTimeSeconds: Long?,
        speedLimitKmh: Double?,
    ) {
        Log.d(TAG, "cluster: $nextInstruction in ${distanceToNextMeters}m, remain ${remainingDistanceMeters}m/${remainingTimeSeconds}s")
    }

    private companion object {
        const val TAG = "ClusterChannel"
    }
}
