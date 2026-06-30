package com.tomtom.demo.nav.feature.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tomtom.demo.nav.App
import com.tomtom.demo.nav.R
import com.tomtom.demo.nav.feature.home.MainActivity
import com.tomtom.demo.nav.ui.theme.TomTomNavDemoTheme

/**
 * ［feature:onboarding · WS2］开机警告页（Compose，规格书"警告界面"需求）：
 * 免责声明 + 隐私数据共享同意（Telemetry 专题页）。已同意则直接进入主界面。
 */
class WarningActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as App).container

        if (container.settingsRepository.warningAccepted) {
            enterMain()
            return
        }

        setContent {
            TomTomNavDemoTheme {
                WarningScreen(
                    initialOptIn = container.telemetryManager.userOptedIn,
                    onAccept = { optIn ->
                        container.settingsRepository.warningAccepted = true
                        // 隐私同意：一处写入，SDK 全局生效（TelemetryManager）
                        container.telemetryManager.userOptedIn = optIn
                        enterMain()
                    },
                )
            }
        }
    }

    private fun enterMain() {
        startActivity(Intent(this, MainActivity::class.java).also { it.data = intent.data })
        finish()
    }
}

@Composable
private fun WarningScreen(initialOptIn: Boolean, onAccept: (Boolean) -> Unit) {
    var optIn by rememberSaveable { mutableStateOf(initialOptIn) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.warning_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.warning_body),
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 16.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = stringResource(R.string.privacy_opt_in), modifier = Modifier.weight(1f))
            Switch(checked = optIn, onCheckedChange = { optIn = it })
        }
        Button(
            onClick = { onAccept(optIn) },
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Text(text = stringResource(R.string.warning_accept))
        }
    }
}
