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

@Entity(tableName = "draft_reports")
data class DraftReportEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val severity: String,
    val location: String,
    val photoNamesJson: String,
    val state: String,
    val createdAt: Long,
)

@Dao
interface DraftReportDao {
    @Query("SELECT * FROM draft_reports ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DraftReportEntity>>

    @Query("SELECT * FROM draft_reports WHERE id = :id LIMIT 1")
    suspend fun get(id: String): DraftReportEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(report: DraftReportEntity)

    @Query("DELETE FROM draft_reports WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(entities = [DraftReportEntity::class], version = 1, exportSchema = false)
abstract class DraftReportDatabase : RoomDatabase() {
    abstract fun drafts(): DraftReportDao
}
