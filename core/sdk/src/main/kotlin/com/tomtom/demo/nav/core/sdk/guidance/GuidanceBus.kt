package com.tomtom.demo.nav.core.sdk.guidance

import com.tomtom.quantity.Distance
import com.tomtom.sdk.location.road.SpeedLimit
import com.tomtom.sdk.navigation.GuidanceUpdatedListener
import com.tomtom.sdk.navigation.LocationContextUpdatedListener
import com.tomtom.sdk.navigation.ProgressUpdatedListener
import com.tomtom.sdk.navigation.TomTomNavigation
import com.tomtom.sdk.navigation.guidance.GuidanceAnnouncement
import com.tomtom.sdk.navigation.guidance.InstructionPhase
import com.tomtom.sdk.navigation.guidance.instruction.ArrivalGuidanceInstruction
import com.tomtom.sdk.navigation.guidance.instruction.DepartureGuidanceInstruction
import com.tomtom.sdk.navigation.guidance.instruction.ExitRoundaboutGuidanceInstruction
import com.tomtom.sdk.navigation.guidance.instruction.ForkGuidanceInstruction
import com.tomtom.sdk.navigation.guidance.instruction.GuidanceInstruction
import com.tomtom.sdk.navigation.guidance.instruction.MergeGuidanceInstruction
import com.tomtom.sdk.navigation.guidance.instruction.RoundaboutGuidanceInstruction
import com.tomtom.sdk.navigation.guidance.instruction.TurnGuidanceInstruction
import com.tomtom.sdk.routing.route.instruction.common.TurnDirection
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 引导快照 —— 多屏消费端的统一数据契约（《引导数据契约》的代码化雏形）。
 * 字段、单位、刷新率与整车/仪表团队对齐后冻结（阶段 3 前）。
 */
data class GuidanceSnapshot(
    val navigating: Boolean = false,
    /** 下一动作分类（自绘面板据此选转向图标）。 */
    val maneuver: Maneuver = Maneuver.UNKNOWN,
    /** 下一条引导文本（由播报内容提取，仅供仪表/通知等简显场景）。 */
    val nextInstruction: String? = null,
    /** 下一动作所在道路名（如 "Park Street"）。 */
    val nextRoadName: String? = null,
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
 * 订阅一次导航引擎，向所有消费端（中控通知 / 仪表 / HUD / Widget / ISA 转发 / 自绘引导面板）广播统一快照。
 *
 * 消费端只依赖 [snapshot] 与 [announcements]，不触达 SDK 类型 —— 通道实现可独立演进。
 */
class GuidanceBus {

    private val _snapshot = MutableStateFlow(GuidanceSnapshot())
    val snapshot: StateFlow<GuidanceSnapshot> = _snapshot

    /** 待播报的语音文本（纯文本，已去 SSML）；由语音播报端（TTS）消费。 */
    private val _announcements = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val announcements: SharedFlow<String> = _announcements

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
        override fun onInstructionsChanged(instructions: List<GuidanceInstruction>) {
            instructions.firstOrNull()?.let { next ->
                update {
                    copy(
                        maneuver = next.toManeuver(),
                        nextRoadName = next.nextSignificantRoad?.name?.takeIf { it.isNotBlank() },
                    )
                }
            }
        }

        override fun onAnnouncementGenerated(announcement: GuidanceAnnouncement, shouldPlay: Boolean) {
            val text = announcement.ssmlMessage.stripSsml()
            update { copy(nextInstruction = text) }
            if (shouldPlay && text.isNotBlank()) _announcements.tryEmit(text)
        }

        override fun onDistanceToNextInstructionChanged(
            distance: Distance,
            instructions: List<GuidanceInstruction>,
            currentPhase: InstructionPhase,
        ) {
            instructions.firstOrNull()?.let { next ->
                update {
                    copy(
                        distanceToNextMeters = distance.inMeters(),
                        maneuver = next.toManeuver(),
                        nextRoadName = next.nextSignificantRoad?.name?.takeIf { it.isNotBlank() },
                    )
                }
            } ?: update { copy(distanceToNextMeters = distance.inMeters()) }
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

    /** 将 SDK 指令归一化为 [Maneuver]（仅 TurnGuidanceInstruction 细分到方向，其余按类别）。 */
    private fun GuidanceInstruction.toManeuver(): Maneuver = when (this) {
        is DepartureGuidanceInstruction -> Maneuver.DEPART
        is ArrivalGuidanceInstruction -> Maneuver.ARRIVE
        is RoundaboutGuidanceInstruction, is ExitRoundaboutGuidanceInstruction -> Maneuver.ROUNDABOUT
        is MergeGuidanceInstruction -> Maneuver.MERGE
        is ForkGuidanceInstruction -> Maneuver.STRAIGHT
        is TurnGuidanceInstruction -> when (turnDirection) {
            TurnDirection.GoStraight -> Maneuver.STRAIGHT
            TurnDirection.BearLeft -> Maneuver.BEAR_LEFT
            TurnDirection.BearRight -> Maneuver.BEAR_RIGHT
            TurnDirection.TurnLeft -> Maneuver.TURN_LEFT
            TurnDirection.TurnRight -> Maneuver.TURN_RIGHT
            TurnDirection.SharpLeft -> Maneuver.SHARP_LEFT
            TurnDirection.SharpRight -> Maneuver.SHARP_RIGHT
            TurnDirection.TurnAround -> Maneuver.UTURN
            else -> Maneuver.STRAIGHT
        }
        else -> Maneuver.STRAIGHT
    }
}
