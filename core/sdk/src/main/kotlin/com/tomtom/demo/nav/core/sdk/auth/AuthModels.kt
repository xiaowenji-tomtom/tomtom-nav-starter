package com.tomtom.demo.nav.core.sdk.auth

/** AMS 产品 ID（与 AMS 设计文档一致）。 */
const val PRODUCT_NAVIGATION = "navigation"
const val PRODUCT_ISA = "isa"

/** 单个产品的授权结果（对应 AMS /v1/auth/verify 返回的 products[] 条目）。 */
data class ProductAuth(
    val productId: String,
    val apiKey: String,
    val expireAtEpochMs: Long,
) {
    val isExpired: Boolean get() = System.currentTimeMillis() > expireAtEpochMs
}

/**
 * 授权状态机（见 Workshop《授权时序》《运行模式状态机》两页）。
 */
sealed interface AuthState {
    /** 启动初始：本地无缓存、AMS 尚未返回。 */
    data object Unknown : AuthState

    /** 持有有效授权（fromCache=true 表示来自本地缓存，AMS 结果未返回前先用它，不阻塞启动）。 */
    data class Valid(val products: Map<String, ProductAuth>, val fromCache: Boolean) : AuthState

    /** AMS 不可达/超时，但缓存仍在宽限期内 —— 维持在线能力。 */
    data class Grace(val products: Map<String, ProductAuth>) : AuthState

    /** 无有效授权（无缓存且鉴权失败，或 AMS 明确返回过期/无效）。 */
    data object Invalid : AuthState

    fun productsOrNull(): Map<String, ProductAuth>? = when (this) {
        is Valid -> products
        is Grace -> products
        else -> null
    }
}
