package com.tomtom.demo.nav.core.platform

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * 车辆信号源（平台适配层，WS5）—— automotive-grade 定位与 EV 算路的输入。
 *
 * 用途（见 Workshop《定位 automotive-grade 参数专题》《EV 算路专题》页）：
 * - 轮速/里程   → 定位增强 distanceTraveled（隧道持续引导）
 * - 档位 R     → drivingDirection（倒车车标正确）
 * - 俯仰角     → 高架/隧道上下层区分
 * - 实时 SOC   → EV 算路 ChargeLevel.currentCharge
 *
 * TODO(车机集成)：对接主机 CAN 读取接口（信号清单与底软团队对齐，7 月中前）。
 * 接入定位的方式：DefaultLocationProviderFactory.create(..., locationInterceptor) 注入。
 */
interface VehicleSignalSource {
    /** 车速（km/h，轮速信号换算）。 */
    val speedKmh: Flow<Double>

    /** 是否倒挡。 */
    val reverseGear: Flow<Boolean>

    /** 动力电池 SOC（kWh，按整车信号换算）。 */
    val batteryChargeKwh: Flow<Double>
}

/** 开发期实现：无信号（各 Flow 不发射）。 */
class StubVehicleSignalSource : VehicleSignalSource {
    override val speedKmh: Flow<Double> = emptyFlow()
    override val reverseGear: Flow<Boolean> = emptyFlow()
    override val batteryChargeKwh: Flow<Double> = emptyFlow()
}
