package com.chronos.app.domain

import android.graphics.Color
import com.chronos.app.data.local.CustomTimerEntity
import com.chronos.app.data.local.PerformanceTimerEntity
import com.chronos.app.data.local.ResetPeriod
import com.chronos.app.theme.ResolvedCyberPalette
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

enum class DayPhase(val label: String) {
    DAWN("MADRUGADA"),
    MORNING("MAÑANA"),
    AFTERNOON("TARDE"),
    NIGHT("NOCHE")
}

data class ProjectedTimelineNode(
    val timer: CustomTimerEntity,
    val startFraction: Float,
    val durationFraction: Float,
    val isCurrentlyActiveWindow: Boolean,
    val liveRemainingSeconds: Long
)

object ChronosTimeEngine {

    private const val SECONDS_PER_DAY = 86_400L

    fun millisUntilDayReset(nowMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return max(0L, cal.timeInMillis - nowMillis)
    }

    fun dayProgressFraction(nowMillis: Long = System.currentTimeMillis()): Float {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val secondsElapsed =
            cal.get(Calendar.HOUR_OF_DAY) * 3600L +
                cal.get(Calendar.MINUTE) * 60L +
                cal.get(Calendar.SECOND)
        return (secondsElapsed.toFloat() / SECONDS_PER_DAY.toFloat()).coerceIn(0f, 1f)
    }

    fun currentDayPhase(nowMillis: Long = System.currentTimeMillis()): DayPhase {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 0..5 -> DayPhase.DAWN
            in 6..11 -> DayPhase.MORNING
            in 12..17 -> DayPhase.AFTERNOON
            else -> DayPhase.NIGHT
        }
    }

    fun laserColorsForPhase(phase: DayPhase, palette: ResolvedCyberPalette): Pair<Int, Int> {
        return if (palette.isDark) {
            when (phase) {
                DayPhase.DAWN -> Color.parseColor("#9D00FF") to palette.primaryNeon
                DayPhase.MORNING -> palette.primaryNeon to Color.parseColor("#00FF9F")
                DayPhase.AFTERNOON -> Color.parseColor("#FFB800") to palette.secondaryNeon
                DayPhase.NIGHT -> palette.secondaryNeon to Color.parseColor("#B400FF")
            }
        } else {
            when (phase) {
                DayPhase.DAWN -> Color.parseColor("#6D28D9") to palette.primaryNeon
                DayPhase.MORNING -> palette.primaryNeon to Color.parseColor("#059669")
                DayPhase.AFTERNOON -> Color.parseColor("#D97706") to palette.secondaryNeon
                DayPhase.NIGHT -> palette.secondaryNeon to Color.parseColor("#7E22CE")
            }
        }
    }

    fun currentMondayBasedDayIndex(nowMillis: Long = System.currentTimeMillis()): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
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

    fun currentMinuteOfDay(nowMillis: Long = System.currentTimeMillis()): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    fun currentSecondOfDay(nowMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        return cal.get(Calendar.HOUR_OF_DAY) * 3600L +
            cal.get(Calendar.MINUTE) * 60L +
            cal.get(Calendar.SECOND)
    }

    fun projectTimersOnDayBar(
        timers: List<CustomTimerEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<ProjectedTimelineNode> {
        val dayIndex = currentMondayBasedDayIndex(nowMillis)
        val nowSecondOfDay = currentSecondOfDay(nowMillis)
        val result = mutableListOf<ProjectedTimelineNode>()

        timers.filter { it.isDayActive(dayIndex) }.forEach { timer ->
            val period = timer.parsedResetPeriod()
            val startMinutes = timer.startMinuteOfDay.coerceIn(0, 1439)
            val durationMin = timer.durationMinutes.coerceAtLeast(1)

            val occurrenceStartsMin: List<Int> = when (period) {
                ResetPeriod.EVERY_4H, ResetPeriod.EVERY_8H, ResetPeriod.EVERY_12H -> {
                    val stepMin = period.intervalHours * 60
                    val list = mutableListOf<Int>()
                    var m = startMinutes % stepMin
                    while (m < 1440) {
                        list.add(m)
                        m += stepMin
                    }
                    list
                }
                else -> listOf(startMinutes)
            }

            val chosenStartMin = occurrenceStartsMin.firstOrNull { occ ->
                val endSec = (occ + durationMin) * 60L
                nowSecondOfDay <= endSec
            } ?: occurrenceStartsMin.last()

            val startSec = chosenStartMin * 60L
            val endSec = startSec + durationMin * 60L
            val inScheduledWindow = nowSecondOfDay in startSec until endSec

            val liveRemaining = when {
                timer.isRunning -> computeLiveRemainingSeconds(
                    timer.remainingSeconds,
                    timer.isRunning,
                    timer.lastTickEpochMs,
                    nowMillis
                )
                inScheduledWindow -> (endSec - nowSecondOfDay).coerceAtLeast(0L)
                nowSecondOfDay < startSec -> (startSec - nowSecondOfDay).coerceAtLeast(0L)
                else -> durationMin * 60L
            }

            val startFrac = (chosenStartMin / 1440f).coerceIn(0f, 1f)
            val durFrac = (durationMin / 1440f).coerceIn(0.01f, 1f - startFrac)

            result.add(
                ProjectedTimelineNode(
                    timer = timer,
                    startFraction = startFrac,
                    durationFraction = durFrac,
                    isCurrentlyActiveWindow = timer.isRunning || inScheduledWindow,
                    liveRemainingSeconds = liveRemaining
                )
            )
        }

        return result.sortedBy { it.startFraction }
    }

    fun computeLiveRemainingSeconds(
        storedRemainingSeconds: Long,
        isRunning: Boolean,
        lastTickEpochMs: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        if (!isRunning) return storedRemainingSeconds.coerceAtLeast(0L)
        val elapsedSec = ((nowMillis - lastTickEpochMs) / 1000L).coerceAtLeast(0L)
        return (storedRemainingSeconds - elapsedSec).coerceAtLeast(0L)
    }

    fun computeLivePerformanceRemaining(
        entity: PerformanceTimerEntity,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        return computeLiveRemainingSeconds(
            entity.remainingSeconds,
            entity.isRunning,
            entity.lastTickEpochMs,
            nowMillis
        )
    }

    fun formatDurationHms(totalSeconds: Long): String {
        val safe = totalSeconds.coerceAtLeast(0L)
        val hours = safe / 3600L
        val minutes = (safe % 3600L) / 60L
        val seconds = safe % 60L
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun formatDurationMs(totalSeconds: Long): String {
        val safe = totalSeconds.coerceAtLeast(0L)
        val hours = safe / 3600L
        val minutes = (safe % 3600L) / 60L
        val seconds = safe % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatMinuteOfDay(minuteOfDay: Int): String {
        val safe = minuteOfDay.coerceIn(0, 1439)
        return String.format(Locale.US, "%02d:%02d", safe / 60, safe % 60)
    }
}
