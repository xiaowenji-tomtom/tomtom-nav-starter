package com.tomtom.demo.nav.feature.settings

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tomtom.demo.nav.App
import com.tomtom.demo.nav.R
import com.tomtom.demo.nav.core.sdk.OnboardDataNotProvisioned
import com.tomtom.demo.nav.ui.theme.TomTomNavDemoTheme

/**
 * ［feature:settings · WS4］设置中心（Compose）：
 * 数据共享（Telemetry）、播报语音（TTS 域）、算路偏好（Routing 域）、EV 算路（EV profile）、
 * 离线地图管理入口（Data Management 域，NDS 授权后接 MapDataService）。
 */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TomTomNavDemoTheme {
                SettingsScreen()
            }
        }
    }
}

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    val container = (context.applicationContext as App).container
    val settings = container.settingsRepository
    val tts = container.navServiceFactory.tts
    val prefs = settings.routePreferences.value

    var telemetry by remember { mutableStateOf(container.telemetryManager.userOptedIn) }
    var voice by remember { mutableStateOf(tts.voiceGuidanceEnabled) }
    var avoidTolls by remember { mutableStateOf(prefs.avoidTolls) }
    var avoidMotorways by remember { mutableStateOf(prefs.avoidMotorways) }
    var evRouting by remember { mutableStateOf(prefs.evRouting) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        SectionHeader(stringResource(R.string.settings_privacy_section))
        SwitchRow(stringResource(R.string.settings_telemetry), telemetry) {
            telemetry = it
            // 同意状态变更即时生效（SDK 在下一次读取 consent 回调时感知）
            container.telemetryManager.userOptedIn = it
        }
        SwitchRow(stringResource(R.string.settings_voice), voice) {
            voice = it
            tts.voiceGuidanceEnabled = it
        }

        SectionHeader(stringResource(R.string.settings_route_section))
        SwitchRow(stringResource(R.string.settings_avoid_tolls), avoidTolls) {
            avoidTolls = it
            settings.update { copy(avoidTolls = it) }
        }
        SwitchRow(stringResource(R.string.settings_avoid_motorways), avoidMotorways) {
            avoidMotorways = it
            settings.update { copy(avoidMotorways = it) }
        }
        SwitchRow(stringResource(R.string.settings_ev_routing), evRouting) {
            evRouting = it
            settings.update { copy(evRouting = it) }
        }

        SectionHeader(stringResource(R.string.settings_data_section))
        Text(
            text = stringResource(R.string.settings_offline_maps),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    // 离线数据 Data Management 域：NDS 授权后这里改为区域管理页
                    val message = try {
                        container.navServiceFactory.mapData.listRegions().toString()
                    } catch (e: OnboardDataNotProvisioned) {
                        e.message ?: context.getString(R.string.offline_maps_placeholder)
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
                .padding(12.dp),
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
