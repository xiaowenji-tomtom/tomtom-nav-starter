package com.tomtom.demo.nav.feature.guidance

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.tomtom.demo.nav.R
import com.tomtom.demo.nav.databinding.ViewNavigationBinding
import com.tomtom.demo.nav.core.sdk.guidance.GuidanceSnapshot
import com.tomtom.demo.nav.core.sdk.guidance.Maneuver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * ［feature:guidance］自绘 TBT 引导面板（View 体系，取代 SDK NavigationFragment）。
 *
 * 纯展示组件：只消费 [GuidanceSnapshot]（经 GuidanceBus 下发），不触达 SDK 类型。
 * 元素对齐官方导航 UI：顶部转向指引（图标 + 距离 + 道路名）、限速牌 + 当前车速、
 * 底部到达时间 / 剩余路程 / 用时 + 结束按钮。
 */
class NavigationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding = ViewNavigationBinding.inflate(LayoutInflater.from(context), this)
    private val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    /** 结束导航回调（结束按钮点击）。 */
    var onStop: (() -> Unit)? = null

    init {
        isVisible = false
        binding.navStop.setOnClickListener { onStop?.invoke() }
    }

    fun show() { isVisible = true }

    fun hide() { isVisible = false }

    fun render(snapshot: GuidanceSnapshot) {
        val (icon, mirror) = iconFor(snapshot.maneuver)
        binding.navManeuver.setImageResource(icon)
        binding.navManeuver.scaleX = if (mirror) -1f else 1f

        if (snapshot.maneuver == Maneuver.ARRIVE) {
            binding.navDistance.text = context.getString(R.string.nav_arrived)
            binding.navRoad.text = snapshot.nextRoadName ?: context.getString(R.string.nav_dest)
        } else {
            binding.navDistance.text = formatDistance(snapshot.distanceToNextMeters)
            binding.navRoad.text = snapshot.nextRoadName ?: snapshot.nextInstruction.orEmpty()
        }

        // 限速牌
        binding.navSpeedLimitContainer.isVisible = snapshot.speedLimitKmh != null
        snapshot.speedLimitKmh?.let { binding.navSpeedLimit.text = it.roundToInt().toString() }

        // 当前车速
        binding.navCurrentSpeedContainer.isVisible = snapshot.speedKmh != null
        snapshot.speedKmh?.let { binding.navCurrentSpeed.text = it.roundToInt().toString() }

        // 底部信息
        binding.navEta.text = formatEta(snapshot.remainingTimeSeconds)
        binding.navRemainingDistance.text = formatDistance(snapshot.remainingDistanceMeters)
        binding.navRemainingTime.text = formatDuration(snapshot.remainingTimeSeconds)
    }

    private fun formatDistance(meters: Double?): String {
        val m = meters ?: return "—"
        return if (m >= 1000) {
            String.format(Locale.getDefault(), "%.1f km", m / 1000.0)
        } else {
            // 取整到最近的 10 米，更接近导航习惯
            "${(m / 10).roundToLong() * 10} m"
        }
    }

    private fun formatDuration(seconds: Long?): String {
        val s = seconds ?: return "—"
        val minutes = (s / 60.0).roundToLong()
        return if (minutes >= 60) {
            "${minutes / 60} 小时 ${minutes % 60} 分"
        } else {
            "$minutes 分钟"
        }
    }

    private fun formatEta(remainingSeconds: Long?): String {
        val s = remainingSeconds ?: return "—"
        return clockFormat.format(Date(System.currentTimeMillis() + s * 1000))
    }

    private companion object {
        /** maneuver → (drawable, 是否水平镜像)。左转类复用右转图标并镜像，减少资源数。 */
        fun iconFor(maneuver: Maneuver): Pair<Int, Boolean> = when (maneuver) {
            Maneuver.DEPART, Maneuver.STRAIGHT, Maneuver.UNKNOWN -> R.drawable.ic_m_straight to false
            Maneuver.BEAR_RIGHT, Maneuver.FORK_RIGHT -> R.drawable.ic_m_bear to false
            Maneuver.BEAR_LEFT, Maneuver.FORK_LEFT -> R.drawable.ic_m_bear to true
            Maneuver.TURN_RIGHT, Maneuver.SHARP_RIGHT, Maneuver.EXIT_RIGHT -> R.drawable.ic_m_turn to false
            Maneuver.TURN_LEFT, Maneuver.SHARP_LEFT, Maneuver.EXIT_LEFT -> R.drawable.ic_m_turn to true
            Maneuver.UTURN -> R.drawable.ic_m_uturn to false
            Maneuver.ROUNDABOUT -> R.drawable.ic_m_roundabout to false
            Maneuver.MERGE -> R.drawable.ic_m_merge to false
            Maneuver.ARRIVE -> R.drawable.ic_m_arrive to false
        }
    }
}
