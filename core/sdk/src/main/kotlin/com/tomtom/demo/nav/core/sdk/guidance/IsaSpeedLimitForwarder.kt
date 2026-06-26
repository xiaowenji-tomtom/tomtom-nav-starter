package com.tomtom.demo.nav.core.sdk.guidance

import com.tomtom.demo.nav.core.platform.VehicleBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * ISA 限速转发（见 Workshop《总体架构》页数据流）：
 * NavSDK 限速输出（经 GuidanceBus）→ 本转发器 → VehicleBus（CAN）→ 整车 ISA 模块。
 *
 * ISA 为法规功能（EU GSR）：
 * - 授权为独立 product/Key（AMS isa product）；
 * - 无授权/过期时的降级行为须产品与商务共同定义后在此实现 —— 见讲义第五章。
 */
class IsaSpeedLimitForwarder(
    private val guidanceBus: GuidanceBus,
    private val vehicleBus: VehicleBus,
) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job != null) return
        job = scope.launch {
            guidanceBus.snapshot
                .map { Triple(it.speedLimitKmh, it.speedUnlimited, it.speedKmh) }
                .distinctUntilChanged()
                .collect { (limit, unlimited, speed) ->
                    vehicleBus.sendSpeedLimitToIsa(limit, unlimited, speed)
                }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
