package com.tomtom.demo.nav.core.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * 数据层（WS2/WS4）：收藏夹与历史目的地的本地存储。
 * 云同步（跨设备）属贵司账号体系，SDK/本工程范围外 —— 见讲义 2.3「收藏点管理」。
 */
@Entity(tableName = "saved_place")
data class SavedPlace(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    /** 0=历史目的地, 1=收藏, 2=家, 3=公司 */
    val kind: Int = KIND_HISTORY,
    val updatedAtMs: Long = System.currentTimeMillis(),
) {
    companion object {
        const val KIND_HISTORY = 0
        const val KIND_FAVORITE = 1
        const val KIND_HOME = 2
        const val KIND_WORK = 3
    }
}

@Dao
interface SavedPlaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(place: SavedPlace): Long

    @Query("SELECT * FROM saved_place WHERE kind != ${SavedPlace.KIND_HISTORY} ORDER BY updatedAtMs DESC")
    fun favorites(): Flow<List<SavedPlace>>

    @Query("SELECT * FROM saved_place WHERE kind = ${SavedPlace.KIND_HISTORY} ORDER BY updatedAtMs DESC LIMIT :limit")
    fun recentHistory(limit: Int = 10): Flow<List<SavedPlace>>

    @Query("DELETE FROM saved_place WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        "DELETE FROM saved_place WHERE kind = ${SavedPlace.KIND_HISTORY} AND id NOT IN " +
            "(SELECT id FROM saved_place WHERE kind = ${SavedPlace.KIND_HISTORY} ORDER BY updatedAtMs DESC LIMIT :keep)",
    )
    suspend fun trimHistory(keep: Int = 50)
}

@Database(entities = [SavedPlace::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedPlaceDao(): SavedPlaceDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tomtom-nav-demo.db",
                ).build().also { instance = it }
            }
    }
}

/** 仓库：UI 经此读写，不直接触达 DAO。 */
class PlacesRepository(private val dao: SavedPlaceDao) {

    fun favorites(): Flow<List<SavedPlace>> = dao.favorites()

    fun recentHistory(): Flow<List<SavedPlace>> = dao.recentHistory()

    suspend fun recordHistory(name: String, address: String, lat: Double, lon: Double) {
        dao.insert(
            SavedPlace(name = name, address = address, latitude = lat, longitude = lon, kind = SavedPlace.KIND_HISTORY),
        )
        dao.trimHistory()
    }

    suspend fun addFavorite(name: String, address: String, lat: Double, lon: Double) {
        dao.insert(
            SavedPlace(name = name, address = address, latitude = lat, longitude = lon, kind = SavedPlace.KIND_FAVORITE),
        )
    }

    suspend fun remove(id: Long) = dao.delete(id)
}
