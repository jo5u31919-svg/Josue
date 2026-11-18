package com.chronos.app.ui

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.chronos.app.ChronosApplication
import com.chronos.app.R
import com.chronos.app.data.local.PerformanceCategory
import com.chronos.app.data.repository.OverclockReward
import com.chronos.app.databinding.ActivityOverclockBinding
import com.chronos.app.domain.ChronosTimeEngine
import com.chronos.app.feedback.CyberFeedbackManager
import com.chronos.app.ui.custom.OverclockVisualPhase
import com.chronos.app.widget.ChronosWidgetProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class OverclockActivity : AppCompatActivity() {

    private enum class OverclockState {
        IDLE,
        RUNNING,
        PAUSED,
        COMPLETED
    }

    private lateinit var binding: ActivityOverclockBinding
    private val app by lazy { application as ChronosApplication }
    private val feedback by lazy { CyberFeedbackManager.getInstance(this) }

    private var state: OverclockState = OverclockState.IDLE
    private var selectedMinutes: Int = 25
    private var selectedModeName: String = "DEEP HACK"
    private var totalMillis: Long = 25 * 60_000L
    private var remainingMillis: Long = 25 * 60_000L
    private var lastTickElapsedMs: Long = 0L
    private var pauseCount: Int = 0

    private var tickerJob: Job? = null
    private var countdownPulseAnimator: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityOverclockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        applyEdgeToEdgeInsets()
        restoreState(savedInstanceState)
        setupBlockSelector()
        setupControlListeners()
        setupBackLocking()
        renderCurrentState()

        if (state == OverclockState.RUNNING) {
            startTickerLoop()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (state == OverclockState.RUNNING) {
            syncElapsedWhileRunning()
        }
        outState.putString(KEY_STATE, state.name)
        outState.putInt(KEY_MINUTES, selectedMinutes)
        outState.putString(KEY_MODE_NAME, selectedModeName)
        outState.putLong(KEY_TOTAL_MS, totalMillis)
        outState.putLong(KEY_REMAINING_MS, remainingMillis)
        outState.putLong(KEY_LAST_ELAPSED, lastTickElapsedMs)
        outState.putInt(KEY_PAUSE_COUNT, pauseCount)
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) return
        state = savedInstanceState.getString(KEY_STATE)
            ?.let { name -> OverclockState.entries.firstOrNull { it.name == name } }
            ?: OverclockState.IDLE
        selectedMinutes = savedInstanceState.getInt(KEY_MINUTES, 25).coerceIn(1, 180)
        selectedModeName = savedInstanceState.getString(KEY_MODE_NAME, "DEEP HACK")
        totalMillis = savedInstanceState.getLong(KEY_TOTAL_MS, selectedMinutes * 60_000L)
        remainingMillis = savedInstanceState.getLong(KEY_REMAINING_MS, totalMillis)
        lastTickElapsedMs = savedInstanceState.getLong(KEY_LAST_ELAPSED, SystemClock.elapsedRealtime())
        pauseCount = savedInstanceState.getInt(KEY_PAUSE_COUNT, 0)
    }

    private fun applyEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.overclockScrollView) { view, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(sys.left, sys.top, sys.right, sys.bottom)
            insets
        }
    }

    private fun setupBlockSelector() {
        when (selectedMinutes) {
            25 -> binding.chipGroupOverclockBlocks.check(R.id.chipBlock25)
            50 -> binding.chipGroupOverclockBlocks.check(R.id.chipBlock50)
            else -> {
                binding.chipBlockCustom.text = "${selectedMinutes}m · Custom"
                binding.chipGroupOverclockBlocks.check(R.id.chipBlockCustom)
            }
        }

        binding.chipBlock25.setOnClickListener {
            if (state != OverclockState.IDLE) return@setOnClickListener
            feedback.playTerminalClick()
            applySelectedDuration(25, "DEEP HACK")
        }

        binding.chipBlock50.setOnClickListener {
            if (state != OverclockState.IDLE) return@setOnClickListener
            feedback.playTerminalClick()
            applySelectedDuration(50, "OVERCLOCK FULL")
        }

        binding.chipBlockCustom.setOnClickListener {
            if (state != OverclockState.IDLE) return@setOnClickListener
            feedback.playTerminalClick()
            showCustomMinutesDialog()
        }
    }

    private fun applySelectedDuration(minutes: Int, modeName: String) {
        selectedMinutes = minutes.coerceIn(1, 180)
        selectedModeName = modeName
        totalMillis = selectedMinutes * 60_000L
        remainingMillis = totalMillis
        pauseCount = 0
        renderCurrentState()
    }

    private fun showCustomMinutesDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(selectedMinutes.toString())
            setSelection(text.length)
            setPadding(48, 32, 48, 24)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Bloque Overclock Personalizado")
            .setMessage("Ingresa la duración extrema en minutos (1 - 180):")
            .setView(input)
            .setNegativeButton("Cancelar") { _, _ ->
                when (selectedMinutes) {
                    25 -> binding.chipGroupOverclockBlocks.check(R.id.chipBlock25)
                    50 -> binding.chipGroupOverclockBlocks.check(R.id.chipBlock50)
                }
            }
            .setPositiveButton("Configurar") { _, _ ->
                val mins = input.text.toString().toIntOrNull()?.coerceIn(1, 180) ?: selectedMinutes
                binding.chipBlockCustom.text = "${mins}m · Custom"
                binding.chipGroupOverclockBlocks.check(R.id.chipBlockCustom)
                applySelectedDuration(mins, "PROTOCOLO CUSTOM")
            }
            .show()
    }

    private fun setupControlListeners() {
        binding.btnStartPauseOverclock.setOnClickListener {
            when (state) {
                OverclockState.IDLE -> startOverclock()
                OverclockState.RUNNING -> pauseWithInstabilityWarning()
                OverclockState.PAUSED -> resumeOverclock()
                OverclockState.COMPLETED -> resetToIdle()
            }
        }

        binding.btnAbortOverclock.setOnClickListener {
            handleAbortRequest()
        }

        binding.btnCloseOverclock.setOnClickListener {
            if (state == OverclockState.RUNNING || state == OverclockState.PAUSED) {
                confirmForceAbortAndClose()
            } else {
                finishWithCyberTransition()
            }
        }

        binding.btnVictoryReturn.setOnClickListener {
            finishWithCyberTransition()
        }

        binding.btnVictoryNewBlock.setOnClickListener {
            resetToIdle()
        }
    }

    private fun setupBackLocking() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (state == OverclockState.RUNNING || state == OverclockState.PAUSED) {
                    confirmForceAbortAndClose()
                } else {
                    finishWithCyberTransition()
                }
            }
        })
    }

    private fun startOverclock() {
        feedback.playBeepProgress(withHaptic = false)
        feedback.hapticOverclockAlert()
        state = OverclockState.RUNNING
        totalMillis = selectedMinutes * 60_000L
        remainingMillis = totalMillis
        pauseCount = 0
        lastTickElapsedMs = SystemClock.elapsedRealtime()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        startCountdownPulseAnimation()
        startTickerLoop()
        renderCurrentState()
    }

    private fun pauseWithInstabilityWarning() {
        feedback.playErrorGlitch()
        syncElapsedWhileRunning()
        tickerJob?.cancel()
        pauseCount++
        state = OverclockState.PAUSED
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        stopCountdownPulseAnimation()
        renderCurrentState()
    }

    private fun resumeOverclock() {
        feedback.playBeepProgress()
        state = OverclockState.RUNNING
        lastTickElapsedMs = SystemClock.elapsedRealtime()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        startCountdownPulseAnimation()
        startTickerLoop()
        renderCurrentState()
    }

    private fun handleAbortRequest() {
        if (state == OverclockState.IDLE || state == OverclockState.COMPLETED) {
            finishWithCyberTransition()
        } else {
            confirmForceAbortAndClose()
        }
    }

    private fun confirmForceAbortAndClose() {
        MaterialAlertDialogBuilder(this)
            .setTitle("⚠ FORZAR CIERRE DE NÚCLEO")
            .setMessage("¿Abortar la sobrecarga actual? Se detendrá el reactor y no se otorgarán puntos de rendimiento.")
            .setNegativeButton("Mantener Enfoque", null)
            .setPositiveButton("Forzar Cierre") { _, _ ->
                tickerJob?.cancel()
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                stopCountdownPulseAnimation()
                finishWithCyberTransition()
            }
            .show()
    }

    private fun resetToIdle() {
        tickerJob?.cancel()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        stopCountdownPulseAnimation()
        binding.layoutVictoryOverlay.visibility = View.GONE
        state = OverclockState.IDLE
        remainingMillis = totalMillis
        pauseCount = 0
        renderCurrentState()
    }

    private fun startTickerLoop() {
        tickerJob?.cancel()
        tickerJob = lifecycleScope.launch {
            while (isActive && state == OverclockState.RUNNING) {
                syncElapsedWhileRunning()
                if (remainingMillis <= 0L) {
                    onOverclockCompleted()
                    break
                }
                renderCurrentState()
                delay(200L)
            }
        }
    }

    private fun syncElapsedWhileRunning() {
        val now = SystemClock.elapsedRealtime()
        val delta = (now - lastTickElapsedMs).coerceAtLeast(0L)
        lastTickElapsedMs = now
        remainingMillis = (remainingMillis - delta).coerceAtLeast(0L)
    }

    private fun onOverclockCompleted() {
        tickerJob?.cancel()
        state = OverclockState.COMPLETED
        remainingMillis = 0L
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        stopCountdownPulseAnimation()
        feedback.playSuccessChime(withHaptic = false)
        feedback.hapticOverclockAlert()
        renderCurrentState()

        lifecycleScope.launch {
            val reward = app.repository.completeOverclockSession(
                durationMinutes = selectedMinutes,
                pauseCount = pauseCount
            )
            app.settingsPrefs.recordOverclockPoints(reward.totalPoints)
            ChronosWidgetProvider.requestRefresh(this@OverclockActivity)
            showVictoryOverlay(reward)
        }
    }

    private fun showVictoryOverlay(reward: OverclockReward) {
        binding.tvVictoryTotalPoints.text = "+${reward.totalPoints} PTS DE RENDIMIENTO"
        binding.tvVictoryStability.text = "ESTABILIDAD DE NÚCLEO: ${reward.stabilityPercent}%"

        binding.tvRewardMental.text = "+${reward.categoryPoints[PerformanceCategory.MENTAL] ?: 0} PTS"
        binding.tvRewardPhysical.text = "+${reward.categoryPoints[PerformanceCategory.PHYSICAL] ?: 0} PTS"
        binding.tvRewardExpressive.text = "+${reward.categoryPoints[PerformanceCategory.EXPRESSIVE] ?: 0} PTS"
        binding.tvRewardOccupational.text = "+${reward.categoryPoints[PerformanceCategory.OCCUPATIONAL] ?: 0} PTS"

        binding.layoutVictoryOverlay.alpha = 0f
        binding.layoutVictoryOverlay.visibility = View.VISIBLE
        binding.cardVictoryPanel.scaleX = 0.88f
        binding.cardVictoryPanel.scaleY = 0.88f

        binding.layoutVictoryOverlay.animate()
            .alpha(1f)
            .setDuration(320L)
            .start()
        binding.cardVictoryPanel.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(360L)
            .start()
    }

    private fun renderCurrentState() {
        val elapsedFraction = if (totalMillis > 0L) {
            ((totalMillis - remainingMillis).toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

        val stabilityPct = ((1f - pauseCount * 0.10f).coerceAtLeast(0.50f) * 100f).toInt()
        binding.tvStabilityBadge.text = "ESTAB. $stabilityPct%"
        binding.tvStabilityBadge.setTextColor(
            if (pauseCount > 0) 0xFFFFB800.toInt() else 0xFF00F0FF.toInt()
        )

        val displaySeconds = ((remainingMillis + 999L) / 1000L).coerceAtLeast(0L)
        binding.tvOverclockCountdown.text = ChronosTimeEngine.formatDurationMs(displaySeconds)

        val potentialPts = ((selectedMinutes * 4) * (stabilityPct / 100f)).toInt().coerceAtLeast(4)
        binding.tvBlockModeLabel.text = "$selectedModeName // +$potentialPts PTS POTENCIALES"

        val chipsEnabled = state == OverclockState.IDLE
        binding.chipBlock25.isEnabled = chipsEnabled
        binding.chipBlock50.isEnabled = chipsEnabled
        binding.chipBlockCustom.isEnabled = chipsEnabled

        when (state) {
            OverclockState.IDLE -> {
                binding.tvCoreStateBadge.text = "NÚCLEO EN ESPERA"
                binding.tvCoreStateBadge.setTextColor(0xFF00F0FF.toInt())
                binding.cardCountdownCore.strokeColor = 0xFF00F0FF.toInt()
                binding.tvOverclockCountdown.setShadowLayer(18f, 0f, 0f, 0xFF00F0FF.toInt())
                binding.cardInstabilityWarning.visibility = View.GONE

                binding.btnStartPauseOverclock.text = getString(R.string.overclock_start)
                binding.btnStartPauseOverclock.backgroundTintList = ColorStateList.valueOf(0xFF00F0FF.toInt())
                binding.btnStartPauseOverclock.setTextColor(0xFF05050B.toInt())
                binding.btnStartPauseOverclock.setIconResource(R.drawable.ic_bolt)
                binding.btnStartPauseOverclock.iconTint = ColorStateList.valueOf(0xFF05050B.toInt())

                binding.overloadFrameView.updateState(OverclockVisualPhase.IDLE, 0f)
                binding.coreTemperatureView.updateTemperature(0f, active = false, unstable = false)
            }
            OverclockState.RUNNING -> {
                binding.tvCoreStateBadge.text = "SOBRECARGA ACTIVA"
                binding.tvCoreStateBadge.setTextColor(0xFFFF1E42.toInt())
                binding.cardCountdownCore.strokeColor = 0xFFFF1E42.toInt()
                binding.tvOverclockCountdown.setShadowLayer(24f, 0f, 0f, 0xFFFF1E42.toInt())
                binding.cardInstabilityWarning.visibility = if (pauseCount > 0) View.VISIBLE else View.GONE
                if (pauseCount > 0) {
                    binding.tvInstabilityWarning.text =
                        "⚠ NÚCLEO CON $pauseCount INTERRUPCIÓN(ES) · ESTABILIDAD $stabilityPct%"
                }

                binding.btnStartPauseOverclock.text = getString(R.string.overclock_pause)
                binding.btnStartPauseOverclock.backgroundTintList = ColorStateList.valueOf(0xFFFFB800.toInt())
                binding.btnStartPauseOverclock.setTextColor(0xFF05050B.toInt())
                binding.btnStartPauseOverclock.setIconResource(R.drawable.ic_pause)
                binding.btnStartPauseOverclock.iconTint = ColorStateList.valueOf(0xFF05050B.toInt())

                binding.overloadFrameView.updateState(OverclockVisualPhase.RUNNING, elapsedFraction)
                binding.coreTemperatureView.updateTemperature(elapsedFraction, active = true, unstable = false)
            }
            OverclockState.PAUSED -> {
                binding.tvCoreStateBadge.text = "⚠ INESTABILIDAD DETECTADA"
                binding.tvCoreStateBadge.setTextColor(0xFFFFB800.toInt())
                binding.cardCountdownCore.strokeColor = 0xFFFFB800.toInt()
                binding.tvOverclockCountdown.setShadowLayer(20f, 0f, 0f, 0xFFFFB800.toInt())
                binding.cardInstabilityWarning.visibility = View.VISIBLE
                binding.tvInstabilityWarning.text =
                    "⚠ ADVERTENCIA: PAUSAR GENERA INESTABILIDAD DEL SISTEMA (ESTABILIDAD $stabilityPct%)"

                binding.btnStartPauseOverclock.text = getString(R.string.overclock_resume)
                binding.btnStartPauseOverclock.backgroundTintList = ColorStateList.valueOf(0xFF00F0FF.toInt())
                binding.btnStartPauseOverclock.setTextColor(0xFF05050B.toInt())
                binding.btnStartPauseOverclock.setIconResource(R.drawable.ic_play)
                binding.btnStartPauseOverclock.iconTint = ColorStateList.valueOf(0xFF05050B.toInt())

                binding.overloadFrameView.updateState(OverclockVisualPhase.PAUSED_UNSTABLE, elapsedFraction)
                binding.coreTemperatureView.updateTemperature(elapsedFraction, active = false, unstable = true)
            }
            OverclockState.COMPLETED -> {
                binding.tvCoreStateBadge.text = "SINCRONIZACIÓN COMPLETA"
                binding.tvCoreStateBadge.setTextColor(0xFF00FF9F.toInt())
                binding.cardCountdownCore.strokeColor = 0xFF00FF9F.toInt()
                binding.tvOverclockCountdown.setShadowLayer(24f, 0f, 0f, 0xFF00FF9F.toInt())
                binding.cardInstabilityWarning.visibility = View.GONE

                binding.overloadFrameView.updateState(OverclockVisualPhase.VICTORY, 1f)
                binding.coreTemperatureView.updateTemperature(1f, active = false, unstable = false)
            }
        }
    }

    private fun startCountdownPulseAnimation() {
        countdownPulseAnimator?.cancel()
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.035f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.035f)
        val alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.86f)
        countdownPulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            binding.tvOverclockCountdown, scaleX, scaleY, alpha
        ).apply {
            duration = 900L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
    }

    private fun stopCountdownPulseAnimation() {
        countdownPulseAnimator?.cancel()
        binding.tvOverclockCountdown.scaleX = 1f
        binding.tvOverclockCountdown.scaleY = 1f
        binding.tvOverclockCountdown.alpha = 1f
    }

    @Suppress("DEPRECATION")
    private fun finishWithCyberTransition() {
        finish()
        overridePendingTransition(R.anim.cyber_hack_pop_enter, R.anim.cyber_hack_pop_exit)
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        countdownPulseAnimator?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val KEY_STATE = "overclock_state"
        private const val KEY_MINUTES = "overclock_minutes"
        private const val KEY_MODE_NAME = "overclock_mode_name"
        private const val KEY_TOTAL_MS = "overclock_total_ms"
        private const val KEY_REMAINING_MS = "overclock_remaining_ms"
        private const val KEY_LAST_ELAPSED = "overclock_last_elapsed"
        private const val KEY_PAUSE_COUNT = "overclock_pause_count"

        fun createIntent(context: Context): Intent {
            return Intent(context, OverclockActivity::class.java)
        }
    }
}
