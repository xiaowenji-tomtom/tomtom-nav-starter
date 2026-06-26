package com.tomtom.demo.nav.core.sdk

import com.tomtom.demo.nav.core.sdk.auth.AuthManager
import com.tomtom.demo.nav.core.sdk.auth.AuthState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 授权状态 → 运行模式 的唯一换算点（见 Workshop《运行模式状态机》页）。
 *
 * Valid / Grace → OnlineFirst；Unknown / Invalid → OnboardOnly。
 * UI 订阅 [mode] 展示模式横幅；NavServiceFactory 据此创建对应实现。
 *
 * TODO(产品定义)：检测到从 OnlineFirst 切到 OnboardOnly 时是热切换（立即重建在线服务）
 * 还是下次启动生效 —— 当前实现为"新创建的服务即用新模式"，存量实例由调用方重建。
 */
class ServiceModeController(
    authManager: AuthManager,
    scope: CoroutineScope,
) {
    val mode: StateFlow<ServiceMode> =
        authManager.state
            .map { it.toServiceMode() }
            .stateIn(scope, SharingStarted.Eagerly, authManager.state.value.toServiceMode())

    private companion object {
        fun AuthState.toServiceMode(): ServiceMode = when (this) {
            is AuthState.Valid, is AuthState.Grace -> ServiceMode.ONLINE_FIRST
            AuthState.Unknown, AuthState.Invalid -> ServiceMode.ONBOARD_ONLY
        }
    }
}
