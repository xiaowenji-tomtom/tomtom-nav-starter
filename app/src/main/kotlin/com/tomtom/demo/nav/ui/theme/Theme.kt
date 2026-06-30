package com.tomtom.demo.nav.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Compose 主题（取代 XML 体系下的 Theme.TomTomNavDemo）。
 * 颜色与原 themes.xml 的语义保持一致；自绘引导面板的专用色集中在 [NavColors]。
 */
private val TomTomRed = Color(0xFFDF1B12)

private val LightColors = lightColorScheme(
    primary = TomTomRed,
    secondary = Color(0xFF2C7A4B),
)

private val DarkColors = darkColorScheme(
    primary = TomTomRed,
    secondary = Color(0xFF5FB37E),
)

@Composable
fun TomTomNavDemoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}

/** 自绘导航引导面板配色（对应原 themes.xml 的 nav_* / mode_* / speed_limit_text）。 */
object NavColors {
    val Panel = Color(0xFF1F2A37)
    val PanelText = Color(0xFFFFFFFF)
    val PanelSub = Color(0xFFB8C2CC)
    val Stop = Color(0xFFD32F2F)
    val SpeedLimitText = Color(0xFF1F2A37)
    val ModeOnline = Color(0xFF2C7A4B)
    val ModeOnboard = Color(0xFFB97A0F)
    val NonRecommendedLane = Color(0xFF5F6B78)
}
