package com.chronos.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PerformanceTimerDao {
    @Query("SELECT * FROM performance_timers")
    fun observeAll(): Flow<List<PerformanceTimerEntity>>

    @Query("SELECT * FROM performance_timers")
    suspend fun getAllSnapshot(): List<PerformanceTimerEntity>

    @Query("SELECT * FROM performance_timers WHERE categoryName = :categoryName")
    suspend fun getByCategory(categoryName: String): PerformanceTimerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(timers: List<PerformanceTimerEntity>)

    @Update
    suspend fun update(timer: PerformanceTimerEntity)
}
