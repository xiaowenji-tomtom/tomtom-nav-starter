package com.tomtom.demo.nav.core.data

import android.content.Context
import android.util.Log
import com.tomtom.sdk.common.Result
import com.tomtom.sdk.common.ifFailure
import com.tomtom.sdk.common.ifSuccess
import com.tomtom.sdk.location.Address
import com.tomtom.sdk.location.GeoPoint
import com.tomtom.sdk.location.PersonalLocationType
import com.tomtom.sdk.location.Place
import com.tomtom.sdk.personaldata.PersonalData
import com.tomtom.sdk.personaldata.PersonalDataFactory
import com.tomtom.sdk.personaldata.PersonalLocation
import com.tomtom.sdk.personaldata.UserProfile
import com.tomtom.sdk.personaldata.UserProfileFailure
import com.tomtom.sdk.personaldata.UserProfileUpdatedListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 数据层（WS2/WS4）：收藏夹与历史目的地。
 *
 * 实现改用 **TomTom Personal Data 模块**（离线本地存储），见
 * https://docs.tomtom.com/navigation/android/guides/personalization/personal-data
 * —— 个人位置（家/公司/收藏/最近）统一由 SDK 的 `UserProfile` 管理，跨设备云同步可后续接
 * `OnlinePersonalDataConfiguration`。该 API 仅在 NavSDK **extended** 变体提供。
 */
data class SavedPlace(
    /** SDK 个人位置的 UUID（字符串形式）。 */
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val favorite: Boolean,
)

/** 仓库：UI 经此读写，不直接触达 SDK 类型。 */
class PlacesRepository(
    context: Context,
    private val scope: CoroutineScope,
    storageDir: File = File(context.applicationContext.filesDir, "personal-data"),
) {
    // configuration = null → 离线本地实例（SQLite 后端，无需网络 / API Key）
    private val personalData: PersonalData =
        PersonalDataFactory.create(context = context.applicationContext, storageDir = storageDir)

    private val mutex = Mutex()

    @Volatile
    private var profile: UserProfile? = null

    /** 每次个人数据变化自增，驱动 [favorites] / [recentHistory] 的 Flow 重新发射。 */
    private val revision = MutableStateFlow(0L)

    init {
        scope.launch(Dispatchers.IO) { mutex.withLock { loadProfile() } }
        // 来自其它路径的更新（如导航产生新的"最近目的地"）也反映到 UI
        personalData.addUserProfileUpdatedListener(
            object : UserProfileUpdatedListener {
                override fun onUpdate(result: Result<UserProfile, UserProfileFailure>) {
                    result.ifSuccess { publish(it) }
                }
            },
        )
    }

    fun favorites(): Flow<List<SavedPlace>> = revision.map {
        profile?.locations?.favorites.orEmpty().map { it.toSaved(favorite = true) }
    }

    fun recentHistory(): Flow<List<SavedPlace>> = revision.map {
        profile?.locations?.recentDestinations.orEmpty().take(HISTORY_LIMIT).map { it.toSaved(favorite = false) }
    }

    /** 目的地选定 → 记入"最近目的地"（重复地点会更新访问时间而非新增）。 */
    suspend fun recordHistory(name: String, address: String, lat: Double, lon: Double) = mutate { p ->
        p.locations.add(types = setOf(PersonalLocationType.Recent), place = place(address, lat, lon), name = name)
        trimRecents(p)
    }

    /** 加入收藏（重复地点会合并类型而非新增）。 */
    suspend fun addFavorite(name: String, address: String, lat: Double, lon: Double) = mutate { p ->
        p.locations.add(types = setOf(PersonalLocationType.Favorite), place = place(address, lat, lon), name = name)
    }

    /** 按 [SavedPlace.id] 删除个人位置。 */
    suspend fun remove(id: String) = mutate { p ->
        p.locations.allLocations.firstOrNull { it.id.toString() == id }?.let { p.locations.remove(it) }
    }

    private suspend fun mutate(block: (UserProfile) -> Unit) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val p = profile ?: loadProfile() ?: return@withLock
            block(p)
            personalData.storeUserProfile(p)
                .ifSuccess { publish(it) }
                .ifFailure { Log.w(TAG, "storeUserProfile failed: $it") }
        }
    }

    /** 同步载入用户画像（在 IO 线程、持锁状态下调用）。 */
    private fun loadProfile(): UserProfile? {
        personalData.loadUserProfile()
            .ifSuccess { publish(it) }
            .ifFailure { Log.w(TAG, "loadUserProfile failed: $it") }
        return profile
    }

    private fun publish(updated: UserProfile) {
        profile = updated
        revision.value += 1
    }

    private fun trimRecents(p: UserProfile) {
        p.locations.recentDestinations.drop(HISTORY_KEEP).forEach { p.locations.remove(it) }
    }

    private fun place(address: String, lat: Double, lon: Double): Place =
        Place(coordinate = GeoPoint(latitude = lat, longitude = lon), address = Address(freeformAddress = address))

    private fun PersonalLocation.toSaved(favorite: Boolean) = SavedPlace(
        id = id.toString(),
        name = name,
        address = place.address?.freeformAddress.orEmpty(),
        latitude = place.coordinate.latitude,
        longitude = place.coordinate.longitude,
        favorite = favorite,
    )

    private companion object {
        const val TAG = "PlacesRepository"

        /** 历史列表展示条数（与原实现一致）。 */
        const val HISTORY_LIMIT = 10

        /** 本地保留的最近目的地上限。 */
        const val HISTORY_KEEP = 50
    }
}
