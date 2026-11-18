package com.chronos.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomTimerDao {
    @Query("SELECT * FROM custom_timers ORDER BY startMinuteOfDay ASC, id ASC")
    fun observeAll(): Flow<List<CustomTimerEntity>>

    @Query("SELECT * FROM custom_timers ORDER BY startMinuteOfDay ASC, id ASC")
    suspend fun getAllSnapshot(): List<CustomTimerEntity>

    @Query("SELECT * FROM custom_timers WHERE id = :id")
    suspend fun getById(id: Long): CustomTimerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(timer: CustomTimerEntity): Long

    @Update
    suspend fun update(timer: CustomTimerEntity)

    @Query("DELETE FROM custom_timers WHERE id = :id")
    suspend fun deleteById(id: Long)
}
