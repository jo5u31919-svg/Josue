package com.chronos.app.data.repository

import com.chronos.app.data.local.ChronosDatabase
import com.chronos.app.data.local.CustomTimerDao
import com.chronos.app.data.local.CustomTimerEntity
import com.chronos.app.data.local.DailyNeuralStatDao
import com.chronos.app.data.local.DailyNeuralStatEntity
import com.chronos.app.data.local.PerformanceCategory
import com.chronos.app.data.local.PerformanceTimerDao
import com.chronos.app.data.local.PerformanceTimerEntity
import com.chronos.app.data.local.ResetPeriod
import com.chronos.app.domain.ChronosTimeEngine
import com.chronos.app.domain.NeuralStatsCalculator
import kotlinx.coroutines.flow.Flow

class ChronosRepository(
    private val customTimerDao: CustomTimerDao,
    private val performanceTimerDao: PerformanceTimerDao,
    private val dailyNeuralStatDao: DailyNeuralStatDao
) {
    val customTimersFlow: Flow<List<CustomTimerEntity>> = customTimerDao.observeAll()
    val performanceTimersFlow: Flow<List<PerformanceTimerEntity>> = performanceTimerDao.observeAll()
    val dailyNeuralStatsFlow: Flow<List<DailyNeuralStatEntity>> = dailyNeuralStatDao.observeAll()

    suspend fun ensureSeeded() {
        ChronosDatabase.seedPerformanceTimers(performanceTimerDao)
    }

    suspend fun getCustomTimersSnapshot(): List<CustomTimerEntity> =
        customTimerDao.getAllSnapshot()

    suspend fun getPerformanceTimersSnapshot(): List<PerformanceTimerEntity> {
        ensureSeeded()
        return performanceTimerDao.getAllSnapshot()
    }

    suspend fun getCustomTimerById(id: Long): CustomTimerEntity? =
        customTimerDao.getById(id)

    suspend fun createOrUpdateCustomTimer(
        id: Long = 0L,
        name: String,
        startMinuteOfDay: Int,
        durationMinutes: Int,
        activeDaysMask: Int,
        resetPeriod: ResetPeriod,
        privateNotes: String
    ) {
        val now = System.currentTimeMillis()
        val durationSec = durationMinutes.coerceAtLeast(1) * 60L
        if (id == 0L) {
            customTimerDao.insert(
                CustomTimerEntity(
                    name = name.trim(),
                    startMinuteOfDay = startMinuteOfDay.coerceIn(0, 1439),
                    durationMinutes = durationMinutes.coerceAtLeast(1),
                    activeDaysMask = activeDaysMask,
                    resetPeriod = resetPeriod.name,
                    privateNotes = privateNotes.trim(),
                    isRunning = false,
                    remainingSeconds = durationSec,
                    lastTickEpochMs = now
                )
            )
        } else {
            val existing = customTimerDao.getById(id)
            if (existing != null) {
                customTimerDao.update(
                    existing.copy(
                        name = name.trim(),
                        startMinuteOfDay = startMinuteOfDay.coerceIn(0, 1439),
                        durationMinutes = durationMinutes.coerceAtLeast(1),
                        activeDaysMask = activeDaysMask,
                        resetPeriod = resetPeriod.name,
                        privateNotes = privateNotes.trim(),
                        remainingSeconds = durationSec,
                        isRunning = false,
                        lastTickEpochMs = now
                    )
                )
            }
        }
    }

    suspend fun renameCustomTimer(id: Long, newName: String) {
        val existing = customTimerDao.getById(id) ?: return
        if (newName.isBlank()) return
        customTimerDao.update(existing.copy(name = newName.trim()))
    }

    suspend fun updatePrivateNotes(id: Long, notes: String) {
        val existing = customTimerDao.getById(id) ?: return
        customTimerDao.update(existing.copy(privateNotes = notes.trim()))
    }

    suspend fun toggleCustomTimerRunning(id: Long) {
        val existing = customTimerDao.getById(id) ?: return
        val now = System.currentTimeMillis()
        if (existing.isRunning) {
            val currentRemaining = ChronosTimeEngine.computeLiveRemainingSeconds(
                existing.remainingSeconds,
                true,
                existing.lastTickEpochMs,
                now
            )
            customTimerDao.update(
                existing.copy(
                    isRunning = false,
                    remainingSeconds = currentRemaining,
                    lastTickEpochMs = now
                )
            )
        } else {
            val resetIfFinished = if (existing.remainingSeconds <= 0L) {
                existing.durationMinutes * 60L
            } else {
                existing.remainingSeconds
            }
            customTimerDao.update(
                existing.copy(
                    isRunning = true,
                    remainingSeconds = resetIfFinished,
                    lastTickEpochMs = now
                )
            )
        }
    }

    suspend fun deleteCustomTimer(id: Long) {
        customTimerDao.deleteById(id)
    }

    suspend fun togglePerformanceTimer(category: PerformanceCategory) {
        ensureSeeded()
        val existing = performanceTimerDao.getByCategory(category.name) ?: return
        val now = System.currentTimeMillis()
        if (existing.isRunning) {
            val elapsed = ((now - existing.lastTickEpochMs) / 1000L)
                .coerceIn(0L, existing.remainingSeconds.coerceAtLeast(0L))
            val remaining = ChronosTimeEngine.computeLivePerformanceRemaining(existing, now)
            val completedDelta = if (remaining == 0L && elapsed > 0L) 1 else 0
            if (elapsed > 0L) {
                dailyNeuralStatDao.addFocusSeconds(
                    dateKey = NeuralStatsCalculator.formatDateKey(now),
                    categoryName = category.name,
                    deltaSeconds = elapsed,
                    incrementSession = completedDelta,
                    nowMs = now
                )
            }
            performanceTimerDao.update(
                existing.copy(
                    isRunning = false,
                    remainingSeconds = remaining,
                    lastTickEpochMs = now,
                    completedSessions = existing.completedSessions + completedDelta
                )
            )
        } else {
            val baseRemaining = if (existing.remainingSeconds <= 0L) {
                existing.totalSeconds
            } else {
                existing.remainingSeconds
            }
            performanceTimerDao.update(
                existing.copy(
                    isRunning = true,
                    remainingSeconds = baseRemaining,
                    lastTickEpochMs = now
                )
            )
        }
    }

    suspend fun resetPerformanceTimer(category: PerformanceCategory) {
        ensureSeeded()
        val existing = performanceTimerDao.getByCategory(category.name) ?: return
        val now = System.currentTimeMillis()
        if (existing.isRunning) {
            val elapsed = ((now - existing.lastTickEpochMs) / 1000L)
                .coerceIn(0L, existing.remainingSeconds.coerceAtLeast(0L))
            if (elapsed > 0L) {
                dailyNeuralStatDao.addFocusSeconds(
                    dateKey = NeuralStatsCalculator.formatDateKey(now),
                    categoryName = category.name,
                    deltaSeconds = elapsed,
                    incrementSession = 0,
                    nowMs = now
                )
            }
        }
        performanceTimerDao.update(
            existing.copy(
                isRunning = false,
                remainingSeconds = existing.totalSeconds,
                lastTickEpochMs = now
            )
        )
    }

    suspend fun updatePerformanceDuration(category: PerformanceCategory, durationMinutes: Int) {
        ensureSeeded()
        val existing = performanceTimerDao.getByCategory(category.name) ?: return
        val secs = durationMinutes.coerceIn(1, 360) * 60L
        performanceTimerDao.update(
            existing.copy(
                totalSeconds = secs,
                remainingSeconds = secs,
                isRunning = false,
                lastTickEpochMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun completeOverclockSession(
        durationMinutes: Int,
        pauseCount: Int
    ): OverclockReward {
        ensureSeeded()
        val safeMinutes = durationMinutes.coerceAtLeast(1)
        val totalSeconds = safeMinutes * 60L
        val now = System.currentTimeMillis()
        val dateKey = NeuralStatsCalculator.formatDateKey(now)

        val stabilityMultiplier = (1f - (pauseCount.coerceAtLeast(0) * 0.10f)).coerceAtLeast(0.50f)
        val totalPoints = ((safeMinutes * 4) * stabilityMultiplier).toInt().coerceAtLeast(4)

        val weights = mapOf(
            PerformanceCategory.MENTAL to 0.35f,
            PerformanceCategory.OCCUPATIONAL to 0.30f,
            PerformanceCategory.PHYSICAL to 0.20f,
            PerformanceCategory.EXPRESSIVE to 0.15f
        )

        val categoryPoints = linkedMapOf<PerformanceCategory, Int>()
        var allocatedSeconds = 0L
        val categories = PerformanceCategory.entries
        categories.forEachIndexed { index, category ->
            val w = weights[category] ?: 0.25f
            val pts = (totalPoints * w).toInt().coerceAtLeast(1)
            categoryPoints[category] = pts

            val secForCategory = if (index == categories.lastIndex) {
                (totalSeconds - allocatedSeconds).coerceAtLeast(1L)
            } else {
                (totalSeconds * w).toLong().coerceAtLeast(1L)
            }
            allocatedSeconds += secForCategory

            dailyNeuralStatDao.addFocusSeconds(
                dateKey = dateKey,
                categoryName = category.name,
                deltaSeconds = secForCategory,
                incrementSession = 1,
                nowMs = now
            )

            performanceTimerDao.getByCategory(category.name)?.let { existing ->
                performanceTimerDao.update(
                    existing.copy(completedSessions = existing.completedSessions + 1)
                )
            }
        }

        return OverclockReward(
            totalPoints = categoryPoints.values.sum(),
            stabilityPercent = (stabilityMultiplier * 100f).toInt(),
            categoryPoints = categoryPoints
        )
    }

    fun getMotivationalQuotes(): List<String> = listOf(
        "“Cada segundo en la red es energía pura; ejecútalo con precisión quirúrgica.”",
        "“El tiempo no se detiene ante la duda. Sincroniza tu mente y avanza.”",
        "“Disciplina neural hoy es libertad absoluta antes del próximo reinicio.”",
        "“No persigas el reloj: conviértete en el pulso que marca el ritmo del sistema.”",
        "“Pequeños nodos de enfoque sostenido construyen imperios silenciosos.”",
        "“Cuando el día se reinicie a medianoche, deja tus excusas en el ciclo anterior.”",
        "“Tu atención es el recurso más valioso del sector. Protégela con fuego neón.”"
    )
}

data class OverclockReward(
    val totalPoints: Int,
    val stabilityPercent: Int,
    val categoryPoints: Map<PerformanceCategory, Int>
)
