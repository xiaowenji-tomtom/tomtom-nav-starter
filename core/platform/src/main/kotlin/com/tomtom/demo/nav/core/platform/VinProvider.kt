package com.tomtom.demo.nav.core.platform

/**
 * 车辆身份提供方 —— AMS 鉴权的入参来源（平台适配层，WS5）。
 *
 * TODO(车机集成)：从主机系统接口读取真实 VIN 与车型标识（vehicle_model_id 定义见 AMS 文档）。
 */
interface VinProvider {
    val vin: String
    val vehicleModelId: String
}

class StubVinProvider : VinProvider {
    override val vin: String = "STUB_VIN_0000000000"
    override val vehicleModelId: String = "DEMO_VEHICLE"
}
