package com.chronos.app.data.local

enum class ResetPeriod(
    val label: String,
    val intervalHours: Int
) {
    DAILY("Diario", 24),
    EVERY_4H("Cada 4h", 4),
    EVERY_8H("Cada 8h", 8),
    EVERY_12H("Cada 12h", 12),
    WEEKLY("Semanal", 168),
    ONCE("Único", 0)
}
