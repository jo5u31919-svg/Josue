package com.chronos.app.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OverlaySettings(
    val overlayEnabled: Boolean = false,
    val showDayReset: Boolean = true,
    val showPerformanceTimers: Boolean = true,
    val showCustomTimers: Boolean = true,
    val maxSimultaneousTimers: Int = 3
)

data class OverclockSummary(
    val totalPoints: Int = 0,
    val completedBlocks: Int = 0
)

data class FeedbackSettings(
    val soundEffectsEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true
)

class ChronosSettingsPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _overlaySettings = MutableStateFlow(readSettings())
    val overlaySettings: StateFlow<OverlaySettings> = _overlaySettings.asStateFlow()

    private val _overclockSummary = MutableStateFlow(readOverclockSummary())
    val overclockSummary: StateFlow<OverclockSummary> = _overclockSummary.asStateFlow()

    private val _feedbackSettings = MutableStateFlow(readFeedbackSettings())
    val feedbackSettings: StateFlow<FeedbackSettings> = _feedbackSettings.asStateFlow()

    private fun readSettings(): OverlaySettings {
        return OverlaySettings(
            overlayEnabled = prefs.getBoolean(KEY_OVERLAY_ENABLED, false),
            showDayReset = prefs.getBoolean(KEY_SHOW_DAY_RESET, true),
            showPerformanceTimers = prefs.getBoolean(KEY_SHOW_PERFORMANCE, true),
            showCustomTimers = prefs.getBoolean(KEY_SHOW_CUSTOM, true),
            maxSimultaneousTimers = prefs.getInt(KEY_MAX_SIMULTANEOUS, 3).coerceIn(1, 6)
        )
    }

    private fun readOverclockSummary(): OverclockSummary {
        return OverclockSummary(
            totalPoints = prefs.getInt(KEY_OVERCLOCK_POINTS, 0).coerceAtLeast(0),
            completedBlocks = prefs.getInt(KEY_OVERCLOCK_BLOCKS, 0).coerceAtLeast(0)
        )
    }

    private fun readFeedbackSettings(): FeedbackSettings {
        return FeedbackSettings(
            soundEffectsEnabled = prefs.getBoolean(KEY_SFX_ENABLED, true),
            hapticsEnabled = prefs.getBoolean(KEY_HAPTICS_ENABLED, true)
        )
    }

    fun updateSettings(transform: (OverlaySettings) -> OverlaySettings) {
        val updated = transform(_overlaySettings.value)
        prefs.edit()
            .putBoolean(KEY_OVERLAY_ENABLED, updated.overlayEnabled)
            .putBoolean(KEY_SHOW_DAY_RESET, updated.showDayReset)
            .putBoolean(KEY_SHOW_PERFORMANCE, updated.showPerformanceTimers)
            .putBoolean(KEY_SHOW_CUSTOM, updated.showCustomTimers)
            .putInt(KEY_MAX_SIMULTANEOUS, updated.maxSimultaneousTimers.coerceIn(1, 6))
            .apply()
        _overlaySettings.value = updated
    }

    fun updateFeedbackSettings(transform: (FeedbackSettings) -> FeedbackSettings) {
        val updated = transform(_feedbackSettings.value)
        prefs.edit()
            .putBoolean(KEY_SFX_ENABLED, updated.soundEffectsEnabled)
            .putBoolean(KEY_HAPTICS_ENABLED, updated.hapticsEnabled)
            .apply()
        _feedbackSettings.value = updated
    }

    fun recordOverclockPoints(addedPoints: Int) {
        val current = readOverclockSummary()
        val updated = OverclockSummary(
            totalPoints = current.totalPoints + addedPoints.coerceAtLeast(0),
            completedBlocks = current.completedBlocks + 1
        )
        prefs.edit()
            .putInt(KEY_OVERCLOCK_POINTS, updated.totalPoints)
            .putInt(KEY_OVERCLOCK_BLOCKS, updated.completedBlocks)
            .apply()
        _overclockSummary.value = updated
    }

    fun currentSnapshot(): OverlaySettings = readSettings()

    fun currentFeedbackSnapshot(): FeedbackSettings = readFeedbackSettings()

    companion object {
        private const val PREFS_NAME = "chronos_overlay_prefs"
        private const val KEY_OVERLAY_ENABLED = "overlay_enabled"
        private const val KEY_SHOW_DAY_RESET = "show_day_reset"
        private const val KEY_SHOW_PERFORMANCE = "show_performance"
        private const val KEY_SHOW_CUSTOM = "show_custom"
        private const val KEY_MAX_SIMULTANEOUS = "max_simultaneous"
        private const val KEY_OVERCLOCK_POINTS = "overclock_total_points"
        private const val KEY_OVERCLOCK_BLOCKS = "overclock_completed_blocks"
        private const val KEY_SFX_ENABLED = "feedback_sfx_enabled"
        private const val KEY_HAPTICS_ENABLED = "feedback_haptics_enabled"
    }
}
