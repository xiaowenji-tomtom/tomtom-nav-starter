package com.tomtom.demo.nav.core.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 设置中心（WS4）：算路偏好、EV 模式、首启免责声明状态。
 * 注意：Telemetry 数据共享开关在 :core:sdk TelemetryManager（隐私域单独管理）。
 */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    data class RoutePreferences(
        val avoidTolls: Boolean,
        val avoidMotorways: Boolean,
        val evRouting: Boolean,
    )

    private val _routePreferences = MutableStateFlow(load())
    val routePreferences: StateFlow<RoutePreferences> = _routePreferences

    var warningAccepted: Boolean
        get() = prefs.getBoolean(KEY_WARNING_ACCEPTED, false)
        set(value) = prefs.edit().putBoolean(KEY_WARNING_ACCEPTED, value).apply()

    fun update(transform: RoutePreferences.() -> RoutePreferences) {
        val updated = _routePreferences.value.transform()
        prefs.edit()
            .putBoolean(KEY_AVOID_TOLLS, updated.avoidTolls)
            .putBoolean(KEY_AVOID_MOTORWAYS, updated.avoidMotorways)
            .putBoolean(KEY_EV_ROUTING, updated.evRouting)
            .apply()
        _routePreferences.value = updated
    }

    private fun load() = RoutePreferences(
        avoidTolls = prefs.getBoolean(KEY_AVOID_TOLLS, false),
        avoidMotorways = prefs.getBoolean(KEY_AVOID_MOTORWAYS, false),
        evRouting = prefs.getBoolean(KEY_EV_ROUTING, false),
    )

    private companion object {
        const val KEY_AVOID_TOLLS = "avoid_tolls"
        const val KEY_AVOID_MOTORWAYS = "avoid_motorways"
        const val KEY_EV_ROUTING = "ev_routing"
        const val KEY_WARNING_ACCEPTED = "warning_accepted"
    }
}
