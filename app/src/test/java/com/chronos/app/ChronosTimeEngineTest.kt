package com.chronos.app

import com.chronos.app.data.local.CustomTimerEntity
import com.chronos.app.data.local.ResetPeriod
import com.chronos.app.domain.ChronosTimeEngine
import com.chronos.app.domain.DayPhase
import com.chronos.app.theme.CyberThemeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ChronosTimeEngineTest {

    @Test
    fun `formatDurationHms formats hours minutes seconds properly`() {
        assertEquals("01:05:09", ChronosTimeEngine.formatDurationHms(3909L))
        assertEquals("00:00:00", ChronosTimeEngine.formatDurationHms(0L))
    }

    @Test
    fun `currentDayPhase matches hour of day`() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 30)
        }
        assertEquals(DayPhase.AFTERNOON, ChronosTimeEngine.currentDayPhase(cal.timeInMillis))
    }

    @Test
    fun `projectTimersOnDayBar sorts nodes chronologically`() {
        val t1 = CustomTimerEntity(
            id = 1L,
            name = "Tarde",
            startMinuteOfDay = 15 * 60,
            durationMinutes = 30,
            activeDaysMask = 0b1111111,
            resetPeriod = ResetPeriod.DAILY.name
        )
        val t2 = CustomTimerEntity(
            id = 2L,
            name = "Mañana",
            startMinuteOfDay = 8 * 60,
            durationMinutes = 45,
            activeDaysMask = 0b1111111,
            resetPeriod = ResetPeriod.DAILY.name
        )
        val projected = ChronosTimeEngine.projectTimersOnDayBar(listOf(t1, t2))
        assertEquals(2, projected.size)
        assertEquals("Mañana", projected[0].timer.name)
        assertEquals("Tarde", projected[1].timer.name)
        assertTrue(projected[0].startFraction < projected[1].startFraction)
    }

    @Test
    fun `theme resolves distinct Day and Night palettes`() {
        val night = CyberThemeId.CYBERPUNK_CLASSIC.resolve(isDark = true)
        val day = CyberThemeId.CYBERPUNK_CLASSIC.resolve(isDark = false)

        assertTrue(night.isDark)
        assertEquals(0xFF0A0A12.toInt(), night.backgroundColor)
        assertEquals(0xFF00F0FF.toInt(), night.primaryNeon)
        assertEquals(0xFFFF0055.toInt(), night.secondaryNeon)

        assertFalse(day.isDark)
        assertEquals(0xFFF2F4F8.toInt(), day.backgroundColor)
        assertEquals(0xFF1A1C23.toInt(), day.textPrimary)
        assertEquals(0xFF007ACC.toInt(), day.primaryNeon)
        assertEquals(0xFFD60044.toInt(), day.secondaryNeon)
    }

    @Test
    fun `neural stats calculator computes dominant category totals and active streak`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 17, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayKey = com.chronos.app.domain.NeuralStatsCalculator.formatDateKey(cal)
        val yesterdayCal = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        val yesterdayKey = com.chronos.app.domain.NeuralStatsCalculator.formatDateKey(yesterdayCal)

        val stats = listOf(
            com.chronos.app.data.local.DailyNeuralStatEntity(
                dateKey = yesterdayKey,
                categoryName = com.chronos.app.data.local.PerformanceCategory.MENTAL.name,
                focusSeconds = 1800L,
                sessionsCount = 1
            ),
            com.chronos.app.data.local.DailyNeuralStatEntity(
                dateKey = todayKey,
                categoryName = com.chronos.app.data.local.PerformanceCategory.PHYSICAL.name,
                focusSeconds = 3600L,
                sessionsCount = 2
            ),
            com.chronos.app.data.local.DailyNeuralStatEntity(
                dateKey = todayKey,
                categoryName = com.chronos.app.data.local.PerformanceCategory.MENTAL.name,
                focusSeconds = 600L,
                sessionsCount = 1
            )
        )

        val summary = com.chronos.app.domain.NeuralStatsCalculator.computeStats(
            period = com.chronos.app.domain.StatsPeriod.LAST_7_DAYS,
            persistedStats = stats,
            performanceTimers = emptyList(),
            nowMillis = cal.timeInMillis
        )

        assertEquals(7, summary.days.size)
        assertEquals(100f, summary.totalFocusMinutes, 0.01f)
        assertEquals(com.chronos.app.data.local.PerformanceCategory.PHYSICAL, summary.dominantCategory)
        assertEquals(2, summary.activeStreakDays)
        assertTrue(summary.hasRecordedActivity)
    }
}
