package com.tomtom.demo.nav.feature.settings

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.tomtom.demo.nav.App
import com.tomtom.demo.nav.R
import com.tomtom.demo.nav.core.sdk.OnboardDataNotProvisioned
import com.tomtom.demo.nav.databinding.ActivitySettingsBinding

/**
 * ［feature:settings · WS4］设置中心：
 * 数据共享（Telemetry）、播报语音（TTS 域）、算路偏好（Routing 域）、EV 算路（EV profile）、
 * 离线地图管理入口（Data Management 域，NDS 授权后接 MapDataService）。
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val container = (application as App).container
        val settings = container.settingsRepository
        val tts = container.navServiceFactory.tts
        val prefs = settings.routePreferences.value

        binding.telemetrySwitch.isChecked = container.telemetryManager.userOptedIn
        binding.voiceSwitch.isChecked = tts.voiceGuidanceEnabled
        binding.avoidTollsSwitch.isChecked = prefs.avoidTolls
        binding.avoidMotorwaysSwitch.isChecked = prefs.avoidMotorways
        binding.evRoutingSwitch.isChecked = prefs.evRouting

        binding.telemetrySwitch.setOnCheckedChangeListener { _, checked ->
            // 同意状态变更即时生效（SDK 在下一次读取 consent 回调时感知）
            container.telemetryManager.userOptedIn = checked
        }
        binding.voiceSwitch.setOnCheckedChangeListener { _, checked ->
            tts.voiceGuidanceEnabled = checked
        }
        binding.avoidTollsSwitch.setOnCheckedChangeListener { _, checked ->
            settings.update { copy(avoidTolls = checked) }
        }
        binding.avoidMotorwaysSwitch.setOnCheckedChangeListener { _, checked ->
            settings.update { copy(avoidMotorways = checked) }
        }
        binding.evRoutingSwitch.setOnCheckedChangeListener { _, checked ->
            settings.update { copy(evRouting = checked) }
        }
        binding.offlineMapsRow.setOnClickListener {
            // 离线数据 Data Management 域：NDS 授权后这里改为区域管理页
            val message = try {
                container.navServiceFactory.mapData.listRegions().toString()
            } catch (e: OnboardDataNotProvisioned) {
                e.message ?: getString(R.string.offline_maps_placeholder)
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}
