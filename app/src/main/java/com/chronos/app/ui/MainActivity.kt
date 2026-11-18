package com.chronos.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.chronos.app.R
import com.chronos.app.data.local.CustomTimerEntity
import com.chronos.app.data.local.PerformanceCategory
import com.chronos.app.databinding.ActivityMainBinding
import com.chronos.app.databinding.ItemPerformanceTimerBinding
import com.chronos.app.domain.ChronosTimeEngine
import com.chronos.app.feedback.CyberFeedbackManager
import com.chronos.app.service.FloatingOverlayService
import com.chronos.app.theme.ResolvedCyberPalette
import com.chronos.app.ui.adapter.CustomTimerAdapter
import com.chronos.app.ui.dialog.CustomTimerDialogFragment
import com.chronos.app.ui.dialog.NodePopupDialogFragment
import com.chronos.app.ui.dialog.SettingsDialogFragment
import com.chronos.app.ui.fragment.StatsFragment
import com.chronos.app.ui.viewmodel.ChronosUiState
import com.chronos.app.ui.viewmodel.ChronosViewModel
import com.chronos.app.ui.viewmodel.PerformanceCardUiModel
import com.chronos.app.widget.ChronosWidgetProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: ChronosViewModel by viewModels()
    private lateinit var customTimerAdapter: CustomTimerAdapter

    private var lastPaletteApplied: ResolvedCyberPalette? = null
    private var activeNavItemId: Int = R.id.nav_home
    private val feedback by lazy { CyberFeedbackManager.getInstance(this) }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            viewModel.updateOverlaySettings { it.copy(overlayEnabled = true) }
            startOverlayWithNotificationCheck()
        } else {
            Toast.makeText(this, "Permiso de superposición requerido para la burbuja flotante", Toast.LENGTH_SHORT).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        FloatingOverlayService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyEdgeToEdgeInsets()
        setupRecyclerView()
        setupBottomNavigation(savedInstanceState)
        setupListeners()
        observeUiState()

        if (savedInstanceState == null) {
            binding.mainContentCoordinator.alpha = 0f
            binding.hackerSplashView.onSplashFinished = {
                binding.mainContentCoordinator.animate()
                    .alpha(1f)
                    .setDuration(420L)
                    .start()
            }
            binding.hackerSplashView.startBootSequence()
        } else {
            binding.hackerSplashView.visibility = View.GONE
            binding.mainContentCoordinator.alpha = 1f
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_ACTIVE_NAV_ID, activeNavItemId)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        viewModel.refreshAppearance(this)
    }

    private fun applyEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainScrollView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                (92 * resources.displayMetrics.density).toInt()
            )
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.fragmentContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupBottomNavigation(savedInstanceState: Bundle?) {
        activeNavItemId = savedInstanceState?.getInt(KEY_ACTIVE_NAV_ID, R.id.nav_home) ?: R.id.nav_home

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (activeNavItemId != R.id.nav_home) {
                    binding.bottomNavigation.selectedItemId = R.id.nav_home
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    if (activeNavItemId != R.id.nav_home) feedback.playTerminalClick()
                    switchToHomeTab()
                    true
                }
                R.id.nav_stats -> {
                    if (activeNavItemId != R.id.nav_stats) feedback.playTerminalClick()
                    switchToStatsTab()
                    true
                }
                else -> false
            }
        }

        binding.bottomNavigation.selectedItemId = activeNavItemId
        if (activeNavItemId == R.id.nav_stats) {
            switchToStatsTab()
        } else {
            switchToHomeTab()
        }
    }

    private fun switchToHomeTab() {
        activeNavItemId = R.id.nav_home
        binding.fragmentContainer.visibility = View.GONE
        binding.mainScrollView.visibility = View.VISIBLE
        binding.fabAddTimer.show()
    }

    private fun switchToStatsTab() {
        activeNavItemId = R.id.nav_stats
        binding.mainScrollView.visibility = View.GONE
        binding.fabAddTimer.hide()
        binding.fragmentContainer.visibility = View.VISIBLE
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, StatsFragment(), TAG_STATS_FRAGMENT)
            .commit()
    }

    private fun setupRecyclerView() {
        customTimerAdapter = CustomTimerAdapter { node ->
            openNodePopup(node.timer.id)
        }
        binding.rvCustomTimers.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = customTimerAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupListeners() {
        binding.cardMotivationalQuote.setOnClickListener {
            feedback.playTerminalClick()
            viewModel.rotateQuote()
        }

        binding.laserTimelineView.onNodeSelected = { node ->
            openNodePopup(node.timer.id)
        }

        binding.fabAddTimer.setOnClickListener {
            feedback.playTerminalClick()
            openCreateOrEditCustomTimer(null)
        }

        binding.btnSettings.setOnClickListener {
            feedback.playTerminalClick()
            openSettingsDialog()
        }

        binding.cardOverclockLauncher.setOnClickListener {
            feedback.playBeepProgress()
            launchOverclockMode()
        }

        binding.btnFloatingToggle.setOnClickListener {
            val currentlyEnabled = viewModel.uiState.value.overlaySettings.overlayEnabled
            if (currentlyEnabled) {
                feedback.playTerminalClick()
                viewModel.updateOverlaySettings { it.copy(overlayEnabled = false) }
                FloatingOverlayService.stop(this)
                Toast.makeText(this, "Burbuja flotante desactivada", Toast.LENGTH_SHORT).show()
            } else {
                feedback.playBeepProgress()
                if (Settings.canDrawOverlays(this)) {
                    viewModel.updateOverlaySettings { it.copy(overlayEnabled = true) }
                    startOverlayWithNotificationCheck()
                } else {
                    requestOverlayPermission()
                }
            }
        }
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    private fun renderState(state: ChronosUiState) {
        val palette = state.palette
        if (lastPaletteApplied != palette) {
            lastPaletteApplied = palette
            applyThemePalette(palette)
            ChronosWidgetProvider.requestRefresh(this)
        }

        binding.tvMotivationalQuote.text = state.currentQuote
        binding.tvDayPhaseBadge.text = "FASE: ${state.dayPhase.label}"
        binding.tvDayResetCountdown.text = state.dayResetFormatted

        binding.laserTimelineView.updateTimeline(
            progress = state.dayProgressFraction,
            phase = state.dayPhase,
            nodes = state.projectedNodes,
            palette = palette
        )

        val overlayTint = if (state.overlaySettings.overlayEnabled) {
            palette.secondaryNeon
        } else {
            palette.primaryNeon
        }
        binding.btnFloatingToggle.imageTintList = ColorStateList.valueOf(overlayTint)

        val cardBindings = mapOf(
            PerformanceCategory.MENTAL to binding.cardMental,
            PerformanceCategory.PHYSICAL to binding.cardPhysical,
            PerformanceCategory.EXPRESSIVE to binding.cardExpressive,
            PerformanceCategory.OCCUPATIONAL to binding.cardOccupational
        )
        state.performanceCards.forEach { model ->
            val cardBinding = cardBindings[model.category] ?: return@forEach
            bindPerformanceCard(cardBinding, model, palette)
        }

        customTimerAdapter.submitList(state.projectedNodes)
        val activeNodesCount = state.projectedNodes.count { it.isCurrentlyActiveWindow }
        binding.tvCustomNodeCount.text = "${state.projectedNodes.size} NODOS · $activeNodesCount ACTIVO"
        binding.tvEmptyCustomTimers.visibility = if (state.projectedNodes.isEmpty()) View.VISIBLE else View.GONE
        binding.rvCustomTimers.visibility = if (state.projectedNodes.isEmpty()) View.GONE else View.VISIBLE

        binding.tvOverclockPointsBadge.text = "${state.overclockSummary.totalPoints} PTS"
    }

    private fun bindPerformanceCard(
        card: ItemPerformanceTimerBinding,
        model: PerformanceCardUiModel,
        palette: ResolvedCyberPalette
    ) {
        val cat = model.category
        val isRunning = model.entity.isRunning

        card.ivPerfIcon.setImageResource(cat.iconRes)
        card.tvPerfName.text = cat.title.uppercase()
        card.tvPerfName.setTextColor(palette.textPrimary)

        val accentColor = when (cat) {
            PerformanceCategory.MENTAL -> palette.primaryNeon
            PerformanceCategory.PHYSICAL -> palette.secondaryNeon
            PerformanceCategory.EXPRESSIVE -> palette.tertiaryNeon
            PerformanceCategory.OCCUPATIONAL -> palette.primaryNeon
        }

        card.ivPerfIcon.imageTintList = ColorStateList.valueOf(accentColor)
        card.badgePerfIcon.backgroundTintList = ColorStateList.valueOf(
            Color.argb(if (palette.isDark) 40 else 28, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        )
        card.tvPerfCountdown.text = ChronosTimeEngine.formatDurationMs(model.liveRemainingSeconds)
        card.tvPerfCountdown.setTextColor(if (isRunning) accentColor else palette.textPrimary)

        card.tvPerfStatus.text = if (isRunning) "ACTIVO" else cat.subtitle.uppercase()
        card.tvPerfStatus.setTextColor(if (isRunning) accentColor else palette.textSecondary)

        card.progressPerf.progress = (model.progressFraction * 1000).toInt()
        card.progressPerf.progressTintList = ColorStateList.valueOf(accentColor)
        card.progressPerf.progressBackgroundTintList = ColorStateList.valueOf(palette.surfaceElevatedColor)

        card.cardPerfRoot.setCardBackgroundColor(palette.glassSurfaceColor)
        card.cardPerfRoot.strokeColor = if (isRunning) accentColor else palette.rimHighlightColor

        card.btnPerfPlayPause.setImageResource(if (isRunning) R.drawable.ic_pause else R.drawable.ic_play)
        card.btnPerfPlayPause.imageTintList = ColorStateList.valueOf(accentColor)
        card.btnPerfPlayPause.backgroundTintList = ColorStateList.valueOf(palette.capsuleFillColor)
        card.btnPerfReset.imageTintList = ColorStateList.valueOf(palette.textSecondary)
        card.btnPerfConfig.imageTintList = ColorStateList.valueOf(palette.secondaryNeon)

        card.btnPerfPlayPause.setOnClickListener {
            feedback.playBeepProgress()
            viewModel.togglePerformanceTimer(cat)
            ChronosWidgetProvider.requestRefresh(this)
        }
        card.btnPerfReset.setOnClickListener {
            feedback.playTerminalClick()
            viewModel.resetPerformanceTimer(cat)
            ChronosWidgetProvider.requestRefresh(this)
        }
        card.btnPerfConfig.setOnClickListener {
            feedback.playTerminalClick()
            showPerformanceDurationDialog(cat, (model.entity.totalSeconds / 60L).toInt())
        }
    }

    private fun showPerformanceDurationDialog(category: PerformanceCategory, currentMinutes: Int) {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(currentMinutes.toString())
            setSelection(text.length)
            setPadding(48, 32, 48, 24)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Duración: ${category.title}")
            .setMessage("Define los minutos para ${category.subtitle}:")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Aplicar") { _, _ ->
                val minutes = input.text.toString().toIntOrNull()?.coerceIn(1, 360)
                if (minutes != null) {
                    feedback.playBeepProgress(withHaptic = false)
                    feedback.hapticDoubleConfirm()
                    viewModel.updatePerformanceDuration(category, minutes)
                } else {
                    feedback.playErrorGlitch()
                }
            }
            .show()
    }

    private fun openCreateOrEditCustomTimer(existing: CustomTimerEntity?) {
        val dialog = CustomTimerDialogFragment.newInstance(existing).apply {
            onSaveTimer = { id, name, startMin, duration, daysMask, resetPeriod, notes ->
                feedback.playSuccessChime(withHaptic = true)
                viewModel.saveCustomTimer(
                    id = id,
                    name = name,
                    startMinuteOfDay = startMin,
                    durationMinutes = duration,
                    activeDaysMask = daysMask,
                    resetPeriod = resetPeriod,
                    privateNotes = notes
                )
                ChronosWidgetProvider.requestRefresh(this@MainActivity)
            }
        }
        dialog.show(supportFragmentManager, "custom_timer_dialog")
    }

    private fun openNodePopup(timerId: Long) {
        val popup = NodePopupDialogFragment.newInstance(timerId).apply {
            onEditRequested = { entity ->
                openCreateOrEditCustomTimer(entity)
            }
        }
        popup.show(supportFragmentManager, "node_popup_dialog")
    }

    private fun openSettingsDialog() {
        val dialog = SettingsDialogFragment().apply {
            onRequestOverlayPermission = {
                requestOverlayPermission()
            }
            onOverlayToggleChanged = { enabled ->
                if (enabled) {
                    startOverlayWithNotificationCheck()
                } else {
                    FloatingOverlayService.stop(this@MainActivity)
                }
            }
        }
        dialog.show(supportFragmentManager, "settings_dialog")
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun launchOverclockMode() {
        val options = ActivityOptionsCompat.makeCustomAnimation(
            this,
            R.anim.cyber_hack_enter,
            R.anim.cyber_hack_exit
        )
        startActivity(OverclockActivity.createIntent(this), options.toBundle())
    }

    private fun startOverlayWithNotificationCheck() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            FloatingOverlayService.start(this)
        }
    }

    private fun applyThemePalette(palette: ResolvedCyberPalette) {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !palette.isDark
            isAppearanceLightNavigationBars = !palette.isDark
        }

        binding.rootContainer.background = createHarmonyAmbientBackground(palette)
        binding.hackerSplashView.applyTheme(palette)

        binding.tvSystemTag.setTextColor(palette.primaryNeon)
        binding.tvSystemTag.backgroundTintList = ColorStateList.valueOf(palette.capsuleFillColor)
        binding.tvAppTitle.setTextColor(palette.textPrimary)
        binding.tvDayResetTitle.setTextColor(palette.textSecondary)
        binding.tvDayResetCountdown.setTextColor(palette.primaryNeon)
        binding.tvTimelineHint.setTextColor(palette.textMuted)
        binding.tvQuoteBadge.setTextColor(palette.secondaryNeon)
        binding.tvDayPhaseBadge.setTextColor(palette.primaryNeon)
        binding.tvDayPhaseBadge.backgroundTintList = ColorStateList.valueOf(palette.capsuleFillColor)
        binding.tvMotivationalQuote.setTextColor(palette.textPrimary)
        binding.tvPerformanceSectionTitle.setTextColor(palette.textPrimary)
        binding.tvCustomSectionTitle.setTextColor(palette.textPrimary)
        binding.tvCustomNodeCount.setTextColor(palette.primaryNeon)
        binding.tvCustomNodeCount.backgroundTintList = ColorStateList.valueOf(palette.capsuleFillColor)
        binding.tvEmptyCustomTimers.setTextColor(palette.textSecondary)

        binding.cardMotivationalQuote.setCardBackgroundColor(palette.glassSurfaceColor)
        binding.cardMotivationalQuote.strokeColor = palette.rimHighlightColor
        binding.cardDayLaser.setCardBackgroundColor(palette.glassSurfaceColor)
        binding.cardDayLaser.strokeColor = palette.rimHighlightColor

        binding.cardOverclockLauncher.setCardBackgroundColor(palette.glassSurfaceColor)
        binding.cardOverclockLauncher.strokeColor = palette.secondaryNeon
        binding.ivOverclockBolt.imageTintList = ColorStateList.valueOf(palette.secondaryNeon)
        binding.badgeOverclockIcon.backgroundTintList = ColorStateList.valueOf(
            Color.argb(if (palette.isDark) 42 else 28, Color.red(palette.secondaryNeon), Color.green(palette.secondaryNeon), Color.blue(palette.secondaryNeon))
        )
        binding.tvOverclockCardTitle.setTextColor(palette.textPrimary)
        binding.tvOverclockCardSubtitle.setTextColor(palette.textSecondary)
        binding.tvOverclockActionPill.setTextColor(palette.secondaryNeon)
        binding.tvOverclockActionPill.backgroundTintList = ColorStateList.valueOf(palette.capsuleFillColor)
        binding.tvOverclockPointsBadge.setTextColor(palette.primaryNeon)

        binding.btnFloatingToggle.backgroundTintList = ColorStateList.valueOf(palette.capsuleFillColor)
        binding.btnSettings.backgroundTintList = ColorStateList.valueOf(palette.capsuleFillColor)
        binding.btnSettings.imageTintList = ColorStateList.valueOf(palette.secondaryNeon)

        binding.fabAddTimer.backgroundTintList = ColorStateList.valueOf(palette.primaryNeon)
        binding.fabAddTimer.setTextColor(palette.onPrimaryColor)
        binding.fabAddTimer.iconTint = ColorStateList.valueOf(palette.onPrimaryColor)

        binding.bottomNavigation.setBackgroundColor(palette.surfaceColor)
        binding.bottomNavDivider.setBackgroundColor(palette.rimHighlightColor)
        val navStates = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        )
        val navColors = intArrayOf(palette.primaryNeon, palette.textMuted)
        val navColorStateList = ColorStateList(navStates, navColors)
        binding.bottomNavigation.itemIconTintList = navColorStateList
        binding.bottomNavigation.itemTextColor = navColorStateList
        binding.bottomNavigation.itemRippleColor = ColorStateList.valueOf(
            Color.argb(if (palette.isDark) 44 else 28, Color.red(palette.primaryNeon), Color.green(palette.primaryNeon), Color.blue(palette.primaryNeon))
        )

        customTimerAdapter.applyPalette(palette)
    }

    private fun createHarmonyAmbientBackground(palette: ResolvedCyberPalette): LayerDrawable {
        val density = resources.displayMetrics.density
        val base = ColorDrawable(palette.backgroundColor)
        val primaryAlpha = if (palette.isDark) 42 else 30
        val secondaryAlpha = if (palette.isDark) 34 else 24

        val primaryAura = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            setGradientCenter(0.2f, 0.12f)
            gradientRadius = 420f * density
            colors = intArrayOf(
                Color.argb(primaryAlpha, Color.red(palette.primaryNeon), Color.green(palette.primaryNeon), Color.blue(palette.primaryNeon)),
                Color.TRANSPARENT
            )
        }

        val secondaryAura = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            setGradientCenter(0.85f, 0.72f)
            gradientRadius = 380f * density
            colors = intArrayOf(
                Color.argb(secondaryAlpha, Color.red(palette.secondaryNeon), Color.green(palette.secondaryNeon), Color.blue(palette.secondaryNeon)),
                Color.TRANSPARENT
            )
        }

        return LayerDrawable(arrayOf(base, primaryAura, secondaryAura))
    }

    companion object {
        private const val KEY_ACTIVE_NAV_ID = "active_nav_item_id"
        private const val TAG_STATS_FRAGMENT = "chronos_stats_fragment"
    }
}
