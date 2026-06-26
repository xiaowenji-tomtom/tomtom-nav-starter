package com.tomtom.demo.nav.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tomtom.demo.nav.core.data.PlacesRepository
import com.tomtom.demo.nav.core.sdk.NavServiceFactory
import com.tomtom.demo.nav.core.sdk.search.PlaceResult
import com.tomtom.demo.nav.core.sdk.search.SearchService
import com.tomtom.sdk.location.GeoPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ［feature:search · WS2］搜索界面的 ViewModel。
 * SDK 能力经 NavServiceFactory → SearchService（搜索域），收藏/历史走数据层 PlacesRepository。
 */
class SearchViewModel(
    private val factory: NavServiceFactory,
    private val places: PlacesRepository,
) : ViewModel() {

    data class SearchItem(
        val name: String,
        val address: String,
        val position: GeoPoint,
        val isSaved: Boolean = false,
    )

    private val _results = MutableStateFlow<List<SearchItem>>(emptyList())
    val results: StateFlow<List<SearchItem>> = _results

    /** 收藏 + 最近历史（输入框聚焦且为空时展示）。 */
    val saved: StateFlow<List<SearchItem>> =
        combine(places.favorites(), places.recentHistory()) { favorites, history ->
            (favorites + history).map {
                SearchItem(it.name, it.address, GeoPoint(it.latitude, it.longitude), isSaved = true)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors

    private var searchService: SearchService? = null

    fun search(query: String, bias: GeoPoint?) {
        val service = searchService ?: runCatching { factory.createSearchService() }
            .getOrElse { e ->
                _errors.tryEmit(e.message ?: "搜索服务不可用")
                return
            }
            .also { searchService = it }

        service.keywordSearch(
            query = query,
            bias = bias,
            onResult = { places ->
                _results.value = places.map { it.toItem() }
            },
            onError = { message -> _errors.tryEmit(message) },
        )
    }

    /** 目的地选定 → 记入历史（规格书"历史目的地"需求）。 */
    fun recordSelection(item: SearchItem) {
        viewModelScope.launch {
            places.recordHistory(item.name, item.address, item.position.latitude, item.position.longitude)
        }
    }

    /** 收藏（规格书"收藏点管理"需求；云同步属贵司账号体系）。 */
    fun addFavorite(item: SearchItem) {
        viewModelScope.launch {
            places.addFavorite(item.name, item.address, item.position.latitude, item.position.longitude)
        }
    }

    fun clearResults() {
        _results.value = emptyList()
    }

    /** 模式切换后由 UI 调用：丢弃旧实例，下次按新模式重建。 */
    fun onServiceModeChanged() {
        searchService = null
    }

    private fun PlaceResult.toItem() = SearchItem(name = name, address = address, position = position)

    companion object {
        fun factory(navServiceFactory: NavServiceFactory, places: PlacesRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SearchViewModel(navServiceFactory, places) as T
            }
    }
}
