package com.tomtom.demo.nav.core.sdk.tts

import android.content.Context
import com.tomtom.sdk.navigation.TomTomNavigation
import com.tomtom.sdk.tts.android.AndroidTextToSpeechEngine
import com.tomtom.sdk.tts.engine.AudioMessage
import com.tomtom.sdk.tts.engine.MessagePlaybackListener
import com.tomtom.sdk.tts.engine.MessageType
import com.tomtom.sdk.tts.engine.TextToSpeechEngineError
import java.util.Locale

/**
 * ［PPT 模块总览 · 语音播报 TTS］
 * 引导语音合成，支持多语言。
 *
 * 播报内容（TBT/限速/安全提醒/前方路况）由导航引擎按位置自动生成（经 GuidanceBus.announcements 下发原始 SSML）；
 * 用 SDK 的 [AndroidTextToSpeechEngine] 解析 SSML 并朗读（自带音频焦点处理），不依赖 NavigationFragment 内置语音。
 * 播报语言跟随 [applyLanguage]，开关见 [voiceGuidanceEnabled]。
 *
 * 文档：docs.tomtom.com → guides/navigation/voice-instructions
 * TODO(WS3，TTS 专题页)：
 * - 目标市场语言清单定稿（含土耳其语）并确认语音包覆盖；
 * - 音频焦点策略细化：与媒体/电话/语音助手的仲裁（主机音频通道，平台适配层）；
 * - 播报内容分级（全量/仅关键转向/静音）映射到设置中心。
 */
class TtsService internal constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var engine: AndroidTextToSpeechEngine? = null
    private var language: Locale = Locale.US

    private val noopListener = object : MessagePlaybackListener {
        override fun onStart() = Unit
        override fun onDone() = Unit
        override fun onStop() = Unit
        override fun onError(error: TextToSpeechEngineError) = Unit
    }

    /** 语音播报开关（设置中心"播报语音"需求）。 */
    var voiceGuidanceEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOICE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_ENABLED, value).apply()

    private fun engine(): AndroidTextToSpeechEngine =
        engine ?: AndroidTextToSpeechEngine(appContext, language).also { engine = it }

    /** 设置引导语言（地图标注语言与播报语言可独立，当前 starter 统一）。 */
    fun applyLanguage(navigation: TomTomNavigation, locale: Locale) {
        navigation.preferredLanguage = locale
        language = locale
        engine?.changeLanguage(locale)
    }

    /** 朗读一条引导播报（原始 SSML）；开关关闭或为空则忽略。 */
    fun speak(ssml: String) {
        if (!voiceGuidanceEnabled || ssml.isBlank()) return
        engine().playAudioMessage(AudioMessage(ssml, MessageType.Ssml), noopListener)
    }

    /** 释放 TTS 引擎资源（应用退出 / Activity 销毁时调用）。 */
    fun shutdown() {
        engine?.close()
        engine = null
    }

    private companion object {
        const val PREFS_NAME = "tts_settings"
        const val KEY_VOICE_ENABLED = "voice_enabled"
    }
}
