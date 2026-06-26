package com.tomtom.demo.nav.core.sdk.search

import com.tomtom.sdk.location.GeoPoint
import com.tomtom.sdk.search.Search
import com.tomtom.sdk.search.SearchCallback
import com.tomtom.sdk.search.SearchOptions
import com.tomtom.sdk.search.SearchResponse
import com.tomtom.sdk.search.common.error.SearchFailure

/** 搜索结果的领域模型（UI 不触达 SDK 类型）。 */
data class PlaceResult(
    val name: String,
    val address: String,
    val position: GeoPoint,
)

/**
 * ［PPT 模块总览 · 搜索 Search］
 * 关键字/类别/周边搜索、联想、EV 充电桩、沿途搜索、逆地理编码。
 *
 * 当前实现：在线关键字搜索（位置偏置）。实例经 NavServiceFactory 按运行模式创建 ——
 * OnboardOnly 模式将在 NDS 数据 + 离线授权到位后返回离线实现（讲义 2.3），本类接口不变。
 *
 * 文档：docs.tomtom.com → guides/search/quickstart · guides/search/ev-search
 * TODO(WS2/WS3)：
 * - 周边/类别搜索（停车场、加油站）— SearchOptions(categoryIds/geoBias)；
 * - 沿途搜索（充电桩/加油站偏好）— SearchOptions(route = route.geometry)；
 * - EV 充电桩搜索（插头类型过滤、动态数据）— 需 EV 服务授权；
 * - 移图选点逆地理编码 — ReverseGeocoder（在线/离线/混合）。
 */
class SearchService internal constructor(private val search: Search) {

    fun keywordSearch(
        query: String,
        bias: GeoPoint?,
        limit: Int = DEFAULT_LIMIT,
        onResult: (List<PlaceResult>) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (query.isBlank()) {
            onResult(emptyList())
            return
        }
        search.search(
            SearchOptions(query = query.trim(), limit = limit, geoBias = bias),
            object : SearchCallback {
                override fun onSuccess(result: SearchResponse) {
                    onResult(
                        result.results.map { r ->
                            PlaceResult(
                                name = r.poi?.names?.firstOrNull()
                                    ?: r.place.address?.freeformAddress
                                    ?: "未命名地点",
                                address = r.place.address?.freeformAddress.orEmpty(),
                                position = r.place.coordinate,
                            )
                        },
                    )
                }

                override fun onFailure(failure: SearchFailure) {
                    onError(failure.message)
                }
            },
        )
    }

    private companion object {
        const val DEFAULT_LIMIT = 8
    }
}
