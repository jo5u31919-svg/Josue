package com.chronos.app.domain

import com.chronos.app.data.local.DailyNeuralStatEntity
import com.chronos.app.data.local.PerformanceCategory
import com.chronos.app.data.local.PerformanceTimerEntity
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

enum class StatsPeriod(val id: String, val label: String) {
    THIS_WEEK("this_week", "Esta Semana"),
    LAST_7_DAYS("last_7_days", "Últimos 7 días")
}

data class NeuralDayPoint(
    val dateKey: String,
    val dayLabel: String,
    val isToday: Boolean,
    val minutesByCategory: Map<PerformanceCategory, Float>,
    val totalMinutes: Float
)

data class CategoryBreakdown(
    val category: PerformanceCategory,
    val totalMinutes: Float,
    val sessionsCount: Int,
    val shareFraction: Float
)

data class NeuralStatsSummary(
    val period: StatsPeriod,
    val days: List<NeuralDayPoint>,
    val totalFocusMinutes: Float,
    val dominantCategory: PerformanceCategory?,
    val activeStreakDays: Int,
    val activeCategoriesCount: Int,
    val categoryBreakdowns: List<CategoryBreakdown>,
    val hasRecordedActivity: Boolean
)

object NeuralStatsCalculator {

    private val shortDayNames = listOf("LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB", "DOM")

    fun formatDateKey(nowMillis: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        return formatDateKey(cal)
    }

    fun formatDateKey(cal: Calendar): String {
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        return String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
    }

    fun computeStats(
        period: StatsPeriod,
        persistedStats: List<DailyNeuralStatEntity>,
        performanceTimers: List<PerformanceTimerEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): NeuralStatsSummary {
        val todayCal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayKey = formatDateKey(todayCal)

        val windowDays = buildPeriodDays(period, todayCal)
        val statsMap = persistedStats.associateBy { it.dateKey to it.categoryName }

        val liveElapsedByCategory = mutableMapOf<String, Long>()
        performanceTimers.forEach { entity ->
            if (entity.isRunning) {
                val liveElapsed = ((nowMillis - entity.lastTickEpochMs) / 1000L)
                    .coerceIn(0L, entity.remainingSeconds.coerceAtLeast(0L))
                if (liveElapsed > 0L) {
                    liveElapsedByCategory[entity.categoryName] = liveElapsed
                }
            }
        }

        val categorySecondsTotals = mutableMapOf<PerformanceCategory, Long>().apply {
            PerformanceCategory.entries.forEach { put(it, 0L) }
        }
        val categorySessionsTotals = mutableMapOf<PerformanceCategory, Int>().apply {
            PerformanceCategory.entries.forEach { put(it, 0) }
        }

        val dayPoints = windowDays.map { (dateKey, dayLabel) ->
            val isToday = dateKey == todayKey
            val minutesMap = linkedMapOf<PerformanceCategory, Float>()
            var dayTotalSec = 0L

            PerformanceCategory.entries.forEach { category ->
                val persisted = statsMap[dateKey to category.name]
                val baseSec = persisted?.focusSeconds ?: 0L
                val liveSec = if (isToday) (liveElapsedByCategory[category.name] ?: 0L) else 0L
                val totalSec = baseSec + liveSec
                dayTotalSec += totalSec

                categorySecondsTotals[category] = (categorySecondsTotals[category] ?: 0L) + totalSec
                categorySessionsTotals[category] = (categorySessionsTotals[category] ?: 0) +
                    (persisted?.sessionsCount ?: 0) +
                    (if (liveSec > 0L) 1 else 0)

                minutesMap[category] = totalSec / 60f
            }

            NeuralDayPoint(
                dateKey = dateKey,
                dayLabel = dayLabel,
                isToday = isToday,
                minutesByCategory = minutesMap,
                totalMinutes = dayTotalSec / 60f
            )
        }

        val totalSecAll = categorySecondsTotals.values.sum()
        val totalFocusMinutes = totalSecAll / 60f
        val dominantCategory = categorySecondsTotals.entries
            .filter { it.value > 0L }
            .maxByOrNull { it.value }
            ?.key

        val breakdowns = PerformanceCategory.entries.map { category ->
            val sec = categorySecondsTotals[category] ?: 0L
            val mins = sec / 60f
            val share = if (totalSecAll > 0L) (sec.toFloat() / totalSecAll.toFloat()) else 0f
            CategoryBreakdown(
                category = category,
                totalMinutes = mins,
                sessionsCount = categorySessionsTotals[category] ?: 0,
                shareFraction = share.coerceIn(0f, 1f)
            )
        }

        val activeCategoriesCount = performanceTimers.count { it.isRunning }
        val streakDays = computeActiveStreak(persistedStats, liveElapsedByCategory.values.sum(), todayCal)

        return NeuralStatsSummary(
            period = period,
            days = dayPoints,
            totalFocusMinutes = totalFocusMinutes,
            dominantCategory = dominantCategory,
            activeStreakDays = streakDays,
            activeCategoriesCount = activeCategoriesCount,
            categoryBreakdowns = breakdowns,
            hasRecordedActivity = totalSecAll > 0L
        )
    }

    private fun buildPeriodDays(period: StatsPeriod, todayCal: Calendar): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        val cursor = todayCal.clone() as Calendar
        when (period) {
            StatsPeriod.THIS_WEEK -> {
                val mondayOffset = when (cursor.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.MONDAY -> 0
                    Calendar.TUESDAY -> 1
                    Calendar.WEDNESDAY -> 2
                    Calendar.THURSDAY -> 3
                    Calendar.FRIDAY -> 4
                    Calendar.SATURDAY -> 5
                    else -> 6
                }
                cursor.add(Calendar.DAY_OF_YEAR, -mondayOffset)
                repeat(7) {
                    result.add(formatDateKey(cursor) to shortDayNames[mondayBasedIndex(cursor)])
                    cursor.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            StatsPeriod.LAST_7_DAYS -> {
                cursor.add(Calendar.DAY_OF_YEAR, -6)
                repeat(7) {
                    result.add(formatDateKey(cursor) to shortDayNames[mondayBasedIndex(cursor)])
                    cursor.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
        }
        return result
    }

    private fun mondayBasedIndex(cal: Calendar): Int {
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            else -> 6
        }
    }

    private fun computeActiveStreak(
        persistedStats: List<DailyNeuralStatEntity>,
        todayLiveSeconds: Long,
        todayCal: Calendar
    ): Int {
        val activeDates = persistedStats
            .groupBy { it.dateKey }
            .filterValues { list -> list.sumOf { it.focusSeconds } > 0L }
            .keys
            .toMutableSet()

        val todayKey = formatDateKey(todayCal)
        if (todayLiveSeconds > 0L) {
            activeDates.add(todayKey)
        }
        if (activeDates.isEmpty()) return 0

        val cursor = todayCal.clone() as Calendar
        if (!activeDates.contains(todayKey)) {
            cursor.add(Calendar.DAY_OF_YEAR, -1)
        }

        var streak = 0
        while (activeDates.contains(formatDateKey(cursor))) {
            streak++
            cursor.add(Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }

    fun formatMinutesCompact(minutes: Float): String {
        val totalMins = minutes.roundToInt().coerceAtLeast(0)
        val hours = totalMins / 60
        val remMins = totalMins % 60
        return when {
            hours > 0 && remMins > 0 -> "${hours}h ${remMins}m"
            hours > 0 -> "${hours}h 00m"
            else -> "${remMins}m"
        }
    }
}
