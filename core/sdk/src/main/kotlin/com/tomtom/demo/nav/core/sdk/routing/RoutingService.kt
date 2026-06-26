package com.tomtom.demo.nav.core.sdk.routing

import com.tomtom.sdk.location.GeoPoint
import com.tomtom.sdk.routing.RoutePlanner
import com.tomtom.sdk.routing.RoutePlanningCallback
import com.tomtom.sdk.routing.RoutePlanningResponse
import com.tomtom.sdk.routing.RoutingFailure
import com.tomtom.sdk.routing.options.Itinerary
import com.tomtom.sdk.routing.options.RoutePlanningOptions
import com.tomtom.sdk.routing.options.calculation.AlternativeRoutesOptions
import com.tomtom.sdk.routing.options.calculation.AvoidOptions
import com.tomtom.sdk.routing.options.calculation.AvoidType
import com.tomtom.sdk.routing.options.calculation.CostModel
import com.tomtom.sdk.routing.options.guidance.ExtendedSections
import com.tomtom.sdk.routing.options.guidance.GuidanceOptions
import com.tomtom.sdk.routing.route.Route
import com.tomtom.sdk.vehicle.Vehicle

/** 算路偏好（来自设置中心，规格书"算路原则"需求）。 */
data class RoutePreferences(
    val avoidTolls: Boolean = false,
    val avoidMotorways: Boolean = false,
    val maxAlternatives: Int = 2,
)

/**
 * ［PPT 模块总览 · 路径规划 Routing］
 * 多备选路线、算路偏好、途经点、EV 算路与充电站规划。
 *
 * 当前实现：在线算路（多备选 + 避让偏好 + 车辆 profile）。实例经 NavServiceFactory
 * 按运行模式创建 —— OnboardOnly 在离线授权后切 OfflineRoutePlanner（讲义 2.4），接口不变。
 *
 * 文档：docs.tomtom.com → guides/routing/quickstart · planning-a-route ·
 *       planning-alternative-routes · guides/navigation/vehicle（含 EV 参数）
 * TODO(WS3)：
 * - 途经点（≤3，Itinerary(origin, destination, waypoints)）；
 * - 海外"避开限行"等价物：AvoidType.LowEmissionZones、Vignettes（讲义本地化映射）；
 * - EV 充电站自动规划：ChargingOptions（最低电量约束）— 需长途 EV 算路授权（EV 专题页）。
 */
class RoutingService internal constructor(private val planner: RoutePlanner) {

    fun planRoutes(
        origin: GeoPoint,
        destination: GeoPoint,
        preferences: RoutePreferences,
        vehicle: Vehicle,
        onResult: (routes: List<Route>, options: RoutePlanningOptions) -> Unit,
        onError: (String) -> Unit,
    ) {
        val avoidTypes = buildSet {
            if (preferences.avoidTolls) add(AvoidType.TollRoads)
            if (preferences.avoidMotorways) add(AvoidType.Motorways)
        }
        val options = RoutePlanningOptions(
            itinerary = Itinerary(origin = origin, destination = destination),
            costModel = CostModel(
                avoidOptions = if (avoidTypes.isEmpty()) null else AvoidOptions(avoidTypes = avoidTypes),
            ),
            alternativeRoutesOptions = AlternativeRoutesOptions(maxAlternatives = preferences.maxAlternatives),
            // ExtendedSections.All 让路线携带车道分段 —— 车道指引（LaneGuidance）的前提
            guidanceOptions = GuidanceOptions(extendedSections = ExtendedSections.All),
            vehicle = vehicle,
        )
        planner.planRoute(
            options,
            object : RoutePlanningCallback {
                override fun onSuccess(result: RoutePlanningResponse) = onResult(result.routes, options)

                override fun onFailure(failure: RoutingFailure) = onError(failure.message)
            },
        )
    }
}
