package com.chronos.app.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chronos.app.ChronosApplication
import com.chronos.app.data.local.CustomTimerEntity
import com.chronos.app.data.local.DailyNeuralStatEntity
import com.chronos.app.data.local.FeedbackSettings
import com.chronos.app.data.local.OverclockSummary
import com.chronos.app.data.local.OverlaySettings
import com.chronos.app.data.local.PerformanceCategory
import com.chronos.app.data.local.PerformanceTimerEntity
import com.chronos.app.data.local.ResetPeriod
import com.chronos.app.domain.ChronosTimeEngine
import com.chronos.app.domain.DayPhase
import com.chronos.app.domain.NeuralStatsCalculator
import com.chronos.app.domain.NeuralStatsSummary
import com.chronos.app.domain.ProjectedTimelineNode
import com.chronos.app.domain.StatsPeriod
import com.chronos.app.theme.CyberThemeId
import com.chronos.app.theme.ResolvedCyberPalette
import com.chronos.app.theme.ThemeAppearanceMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PerformanceCardUiModel(
    val category: PerformanceCategory,
    val entity: PerformanceTimerEntity,
    val liveRemainingSeconds: Long,
    val progressFraction: Float
)

data class ChronosUiState(
    val nowMillis: Long = System.currentTimeMillis(),
    val dayResetFormatted: String = "00:00:00",
    val dayProgressFraction: Float = 0f,
    val dayPhase: DayPhase = DayPhase.NIGHT,
    val currentQuote: String = "",
    val performanceCards: List<PerformanceCardUiModel> = emptyList(),
    val projectedNodes: List<ProjectedTimelineNode> = emptyList(),
    val allCustomTimers: List<CustomTimerEntity> = emptyList(),
    val themeId: CyberThemeId = CyberThemeId.CYBERPUNK_CLASSIC,
    val appearanceMode: ThemeAppearanceMode = ThemeAppearanceMode.NIGHT,
    val palette: ResolvedCyberPalette = CyberThemeId.CYBERPUNK_CLASSIC.resolve(true),
    val overlaySettings: OverlaySettings = OverlaySettings(),
    val feedbackSettings: FeedbackSettings = FeedbackSettings(),
    val overclockSummary: OverclockSummary = OverclockSummary(),
    val statsPeriod: StatsPeriod = StatsPeriod.THIS_WEEK,
    val highlightedStatsCategory: PerformanceCategory? = null,
    val neuralStats: NeuralStatsSummary = NeuralStatsCalculator.computeStats(
        period = StatsPeriod.THIS_WEEK,
        persistedStats = emptyList(),
        performanceTimers = emptyList()
    )
)

class ChronosViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ChronosApplication
    private val repository = app.repository
    private val themeManager = app.themeManager
    private val settingsPrefs = app.settingsPrefs
    private val quotes = repository.getMotivationalQuotes()

    private val manualQuoteOffset = MutableStateFlow(0)
    private val statsPeriodFlow = MutableStateFlow(StatsPeriod.THIS_WEEK)
    private val highlightedStatsCategoryFlow = MutableStateFlow<PerformanceCategory?>(null)

    private val tickerFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1000L)
        }
    }

    private data class ThemeAndSettingsBundle(
        val themeId: CyberThemeId,
        val appearanceMode: ThemeAppearanceMode,
        val palette: ResolvedCyberPalette,
        val overlay: OverlaySettings,
        val feedback: FeedbackSettings,
        val quoteOffset: Int
    )

    private data class StatsInputBundle(
        val statsList: List<DailyNeuralStatEntity>,
        val period: StatsPeriod,
        val highlightedCategory: PerformanceCategory?
    )

    private val themeBundleFlow = combine(
        themeManager.currentTheme,
        themeManager.appearanceMode,
        themeManager.resolvedPalette,
        settingsPrefs.overlaySettings,
        settingsPrefs.feedbackSettings,
        manualQuoteOffset
    ) { values ->
        ThemeAndSettingsBundle(
            themeId = values[0] as CyberThemeId,
            appearanceMode = values[1] as ThemeAppearanceMode,
            palette = values[2] as ResolvedCyberPalette,
            overlay = values[3] as OverlaySettings,
            feedback = values[4] as FeedbackSettings,
            quoteOffset = values[5] as Int
        )
    }

    private val statsInputFlow = combine(
        repository.dailyNeuralStatsFlow,
        statsPeriodFlow,
        highlightedStatsCategoryFlow
    ) { statsList, period, highlighted ->
        StatsInputBundle(statsList, period, highlighted)
    }

    val uiState: StateFlow<ChronosUiState> = combine(
        tickerFlow,
        repository.performanceTimersFlow,
        repository.customTimersFlow,
        themeBundleFlow,
        statsInputFlow,
        settingsPrefs.overclockSummary
    ) { values ->
        val now = values[0] as Long
        @Suppress("UNCHECKED_CAST")
        val perfList = values[1] as List<PerformanceTimerEntity>
        @Suppress("UNCHECKED_CAST")
        val customList = values[2] as List<CustomTimerEntity>
        val themeBundle = values[3] as ThemeAndSettingsBundle
        val statsBundle = values[4] as StatsInputBundle
        val overclockSummary = values[5] as OverclockSummary

        val millisLeft = ChronosTimeEngine.millisUntilDayReset(now)
        val dayResetFormatted = ChronosTimeEngine.formatDurationHms(millisLeft / 1000L)
        val dayFrac = ChronosTimeEngine.dayProgressFraction(now)
        val phase = ChronosTimeEngine.currentDayPhase(now)

        val autoIndex = ((now / 12_000L) + themeBundle.quoteOffset).toInt().let { idx ->
            ((idx % quotes.size) + quotes.size) % quotes.size
        }

        val perfMap = perfList.associateBy { it.categoryName }
        val perfModels = PerformanceCategory.entries.map { cat ->
            val entity = perfMap[cat.name] ?: PerformanceTimerEntity(
                categoryName = cat.name,
                totalSeconds = cat.defaultDurationMinutes * 60L,
                remainingSeconds = cat.defaultDurationMinutes * 60L
            )
            val remaining = ChronosTimeEngine.computeLivePerformanceRemaining(entity, now)
            val frac = if (entity.totalSeconds > 0L) {
                (remaining.toFloat() / entity.totalSeconds.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            PerformanceCardUiModel(
                category = cat,
                entity = entity,
                liveRemainingSeconds = remaining,
                progressFraction = frac
            )
        }

        val projected = ChronosTimeEngine.projectTimersOnDayBar(customList, now)
        val neuralStats = NeuralStatsCalculator.computeStats(
            period = statsBundle.period,
            persistedStats = statsBundle.statsList,
            performanceTimers = perfList,
            nowMillis = now
        )

        ChronosUiState(
            nowMillis = now,
            dayResetFormatted = dayResetFormatted,
            dayProgressFraction = dayFrac,
            dayPhase = phase,
            currentQuote = quotes[autoIndex],
            performanceCards = perfModels,
            projectedNodes = projected,
            allCustomTimers = customList,
            themeId = themeBundle.themeId,
            appearanceMode = themeBundle.appearanceMode,
            palette = themeBundle.palette,
            overlaySettings = themeBundle.overlay,
            feedbackSettings = themeBundle.feedback,
            overclockSummary = overclockSummary,
            statsPeriod = statsBundle.period,
            highlightedStatsCategory = statsBundle.highlightedCategory,
            neuralStats = neuralStats
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = ChronosUiState(
            currentQuote = quotes.first(),
            themeId = themeManager.currentTheme.value,
            appearanceMode = themeManager.appearanceMode.value,
            palette = themeManager.resolvedPalette.value
        )
    )

    fun rotateQuote() {
        manualQuoteOffset.value = manualQuoteOffset.value + 1
    }

    fun selectStatsPeriod(period: StatsPeriod) {
        statsPeriodFlow.value = period
    }

    fun selectHighlightedStatsCategory(category: PerformanceCategory?) {
        highlightedStatsCategoryFlow.value = category
    }

    fun togglePerformanceTimer(category: PerformanceCategory) {
        viewModelScope.launch {
            repository.togglePerformanceTimer(category)
        }
    }

    fun resetPerformanceTimer(category: PerformanceCategory) {
        viewModelScope.launch {
            repository.resetPerformanceTimer(category)
        }
    }

    fun updatePerformanceDuration(category: PerformanceCategory, durationMinutes: Int) {
        viewModelScope.launch {
            repository.updatePerformanceDuration(category, durationMinutes)
        }
    }

    fun saveCustomTimer(
        id: Long = 0L,
        name: String,
        startMinuteOfDay: Int,
        durationMinutes: Int,
        activeDaysMask: Int,
        resetPeriod: ResetPeriod,
        privateNotes: String
    ) {
        viewModelScope.launch {
            repository.createOrUpdateCustomTimer(
                id = id,
                name = name,
                startMinuteOfDay = startMinuteOfDay,
                durationMinutes = durationMinutes,
                activeDaysMask = activeDaysMask,
                resetPeriod = resetPeriod,
                privateNotes = privateNotes
            )
        }
    }

    fun renameCustomTimer(id: Long, newName: String) {
        viewModelScope.launch {
            repository.renameCustomTimer(id, newName)
        }
    }

    fun updatePrivateNotes(id: Long, notes: String) {
        viewModelScope.launch {
            repository.updatePrivateNotes(id, notes)
        }
    }

    fun toggleCustomTimerRunning(id: Long) {
        viewModelScope.launch {
            repository.toggleCustomTimerRunning(id)
        }
    }

    fun deleteCustomTimer(id: Long) {
        viewModelScope.launch {
            repository.deleteCustomTimer(id)
        }
    }

    fun selectTheme(theme: CyberThemeId) {
        themeManager.setTheme(theme)
    }

    fun selectAppearanceMode(mode: ThemeAppearanceMode) {
        themeManager.setAppearanceMode(mode)
    }

    fun refreshAppearance(context: Context) {
        themeManager.refreshResolvedPalette(context)
    }

    fun updateOverlaySettings(transform: (OverlaySettings) -> OverlaySettings) {
        settingsPrefs.updateSettings(transform)
    }

    fun updateFeedbackSettings(transform: (FeedbackSettings) -> FeedbackSettings) {
        settingsPrefs.updateFeedbackSettings(transform)
    }
}
