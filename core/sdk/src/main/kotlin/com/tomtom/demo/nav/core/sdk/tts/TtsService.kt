package com.tomtom.demo.nav.core.sdk.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import com.tomtom.sdk.navigation.TomTomNavigation
import java.util.Locale

/**
 * ［PPT 模块总览 · 语音播报 TTS］
 * 引导语音合成，支持多语言。
 *
 * 播报内容（TBT/限速/安全提醒/前方路况）由导航引擎按位置自动生成（经 GuidanceBus.announcements 下发）；
 * 当前实现：用系统 [TextToSpeech] 朗读引导文本（不再依赖 SDK NavigationFragment 的内置语音），
 * 播报语言跟随 [applyLanguage]，开关见 [voiceGuidanceEnabled]。
 *
 * 文档：docs.tomtom.com → guides/navigation/voice-instructions
 * TODO(WS3，TTS 专题页)：
 * - 目标市场语言清单定稿（含土耳其语）并确认语音包覆盖；
 * - 音频焦点策略：与媒体/电话/语音助手的仲裁（主机音频通道，平台适配层）；
 * - 自定义 TTS 引擎（com.tomtom.sdk:tts TextToSpeechEngine）替换系统内置（如需统一音色）；
 * - 播报内容分级（全量/仅关键转向/静音）映射到设置中心。
 */
class TtsService internal constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var engine: TextToSpeech? = null

    @Volatile
    private var ready = false
    private var language: Locale = Locale.US

    /** 语音播报开关（设置中心"播报语音"需求）。 */
    var voiceGuidanceEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOICE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_ENABLED, value).apply()

    /** 设置引导语言（地图标注语言与播报语言可独立，当前 starter 统一）。 */
    fun applyLanguage(navigation: TomTomNavigation, locale: Locale) {
        navigation.preferredLanguage = locale
        language = locale
        if (ready) engine?.language = locale
    }

    /** 朗读一条引导文本（开关关闭或文本为空则忽略）；新指令打断旧指令。 */
    fun speak(text: String) {
        if (!voiceGuidanceEnabled || text.isBlank()) return
        val tts = engine ?: TextToSpeech(appContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) engine?.language = language
        }.also { engine = it }
        if (ready) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    /** 释放系统 TTS 资源（应用退出 / Activity 销毁时调用）。 */
    fun shutdown() {
        engine?.shutdown()
        engine = null
        ready = false
    }

    private companion object {
        const val PREFS_NAME = "tts_settings"
        const val KEY_VOICE_ENABLED = "voice_enabled"
        const val UTTERANCE_ID = "nav-guidance"
    }
}
