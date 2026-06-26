package com.tomtom.demo.nav.core.sdk.auth

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * 授权管理：实现 Workshop《授权时序》页的三个场景 ——
 *
 * 1. 启动即读本地缓存（不等待网络），缓存有效 → [AuthState.Valid] (fromCache=true)；
 * 2. 异步调 AMS 鉴权，成功 → 更新缓存与状态；
 * 3. AMS 不可达/超时 → 缓存仍在宽限期内则 [AuthState.Grace]，否则 [AuthState.Invalid]；
 * 4. AMS 明确返回全部过期 → [AuthState.Invalid]（运行模式随之切 OnboardOnly）。
 *
 * TODO(产品定义)：宽限期时长 [gracePeriodMs]、过期后热切换还是下次启动生效。
 * TODO(安全)：缓存改用 EncryptedSharedPreferences / Keystore（当前为明文，仅限开发期）。
 */
class AuthManager(
    context: Context,
    private val amsClient: AmsClient,
    private val scope: CoroutineScope,
    private val gracePeriodMs: Long = DEFAULT_GRACE_PERIOD_MS,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<AuthState> = _state

    /** 场景 A 第 1 步：启动即读缓存，不阻塞。 */
    private fun initialState(): AuthState {
        val cached = loadCache()
        return if (cached.isNotEmpty() && cached.values.none { it.isExpired }) {
            AuthState.Valid(cached, fromCache = true)
        } else {
            AuthState.Unknown
        }
    }

    /** 异步向 AMS 发起鉴权（VIN 与车型 ID 从主机接口读取后传入）。 */
    fun refresh(vin: String, vehicleModelId: String) {
        scope.launch {
            try {
                val products = withTimeout(VERIFY_TIMEOUT_MS) {
                    amsClient.verify(vin, vehicleModelId)
                }
                val valid = products.filter { !it.isExpired }.associateBy { it.productId }
                if (valid.isEmpty()) {
                    // 场景 C：AMS 明确返回过期 → 停用在线（由 ServiceModeController 落地）
                    clearCache()
                    _state.value = AuthState.Invalid
                } else {
                    saveCache(valid)
                    _state.value = AuthState.Valid(valid, fromCache = false)
                }
            } catch (e: Exception) {
                // 场景 B：AMS 不可达 / 超时 → 宽限期判断
                val cached = loadCache()
                _state.value = if (cached.isNotEmpty() && withinGracePeriod()) {
                    AuthState.Grace(cached)
                } else {
                    AuthState.Invalid
                }
            }
        }
    }

    private fun withinGracePeriod(): Boolean {
        val lastSuccess = prefs.getLong(KEY_LAST_SUCCESS, 0L)
        return lastSuccess > 0 && System.currentTimeMillis() - lastSuccess < gracePeriodMs
    }

    // —— 缓存（格式：productId|apiKey|expireAt 每行一条）——

    private fun saveCache(products: Map<String, ProductAuth>) {
        val payload = products.values.joinToString("\n") {
            "${it.productId}|${it.apiKey}|${it.expireAtEpochMs}"
        }
        prefs.edit()
            .putString(KEY_PRODUCTS, payload)
            .putLong(KEY_LAST_SUCCESS, System.currentTimeMillis())
            .apply()
    }

    private fun loadCache(): Map<String, ProductAuth> =
        prefs.getString(KEY_PRODUCTS, null)
            ?.lineSequence()
            ?.mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size == 3) {
                    ProductAuth(parts[0], parts[1], parts[2].toLongOrNull() ?: 0L)
                } else {
                    null
                }
            }
            ?.associateBy { it.productId }
            .orEmpty()

    private fun clearCache() {
        prefs.edit().remove(KEY_PRODUCTS).apply()
    }

    companion object {
        private const val PREFS_NAME = "auth_cache"
        private const val KEY_PRODUCTS = "products"
        private const val KEY_LAST_SUCCESS = "last_success_ms"
        private const val VERIFY_TIMEOUT_MS = 10_000L
        /** 默认宽限期 7 天 —— 待产品定义后调整。 */
        private const val DEFAULT_GRACE_PERIOD_MS = 7L * 24 * 3600 * 1000
    }
}
