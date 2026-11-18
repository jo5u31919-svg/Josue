package com.chronos.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_timers")
data class CustomTimerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val startMinuteOfDay: Int,
    val durationMinutes: Int,
    val activeDaysMask: Int, // Bits 0..6 for Mon..Sun (0b1111111 = all days)
    val resetPeriod: String = ResetPeriod.DAILY.name,
    val privateNotes: String = "",
    val isRunning: Boolean = false,
    val remainingSeconds: Long = durationMinutes * 60L,
    val lastTickEpochMs: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
) {
    fun parsedResetPeriod(): ResetPeriod {
        return ResetPeriod.entries.firstOrNull { it.name == resetPeriod } ?: ResetPeriod.DAILY
    }

    fun isDayActive(mondayBasedDayIndex: Int): Boolean {
        if (activeDaysMask == 0) return true
        return (activeDaysMask and (1 shl mondayBasedDayIndex)) != 0
    }

    fun formatDaysSummary(): String {
        val labels = listOf("L", "M", "X", "J", "V", "S", "D")
        val selected = labels.indices.filter { (activeDaysMask and (1 shl it)) != 0 }.map { labels[it] }
        return if (selected.size == 7) "Todos" else if (selected.isEmpty()) "Hoy" else selected.joinToString(" ")
    }
}
