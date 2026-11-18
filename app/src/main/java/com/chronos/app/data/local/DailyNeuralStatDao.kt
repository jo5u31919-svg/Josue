package com.chronos.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyNeuralStatDao {
    @Query("SELECT * FROM daily_neural_stats ORDER BY dateKey ASC")
    fun observeAll(): Flow<List<DailyNeuralStatEntity>>

    @Query("SELECT * FROM daily_neural_stats WHERE dateKey = :dateKey AND categoryName = :categoryName")
    suspend fun getByDateAndCategory(dateKey: String, categoryName: String): DailyNeuralStatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DailyNeuralStatEntity)

    @Transaction
    suspend fun addFocusSeconds(
        dateKey: String,
        categoryName: String,
        deltaSeconds: Long,
        incrementSession: Int,
        nowMs: Long
    ) {
        if (deltaSeconds <= 0L && incrementSession <= 0) return
        val existing = getByDateAndCategory(dateKey, categoryName)
        val updated = if (existing == null) {
            DailyNeuralStatEntity(
                dateKey = dateKey,
                categoryName = categoryName,
                focusSeconds = deltaSeconds.coerceAtLeast(0L),
                sessionsCount = incrementSession.coerceAtLeast(0),
                updatedAtMs = nowMs
            )
        } else {
            existing.copy(
                focusSeconds = existing.focusSeconds + deltaSeconds.coerceAtLeast(0L),
                sessionsCount = existing.sessionsCount + incrementSession.coerceAtLeast(0),
                updatedAtMs = nowMs
            )
        }
        upsert(updated)
    }
}
