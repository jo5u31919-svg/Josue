package com.chronos.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "performance_timers")
data class PerformanceTimerEntity(
    @PrimaryKey
    val categoryName: String,
    val totalSeconds: Long,
    val remainingSeconds: Long,
    val isRunning: Boolean = false,
    val lastTickEpochMs: Long = System.currentTimeMillis(),
    val completedSessions: Int = 0
) {
    fun category(): PerformanceCategory {
        return PerformanceCategory.entries.firstOrNull { it.name == categoryName }
            ?: PerformanceCategory.MENTAL
    }
}
