package com.recapmaker.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "video_history")
data class VideoHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val filePath: String,
    val status: String = "completed",  // completed, failed
    val createdAt: Long = System.currentTimeMillis(),
    val fileSize: Long = 0,
    val duration: Int = 0,  // seconds
    val type: String = "video",  // video, subtitle
)

@Dao
interface VideoHistoryDao {
    @Query("SELECT * FROM video_history ORDER BY createdAt DESC")
    fun getAll(): Flow<List<VideoHistoryEntity>>

    @Insert
    suspend fun insert(entry: VideoHistoryEntity): Long

    @Delete
    suspend fun delete(entry: VideoHistoryEntity)

    @Query("DELETE FROM video_history WHERE createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("SELECT COUNT(*) FROM video_history")
    suspend fun count(): Int
}

@Database(entities = [VideoHistoryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoHistoryDao(): VideoHistoryDao
}
