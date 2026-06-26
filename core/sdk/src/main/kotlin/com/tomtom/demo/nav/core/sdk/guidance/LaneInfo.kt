package com.tomtom.demo.nav.core.sdk.guidance

/**
 * 与 SDK 解耦的单条车道指引 —— 自绘车道指引视图据此渲染。
 * 由 [GuidanceBus] 从 [com.tomtom.sdk.navigation.guidance.LaneGuidance] 归一化得到。
 *
 * @property arrows 该车道可走方向的箭头字形（如 "↑"、"↰↑"）。
 * @property recommended 该车道是否在当前路线上（应走车道，高亮显示）。
 */
data class LaneInfo(
    val arrows: String,
    val recommended: Boolean,
)
