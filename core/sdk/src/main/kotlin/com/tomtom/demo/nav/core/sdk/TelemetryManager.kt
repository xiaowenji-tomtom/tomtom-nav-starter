package com.tomtom.demo.nav.core.sdk

import android.content.Context
import com.tomtom.sdk.telemetry.UserConsent

/**
 * Telemetry 用户同意管理（隐私合规要求，见 Workshop《Telemetry 上报与隐私合规》页）。
 *
 * 原则：用户同意前保持关闭；同意状态由设置页"数据共享"开关写入，一处管理、全局生效。
 * TODO(集成)：与整车隐私同意框架联动（一处撤回、处处生效）。
 */
class TelemetryManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 用户是否同意数据共享（默认 false —— 未同意前不上报）。 */
    var userOptedIn: Boolean
        get() = prefs.getBoolean(KEY_OPT_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_OPT_IN, value).apply()

    /** 供 SDK 初始化时回调读取（buildSdkConfiguration 的 telemetryUserConsent）。 */
    fun currentConsent(): UserConsent =
        if (userOptedIn) UserConsent.TelemetryOn else UserConsent.TelemetryOff

    private companion object {
        const val PREFS_NAME = "telemetry_consent"
        const val KEY_OPT_IN = "opt_in"
    }
}
