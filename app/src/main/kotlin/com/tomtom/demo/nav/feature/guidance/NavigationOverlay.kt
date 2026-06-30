package com.tomtom.demo.nav.feature.guidance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tomtom.demo.nav.R
import com.tomtom.demo.nav.core.sdk.guidance.GuidanceSnapshot
import com.tomtom.demo.nav.core.sdk.guidance.LaneInfo
import com.tomtom.demo.nav.core.sdk.guidance.Maneuver
import com.tomtom.demo.nav.ui.theme.NavColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * ［feature:guidance］自绘 TBT 引导面板（Compose 体系，取代原 NavigationView + view_navigation.xml）。
 *
 * 纯展示组件：只消费 [GuidanceSnapshot]（经 GuidanceBus 下发），不触达 SDK 类型。
 * 元素对齐官方导航 UI：顶部转向指引（图标 + 距离 + 道路名）、车道指引、限速牌 + 当前车速、
 * 底部到达时间 / 剩余路程 / 用时 + 结束按钮。
 */
@Composable
fun NavigationOverlay(
    snapshot: GuidanceSnapshot,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        // 顶部：转向指引 + 车道指引；右下角对齐限速/车速簇
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(12.dp),
        ) {
            InstructionCard(snapshot)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                if (snapshot.lanes.isNotEmpty()) {
                    LanesCard(snapshot.lanes)
                }
                Spacer(Modifier.weight(1f))
                SpeedCluster(snapshot)
            }
        }

        // 底部：到达 / 剩余 / 用时 + 结束
        BottomEtaPanel(
            snapshot = snapshot,
            onStop = onStop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(12.dp),
        )
    }
}

@Composable
private fun InstructionCard(snapshot: GuidanceSnapshot) {
    Card(
        colors = CardDefaults.cardColors(containerColor = NavColors.Panel),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val (icon, mirror) = maneuverIcon(snapshot.maneuver)
            Icon(
                painter = painterResource(icon),
                contentDescription = stringResource(R.string.nav_maneuver_desc),
                tint = NavColors.PanelText,
                modifier = Modifier
                    .size(60.dp)
                    .scale(scaleX = if (mirror) -1f else 1f, scaleY = 1f),
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                val arrived = snapshot.maneuver == Maneuver.ARRIVE
                Text(
                    text = if (arrived) {
                        stringResource(R.string.nav_arrived)
                    } else {
                        formatDistance(snapshot.distanceToNextMeters)
                    },
                    color = NavColors.PanelText,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )
                val road = if (arrived) {
                    snapshot.nextRoadName ?: stringResource(R.string.nav_dest)
                } else {
                    snapshot.nextRoadName ?: snapshot.nextInstruction.orEmpty()
                }
                if (road.isNotEmpty()) {
                    Text(text = road, color = NavColors.PanelSub, fontSize = 16.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun LanesCard(lanes: List<LaneInfo>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = NavColors.Panel),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            lanes.forEach { lane ->
                Text(
                    text = lane.arrows,
                    fontSize = 22.sp,
                    // 推荐车道高亮（白），其余暗显
                    color = if (lane.recommended) NavColors.PanelText else NavColors.NonRecommendedLane,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun SpeedCluster(snapshot: GuidanceSnapshot) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        snapshot.speedLimitKmh?.let { limit ->
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(color = Color.White, shape = CircleShape)
                    .border(width = 4.dp, color = NavColors.Stop, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = limit.roundToInt().toString(),
                    color = NavColors.SpeedLimitText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        snapshot.speedKmh?.let { speed ->
            Spacer(Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = NavColors.Panel),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                Text(
                    text = speed.roundToInt().toString(),
                    color = NavColors.PanelText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(54.dp).padding(vertical = 6.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun BottomEtaPanel(
    snapshot: GuidanceSnapshot,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = NavColors.Panel),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EtaColumn(
                value = formatEta(snapshot.remainingTimeSeconds),
                label = stringResource(R.string.nav_eta_label),
                modifier = Modifier.weight(1f),
            )
            EtaColumn(
                value = formatDistance(snapshot.remainingDistanceMeters),
                label = stringResource(R.string.nav_remaining_distance_label),
                modifier = Modifier.weight(1f),
            )
            EtaColumn(
                value = formatDuration(snapshot.remainingTimeSeconds),
                label = stringResource(R.string.nav_remaining_time_label),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = NavColors.Stop),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(text = stringResource(R.string.nav_stop), color = Color.White)
            }
        }
    }
}

@Composable
private fun EtaColumn(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = value, color = NavColors.PanelText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = NavColors.PanelSub, fontSize = 12.sp)
    }
}

// —— 纯函数：格式化 + maneuver → 图标（与原 NavigationView 一致）——

/** maneuver → (drawable, 是否水平镜像)。左转类复用右转图标并镜像，减少资源数。 */
private fun maneuverIcon(maneuver: Maneuver): Pair<Int, Boolean> = when (maneuver) {
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
    return if (minutes >= 60) "${minutes / 60} 小时 ${minutes % 60} 分" else "$minutes 分钟"
}

private fun formatEta(remainingSeconds: Long?): String {
    val s = remainingSeconds ?: return "—"
    val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    return clockFormat.format(Date(System.currentTimeMillis() + s * 1000))
}
