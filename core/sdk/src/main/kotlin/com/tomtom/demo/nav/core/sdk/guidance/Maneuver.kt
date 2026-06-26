package com.tomtom.demo.nav.core.sdk.guidance

/**
 * 与 SDK 解耦的转向动作分类 —— 自绘引导面板据此选择转向图标。
 * 由 [GuidanceBus] 从下一条 [com.tomtom.sdk.navigation.guidance.instruction.GuidanceInstruction] 归一化得到，
 * 消费端（UI / 仪表）只依赖本枚举，不触达 SDK 指令类型。
 */
enum class Maneuver {
    DEPART,
    STRAIGHT,
    BEAR_LEFT,
    BEAR_RIGHT,
    TURN_LEFT,
    TURN_RIGHT,
    SHARP_LEFT,
    SHARP_RIGHT,
    UTURN,
    ROUNDABOUT,
    MERGE,
    FORK_LEFT,
    FORK_RIGHT,
    EXIT_LEFT,
    EXIT_RIGHT,
    ARRIVE,
    UNKNOWN,
}
