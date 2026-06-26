package com.tomtom.demo.nav.core.sdk.auth

/**
 * AMS（授权管理系统）客户端抽象。
 *
 * 量产实现：替换为真实 TomTomAuth SDK ——
 * ```
 * TomTomAuth.init(context, appKey, secret)
 * TomTomAuth.verify(vin, vehicleModelId, callback)   // 返回各产品的 apiKey / expireAt / status
 * ```
 * 把回调桥接为本接口的挂起函数即可，应用其余部分零改动。
 */
interface AmsClient {
    /**
     * 车端鉴权：返回该车辆所有已购产品的授权（对应 AMS POST /v1/auth/verify）。
     * @throws Exception 网络不可达 / 超时 / 服务端错误 —— 由 AuthManager 决定降级策略。
     */
    suspend fun verify(vin: String, vehicleModelId: String): List<ProductAuth>
}

/**
 * 开发期 Stub：直接使用 gradle.properties 注入的 API Key，模拟"鉴权成功、授权一年"。
 *
 * TODO(集成)：接入真实 TomTomAuth SDK 后删除本类。
 */
class StubAmsClient(private val devApiKey: String) : AmsClient {

    override suspend fun verify(vin: String, vehicleModelId: String): List<ProductAuth> {
        check(devApiKey.isNotBlank() && devApiKey != "YOUR_TOMTOM_API_KEY") {
            "请在 gradle.properties 中配置 tomtomApiKey（开发期 Stub 模式）"
        }
        val oneYear = 365L * 24 * 3600 * 1000
        return listOf(
            ProductAuth(PRODUCT_NAVIGATION, devApiKey, System.currentTimeMillis() + oneYear),
            // ISA 与导航共用 NavSDK，授权为独立 product/Key；Stub 期共用同一个 Key
            ProductAuth(PRODUCT_ISA, devApiKey, System.currentTimeMillis() + oneYear),
        )
    }
}
