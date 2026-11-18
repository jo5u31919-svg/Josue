package com.chronos.app.ui.fragment

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chronos.app.R
import com.chronos.app.data.local.PerformanceCategory
import com.chronos.app.databinding.FragmentStatsBinding
import com.chronos.app.domain.NeuralStatsCalculator
import com.chronos.app.domain.StatsPeriod
import com.chronos.app.feedback.CyberFeedbackManager
import com.chronos.app.theme.ResolvedCyberPalette
import com.chronos.app.ui.custom.NeuralPulseChartView
import com.chronos.app.ui.viewmodel.ChronosUiState
import com.chronos.app.ui.viewmodel.ChronosViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ChronosViewModel
    private var lastPeriodRendered: StatsPeriod? = null
    private var lastPaletteApplied: ResolvedCyberPalette? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[ChronosViewModel::class.java]

        setupSelectors()
        observeUiState()
    }

    private fun setupSelectors() {
        val currentState = viewModel.uiState.value
        binding.chipGroupPeriod.check(
            when (currentState.statsPeriod) {
                StatsPeriod.THIS_WEEK -> R.id.chipPeriodThisWeek
                StatsPeriod.LAST_7_DAYS -> R.id.chipPeriodLast7Days
            }
        )

        binding.chipGroupPeriod.setOnCheckedStateChangeListener { _, checkedIds ->
            val selectedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val period = when (selectedId) {
                R.id.chipPeriodLast7Days -> StatsPeriod.LAST_7_DAYS
                else -> StatsPeriod.THIS_WEEK
            }
            if (period != viewModel.uiState.value.statsPeriod) {
                CyberFeedbackManager.getInstance(requireContext()).playTerminalClick()
                viewModel.selectStatsPeriod(period)
            }
        }

        val initialFilterId = when (currentState.highlightedStatsCategory) {
            PerformanceCategory.MENTAL -> R.id.chipFilterMental
            PerformanceCategory.PHYSICAL -> R.id.chipFilterPhysical
            PerformanceCategory.EXPRESSIVE -> R.id.chipFilterExpressive
            PerformanceCategory.OCCUPATIONAL -> R.id.chipFilterOccupational
            null -> R.id.chipFilterAll
        }
        binding.chipGroupCategoryFilter.check(initialFilterId)

        binding.chipGroupCategoryFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            val selectedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val category = when (selectedId) {
                R.id.chipFilterMental -> PerformanceCategory.MENTAL
                R.id.chipFilterPhysical -> PerformanceCategory.PHYSICAL
                R.id.chipFilterExpressive -> PerformanceCategory.EXPRESSIVE
                R.id.chipFilterOccupational -> PerformanceCategory.OCCUPATIONAL
                else -> null
            }
            if (category != viewModel.uiState.value.highlightedStatsCategory) {
                CyberFeedbackManager.getInstance(requireContext()).playTerminalClick()
                viewModel.selectHighlightedStatsCategory(category)
            }
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderStats(state)
                }
            }
        }
    }

    private fun renderStats(state: ChronosUiState) {
        val palette = state.palette
        val stats = state.neuralStats

        if (lastPaletteApplied != palette) {
            lastPaletteApplied = palette
            applyPalette(palette)
        }

        val periodChanged = lastPeriodRendered != stats.period
        lastPeriodRendered = stats.period

        binding.tvTotalFocusValue.text = NeuralStatsCalculator.formatMinutesCompact(stats.totalFocusMinutes)
        binding.tvTotalFocusPeriodTag.text = when (stats.period) {
            StatsPeriod.THIS_WEEK -> "SEMANA"
            StatsPeriod.LAST_7_DAYS -> "7 DÍAS"
        }

        val dominant = stats.dominantCategory
        if (dominant != null) {
            val dominantBreakdown = stats.categoryBreakdowns.firstOrNull { it.category == dominant }
            val dominantPct = ((dominantBreakdown?.shareFraction ?: 0f) * 100f).roundToInt()
            val dominantColor = NeuralPulseChartView.colorForCategory(dominant, palette)
            binding.tvDominantCategoryValue.text = dominant.title.uppercase()
            binding.tvDominantCategoryValue.setTextColor(dominantColor)
            binding.tvDominantCategorySub.text =
                "${NeuralStatsCalculator.formatMinutesCompact(dominantBreakdown?.totalMinutes ?: 0f)} · $dominantPct%"
            binding.tvDominantCategorySub.setTextColor(dominantColor)
        } else {
            binding.tvDominantCategoryValue.text = "SIN DATOS"
            binding.tvDominantCategoryValue.setTextColor(palette.textPrimary)
            binding.tvDominantCategorySub.text = "0 sesiones"
            binding.tvDominantCategorySub.setTextColor(palette.textSecondary)
        }

        val streakLabel = if (stats.activeStreakDays == 1) "1 DÍA" else "${stats.activeStreakDays} DÍAS"
        binding.tvActiveStreakValue.text = streakLabel
        binding.tvActiveStreakSub.text = "${stats.activeCategoriesCount} activos ahora"

        binding.tvEmptyTelemetryNotice.visibility = if (stats.hasRecordedActivity) View.GONE else View.VISIBLE

        binding.neuralPulseChart.setChartData(
            days = stats.days,
            palette = palette,
            highlightedCategory = state.highlightedStatsCategory,
            animateWaves = periodChanged
        )

        stats.categoryBreakdowns.forEach { item ->
            val pct = (item.shareFraction * 100f).roundToInt()
            val formatted = "${NeuralStatsCalculator.formatMinutesCompact(item.totalMinutes)} · $pct%"
            val color = NeuralPulseChartView.colorForCategory(item.category, palette)
            val progressVal = (item.shareFraction * 1000f).roundToInt().coerceIn(0, 1000)

            when (item.category) {
                PerformanceCategory.MENTAL -> {
                    binding.tvRowMentalValue.text = formatted
                    binding.tvRowMentalValue.setTextColor(color)
                    binding.progressRowMental.progress = progressVal
                    binding.progressRowMental.progressTintList = ColorStateList.valueOf(color)
                }
                PerformanceCategory.PHYSICAL -> {
                    binding.tvRowPhysicalValue.text = formatted
                    binding.tvRowPhysicalValue.setTextColor(color)
                    binding.progressRowPhysical.progress = progressVal
                    binding.progressRowPhysical.progressTintList = ColorStateList.valueOf(color)
                }
                PerformanceCategory.EXPRESSIVE -> {
                    binding.tvRowExpressiveValue.text = formatted
                    binding.tvRowExpressiveValue.setTextColor(color)
                    binding.progressRowExpressive.progress = progressVal
                    binding.progressRowExpressive.progressTintList = ColorStateList.valueOf(color)
                }
                PerformanceCategory.OCCUPATIONAL -> {
                    binding.tvRowOccupationalValue.text = formatted
                    binding.tvRowOccupationalValue.setTextColor(color)
                    binding.progressRowOccupational.progress = progressVal
                    binding.progressRowOccupational.progressTintList = ColorStateList.valueOf(color)
                }
            }
        }
    }

    private fun applyPalette(palette: ResolvedCyberPalette) {
        binding.tvStatsTag.setTextColor(palette.primaryNeon)
        binding.tvStatsTag.backgroundTintList = ColorStateList.valueOf(palette.capsuleFillColor)
        binding.tvStatsTitle.setTextColor(palette.textPrimary)
        binding.tvStatsSubtitle.setTextColor(palette.textSecondary)
        binding.tvStatsLiveBadge.setTextColor(palette.secondaryNeon)
        binding.tvStatsLiveBadge.backgroundTintList = ColorStateList.valueOf(palette.capsuleFillColor)

        binding.cardTotalFocus.setCardBackgroundColor(palette.glassSurfaceColor)
        binding.cardTotalFocus.strokeColor = palette.rimHighlightColor
        binding.ivTotalFocus.imageTintList = ColorStateList.valueOf(palette.primaryNeon)
        binding.badgeTotalFocus.backgroundTintList = ColorStateList.valueOf(
            Color.argb(if (palette.isDark) 42 else 28, Color.red(palette.primaryNeon), Color.green(palette.primaryNeon), Color.blue(palette.primaryNeon))
        )
        binding.tvTotalFocusLabel.setTextColor(palette.textSecondary)
        binding.tvTotalFocusValue.setTextColor(palette.primaryNeon)
        binding.tvTotalFocusPeriodTag.setTextColor(palette.textPrimary)
        binding.tvTotalFocusPeriodTag.backgroundTintList = ColorStateList.valueOf(palette.capsuleFillColor)

        binding.cardDominantCategory.setCardBackgroundColor(palette.glassSurfaceColor)
        binding.cardDominantCategory.strokeColor = palette.subtleBorderColor
        binding.ivDominantCategory.imageTintList = ColorStateList.valueOf(palette.secondaryNeon)
        binding.tvDominantLabel.setTextColor(palette.textSecondary)

        binding.cardActiveStreak.setCardBackgroundColor(palette.glassSurfaceColor)
        binding.cardActiveStreak.strokeColor = palette.subtleBorderColor
        binding.ivActiveStreak.imageTintList = ColorStateList.valueOf(palette.tertiaryNeon)
        binding.tvStreakLabel.setTextColor(palette.textSecondary)
        binding.tvActiveStreakValue.setTextColor(palette.textPrimary)
        binding.tvActiveStreakSub.setTextColor(
            NeuralPulseChartView.colorForCategory(PerformanceCategory.EXPRESSIVE, palette)
        )

        binding.cardNeuralChart.setCardBackgroundColor(palette.glassSurfaceColor)
        binding.cardNeuralChart.strokeColor = palette.rimHighlightColor
        binding.tvChartHeader.setTextColor(palette.textPrimary)
        binding.tvChartHint.setTextColor(palette.textMuted)
        binding.tvEmptyTelemetryNotice.setTextColor(palette.textSecondary)
        binding.tvEmptyTelemetryNotice.backgroundTintList = ColorStateList.valueOf(palette.capsuleFillColor)

        val cMental = NeuralPulseChartView.colorForCategory(PerformanceCategory.MENTAL, palette)
        val cPhysical = NeuralPulseChartView.colorForCategory(PerformanceCategory.PHYSICAL, palette)
        val cExpressive = NeuralPulseChartView.colorForCategory(PerformanceCategory.EXPRESSIVE, palette)
        val cOccupational = NeuralPulseChartView.colorForCategory(PerformanceCategory.OCCUPATIONAL, palette)

        binding.dotLegendMental.backgroundTintList = ColorStateList.valueOf(cMental)
        binding.dotLegendPhysical.backgroundTintList = ColorStateList.valueOf(cPhysical)
        binding.dotLegendExpressive.backgroundTintList = ColorStateList.valueOf(cExpressive)
        binding.dotLegendOccupational.backgroundTintList = ColorStateList.valueOf(cOccupational)

        binding.tvLegendMental.setTextColor(palette.textSecondary)
        binding.tvLegendPhysical.setTextColor(palette.textSecondary)
        binding.tvLegendExpressive.setTextColor(palette.textSecondary)
        binding.tvLegendOccupational.setTextColor(palette.textSecondary)

        binding.cardCategoryBreakdown.setCardBackgroundColor(palette.glassSurfaceColor)
        binding.cardCategoryBreakdown.strokeColor = palette.subtleBorderColor
        binding.tvBreakdownHeader.setTextColor(palette.textPrimary)

        binding.tvRowMentalLabel.setTextColor(palette.textPrimary)
        binding.tvRowPhysicalLabel.setTextColor(palette.textPrimary)
        binding.tvRowExpressiveLabel.setTextColor(palette.textPrimary)
        binding.tvRowOccupationalLabel.setTextColor(palette.textPrimary)

        val trackTint = ColorStateList.valueOf(palette.surfaceElevatedColor)
        binding.progressRowMental.progressBackgroundTintList = trackTint
        binding.progressRowPhysical.progressBackgroundTintList = trackTint
        binding.progressRowExpressive.progressBackgroundTintList = trackTint
        binding.progressRowOccupational.progressBackgroundTintList = trackTint
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
