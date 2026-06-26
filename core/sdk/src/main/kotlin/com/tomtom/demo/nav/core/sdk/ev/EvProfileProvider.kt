package com.tomtom.demo.nav.core.sdk.ev

import com.tomtom.quantity.Energy
import com.tomtom.quantity.Force
import com.tomtom.quantity.Power
import com.tomtom.quantity.Speed
import com.tomtom.sdk.vehicle.ChargeLevel
import com.tomtom.sdk.vehicle.ElectricEngine
import com.tomtom.sdk.vehicle.ElectricVehicleConsumption
import com.tomtom.sdk.vehicle.Vehicle

/**
 * EV 车辆 profile（见 Workshop《EV 算路专题》页）。
 *
 * 把车辆电气参数交给算路引擎，即可获得续航感知路线；
 * 自动充电站规划另需充电参数（充电曲线/插头类型）与长途 EV 算路授权。
 *
 * TODO(整车团队，7 月底前)：
 * - speedConsumption 替换为 实车标定的速度-能耗曲线；
 * - currentCharge 接 CAN 实时 SOC（VehicleSignalSource.batteryChargeKwh）；
 * - 充电规划参数（batteryCurve / chargingConnectors）随授权开通补充。
 */
interface EvProfileProvider {
    /** 构建用于算路的车辆对象（EV 模式关闭时返回普通燃油车）。 */
    fun vehicle(evMode: Boolean): Vehicle
}

class DefaultEvProfileProvider : EvProfileProvider {

    override fun vehicle(evMode: Boolean): Vehicle {
        if (!evMode) return Vehicle.Car()
        return Vehicle.Car(
            electricEngine = ElectricEngine(
                consumption = ElectricVehicleConsumption(
                    auxiliaryPower = Power.kilowatts(1),
                    // 占位曲线（kWh/100km）—— 必须替换为整车标定数据
                    speedConsumption = mapOf(
                        Speed.kilometersPerHour(50) to Force.kilowattHoursPer100Kilometers(14),
                        Speed.kilometersPerHour(100) to Force.kilowattHoursPer100Kilometers(20),
                        Speed.kilometersPerHour(130) to Force.kilowattHoursPer100Kilometers(26),
                    ),
                ),
                // 占位电量 —— currentCharge 接 CAN 实时 SOC
                chargeLevel = ChargeLevel(
                    currentCharge = Energy.kilowattHours(40),
                    maxCharge = Energy.kilowattHours(60),
                ),
            ),
        )
    }
}
