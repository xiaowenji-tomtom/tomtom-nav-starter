package com.tomtom.demo.nav.feature.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.tomtom.demo.nav.App
import com.tomtom.demo.nav.databinding.ActivityWarningBinding
import com.tomtom.demo.nav.feature.home.MainActivity

/**
 * ［feature:onboarding · WS2］开机警告页（规格书"警告界面"需求）：
 * 免责声明 + 隐私数据共享同意（Telemetry 专题页）。已同意则直接进入主界面。
 */
class WarningActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as App).container

        if (container.settingsRepository.warningAccepted) {
            enterMain()
            return
        }

        val binding = ActivityWarningBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.privacySwitch.isChecked = container.telemetryManager.userOptedIn
        binding.acceptButton.setOnClickListener {
            container.settingsRepository.warningAccepted = true
            // 隐私同意：一处写入，SDK 全局生效（TelemetryManager）
            container.telemetryManager.userOptedIn = binding.privacySwitch.isChecked
            enterMain()
        }
    }

    private fun enterMain() {
        startActivity(Intent(this, MainActivity::class.java).also { it.data = intent.data })
        finish()
    }
}
