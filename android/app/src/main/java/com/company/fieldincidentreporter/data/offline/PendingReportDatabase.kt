package com.company.fieldincidentreporter.data.offline

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "pending_reports")
data class PendingReportEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val severity: String,
    val location: String,
    val photoPathsJson: String,
    val state: String,
    val attempted: Boolean,
    val createdAt: Long,
)

@Dao
interface PendingReportDao {
    @Query("SELECT * FROM pending_reports ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PendingReportEntity>>

    @Query("SELECT * FROM pending_reports WHERE id = :id LIMIT 1")
    suspend fun get(id: String): PendingReportEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(report: PendingReportEntity)

    @Query("UPDATE pending_reports SET state = :state WHERE id = :id")
    suspend fun updateState(id: String, state: String)

    @Query("UPDATE pending_reports SET state = 'SENDING', attempted = 1 WHERE id = :id")
    suspend fun markSending(id: String)

    @Query("DELETE FROM pending_reports WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(entities = [PendingReportEntity::class], version = 1, exportSchema = false)
abstract class PendingReportDatabase : RoomDatabase() {
    abstract fun pendingReports(): PendingReportDao
}
