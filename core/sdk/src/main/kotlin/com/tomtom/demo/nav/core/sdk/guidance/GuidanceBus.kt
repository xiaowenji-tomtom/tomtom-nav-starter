package com.tomtom.demo.nav.core.sdk.guidance

import com.tomtom.quantity.Distance
import com.tomtom.sdk.location.road.SpeedLimit
import com.tomtom.sdk.navigation.GuidanceUpdatedListener
import com.tomtom.sdk.navigation.LocationContextUpdatedListener
import com.tomtom.sdk.navigation.ProgressUpdatedListener
import com.tomtom.sdk.navigation.TomTomNavigation
import com.tomtom.sdk.navigation.guidance.GuidanceAnnouncement
import com.tomtom.sdk.navigation.guidance.InstructionPhase
import com.tomtom.sdk.navigation.guidance.instruction.GuidanceInstruction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 引导快照 —— 多屏消费端的统一数据契约（《引导数据契约》的代码化雏形）。
 * 字段、单位、刷新率与整车/仪表团队对齐后冻结（阶段 3 前）。
 */
data class GuidanceSnapshot(
    val navigating: Boolean = false,
    /** 下一条引导文本（由播报内容提取，仅供仪表/通知等简显场景）。 */
    val nextInstruction: String? = null,
    /** 距下一动作距离（米）。 */
    val distanceToNextMeters: Double? = null,
    val remainingDistanceMeters: Double? = null,
    val remainingTimeSeconds: Long? = null,
    /** 当前车速（km/h，导航引擎位置流）。 */
    val speedKmh: Double? = null,
    /** 当前位置限速（km/h）；null=未知。 */
    val speedLimitKmh: Double? = null,
    /** 明确不限速路段（如部分德国高速）。 */
    val speedUnlimited: Boolean = false,
)

/**
 * 引导数据分发层（见 Workshop《多屏引导数据流》页）：
 * 订阅一次导航引擎，向所有消费端（中控通知 / 仪表 / HUD / Widget / ISA 转发）广播统一快照。
 *
 * 消费端只依赖 [snapshot]，不触达 SDK 类型 —— 通道实现可独立演进。
 */
class GuidanceBus {

    private val _snapshot = MutableStateFlow(GuidanceSnapshot())
    val snapshot: StateFlow<GuidanceSnapshot> = _snapshot

    private var navigation: TomTomNavigation? = null

    private val progressListener = ProgressUpdatedListener { progress ->
        update {
            copy(
                remainingDistanceMeters = progress.remainingDistance.inMeters(),
                remainingTimeSeconds = progress.remainingTime.inWholeSeconds,
            )
        }
    }

    private val guidanceListener = object : GuidanceUpdatedListener {
        override fun onInstructionsChanged(instructions: List<GuidanceInstruction>) = Unit

        override fun onAnnouncementGenerated(announcement: GuidanceAnnouncement, shouldPlay: Boolean) {
            update { copy(nextInstruction = announcement.ssmlMessage.stripSsml()) }
        }

        override fun onDistanceToNextInstructionChanged(
            distance: Distance,
            instructions: List<GuidanceInstruction>,
            currentPhase: InstructionPhase,
        ) {
            update { copy(distanceToNextMeters = distance.inMeters()) }
        }
    }

    private val locationContextListener = LocationContextUpdatedListener { context ->
        val limit = context.speedLimit
        update {
            copy(
                speedKmh = context.speed.inKilometersPerHour(),
                speedLimitKmh = limit?.speed?.inKilometersPerHour(),
                speedUnlimited = limit?.type == SpeedLimit.Type.Unlimited,
            )
        }
    }

    fun attach(navigation: TomTomNavigation) {
        detach()
        this.navigation = navigation
        navigation.addProgressUpdatedListener(progressListener)
        navigation.addGuidanceUpdatedListener(guidanceListener)
        navigation.addLocationContextUpdatedListener(locationContextListener)
        update { copy(navigating = true) }
    }

    fun detach() {
        navigation?.let {
            it.removeProgressUpdatedListener(progressListener)
            it.removeGuidanceUpdatedListener(guidanceListener)
            it.removeLocationContextUpdatedListener(locationContextListener)
        }
        navigation = null
        _snapshot.value = GuidanceSnapshot()
    }

    private inline fun update(transform: GuidanceSnapshot.() -> GuidanceSnapshot) {
        _snapshot.value = _snapshot.value.transform()
    }

    private fun String.stripSsml(): String = replace(Regex("<[^>]*>"), "").trim()
}
