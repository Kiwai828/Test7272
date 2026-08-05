package com.recapmaker.app.data.local

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "video_history")
data class VideoHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val inputVideoName: String = "",
    val outputVideoName: String = "",
    val status: String = "completed",
    val createdAt: Long = System.currentTimeMillis(),
    val duration: Int = 0,
    val fileSize: Long = 0,
    val effectsApplied: String = "",
    val ttsUsed: Boolean = false,
    val subtitleGenerated: Boolean = false,
    val processingTimeMs: Long = 0,
    val coinsSpent: Int = 0,
    val errorMessage: String = "",
)

@Dao
interface VideoHistoryDao {
    @Query("SELECT * FROM video_history ORDER BY createdAt DESC")
    fun getAll(): Flow<List<VideoHistoryEntity>>

    @Query("SELECT * FROM video_history WHERE status = :status ORDER BY createdAt DESC")
    fun getByStatus(status: String): Flow<List<VideoHistoryEntity>>

    @Query("SELECT * FROM video_history WHERE createdAt BETWEEN :start AND :end ORDER BY createdAt DESC")
    fun getByDateRange(start: Long, end: Long): Flow<List<VideoHistoryEntity>>

    @Query("SELECT * FROM video_history WHERE effectsApplied LIKE '%' || :effect || '%' ORDER BY createdAt DESC")
    fun getByEffect(effect: String): Flow<List<VideoHistoryEntity>>

    @Query("SELECT * FROM video_history WHERE inputVideoName LIKE '%' || :query || '%' OR outputVideoName LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun search(query: String): Flow<List<VideoHistoryEntity>>

    @Insert
    suspend fun insert(entry: VideoHistoryEntity): Long

    @Delete
    suspend fun delete(entry: VideoHistoryEntity)

    @Query("DELETE FROM video_history WHERE createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("SELECT COUNT(*) FROM video_history")
    suspend fun count(): Int
}

val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("DROP TABLE IF EXISTS video_history")
        database.execSQL(
            "CREATE TABLE video_history (" +
                "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "inputVideoName TEXT NOT NULL, " +
                "outputVideoName TEXT NOT NULL, " +
                "status TEXT NOT NULL, " +
                "createdAt INTEGER NOT NULL, " +
                "duration INTEGER NOT NULL, " +
                "fileSize INTEGER NOT NULL, " +
                "effectsApplied TEXT NOT NULL, " +
                "ttsUsed INTEGER NOT NULL, " +
                "subtitleGenerated INTEGER NOT NULL, " +
                "processingTimeMs INTEGER NOT NULL, " +
                "coinsSpent INTEGER NOT NULL, " +
                "errorMessage TEXT NOT NULL" +
            ")"
        )
    }
}

@Database(entities = [VideoHistoryEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoHistoryDao(): VideoHistoryDao
}
