package com.chronos.app.data.local

import androidx.room.Entity

@Entity(
    tableName = "daily_neural_stats",
    primaryKeys = ["dateKey", "categoryName"]
)
data class DailyNeuralStatEntity(
    val dateKey: String,
    val categoryName: String,
    val focusSeconds: Long = 0L,
    val sessionsCount: Int = 0,
    val updatedAtMs: Long = System.currentTimeMillis()
)
