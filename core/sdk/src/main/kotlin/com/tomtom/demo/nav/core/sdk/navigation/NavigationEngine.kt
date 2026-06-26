package com.tomtom.demo.nav.core.sdk.navigation

import com.tomtom.demo.nav.core.sdk.guidance.GuidanceBus
import com.tomtom.sdk.navigation.NavigationState
import com.tomtom.sdk.navigation.TomTomNavigation

/**
 * ［PPT 模块总览 · 导航引导 Navigation］
 * TBT 引导、车道指引、偏航重算、更优路线提议、路线进度。
 *
 * 包装导航引擎实例：引导数据统一经 [GuidanceBus] 分发给多屏消费端（仪表/HUD/Widget/ISA）。
 * 偏航重算与动态更优路线由引擎自动处理，无需应用层代码。
 *
 * 文档：docs.tomtom.com → guides/navigation/quickstart · turn-by-turn-navigation ·
 *       turn-by-turn-instructions
 * TODO(WS3)：
 * - 更优路线提议的交互策略（自动切换 / 用户确认）— BetterProposalAcceptanceMode 产品定义；
 * - 自绘引导面板：订阅 GuidanceBus 替换默认 NavigationFragment（车道蓝/白配色 UI 规范）。
 */
class NavigationEngine internal constructor(
    val navigation: TomTomNavigation,
    private val guidanceBus: GuidanceBus,
) {
    val isNavigating: Boolean
        get() = navigation.navigationState != NavigationState.Idle

    /** 导航开始：接通引导数据分发（多屏/ISA 链路自此有数据）。 */
    fun onNavigationStarted() = guidanceBus.attach(navigation)

    /** 导航结束：断开分发并复位快照。 */
    fun onNavigationStopped() = guidanceBus.detach()

    fun close() {
        guidanceBus.detach()
        navigation.close()
    }
}
