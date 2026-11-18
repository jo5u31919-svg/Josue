package com.chronos.app.ui.dialog

import android.content.res.ColorStateList
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chronos.app.R
import com.chronos.app.databinding.DialogSettingsBinding
import com.chronos.app.feedback.CyberFeedbackManager
import com.chronos.app.theme.CyberThemeId
import com.chronos.app.theme.ResolvedCyberPalette
import com.chronos.app.theme.ThemeAppearanceMode
import com.chronos.app.ui.viewmodel.ChronosViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class SettingsDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ChronosViewModel

    var onRequestOverlayPermission: (() -> Unit)? = null
    var onOverlayToggleChanged: ((Boolean) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[ChronosViewModel::class.java]

        val state = viewModel.uiState.value

        val modeChipId = when (state.appearanceMode) {
            ThemeAppearanceMode.NIGHT -> R.id.chipModeNight
            ThemeAppearanceMode.DAY -> R.id.chipModeDay
            ThemeAppearanceMode.SYSTEM -> R.id.chipModeSystem
        }
        binding.chipGroupAppearanceMode.check(modeChipId)

        binding.chipGroupAppearanceMode.setOnCheckedStateChangeListener { _, checkedIds ->
            val selectedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val mode = when (selectedId) {
                R.id.chipModeDay -> ThemeAppearanceMode.DAY
                R.id.chipModeSystem -> ThemeAppearanceMode.SYSTEM
                else -> ThemeAppearanceMode.NIGHT
            }
            if (mode != viewModel.uiState.value.appearanceMode) {
                CyberFeedbackManager.getInstance(requireContext()).playTerminalClick()
                viewModel.selectAppearanceMode(mode)
            }
        }

        val themeChipId = when (state.themeId) {
            CyberThemeId.CYBERPUNK_CLASSIC -> R.id.chipThemeCyberpunk
            CyberThemeId.HARMONY_AURORA -> R.id.chipThemeHarmony
            CyberThemeId.MATRIX_PROTOCOL -> R.id.chipThemeMatrix
            CyberThemeId.SYNTHWAVE_OUTRUN -> R.id.chipThemeSynthwave
            CyberThemeId.GHOST_CRIMSON -> R.id.chipThemeCrimson
            CyberThemeId.ARCTIC_NEURAL -> R.id.chipThemeArctic
        }
        binding.chipGroupThemes.check(themeChipId)

        binding.chipGroupThemes.setOnCheckedStateChangeListener { _, checkedIds ->
            val selectedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val theme = when (selectedId) {
                R.id.chipThemeHarmony -> CyberThemeId.HARMONY_AURORA
                R.id.chipThemeMatrix -> CyberThemeId.MATRIX_PROTOCOL
                R.id.chipThemeSynthwave -> CyberThemeId.SYNTHWAVE_OUTRUN
                R.id.chipThemeCrimson -> CyberThemeId.GHOST_CRIMSON
                R.id.chipThemeArctic -> CyberThemeId.ARCTIC_NEURAL
                else -> CyberThemeId.CYBERPUNK_CLASSIC
            }
            CyberFeedbackManager.getInstance(requireContext()).playTerminalClick()
            viewModel.selectTheme(theme)
        }

        val overlay = state.overlaySettings
        binding.switchOverlayEnabled.isChecked = overlay.overlayEnabled
        binding.cbShowDayReset.isChecked = overlay.showDayReset
        binding.cbShowPerformance.isChecked = overlay.showPerformanceTimers
        binding.cbShowCustom.isChecked = overlay.showCustomTimers
        binding.sliderMaxTimers.value = overlay.maxSimultaneousTimers.toFloat()
        binding.tvMaxTimersValue.text = overlay.maxSimultaneousTimers.toString()

        val feedback = state.feedbackSettings
        binding.switchSoundEnabled.isChecked = feedback.soundEffectsEnabled
        binding.switchHapticsEnabled.isChecked = feedback.hapticsEnabled

        refreshPermissionButton()

        binding.btnGrantOverlayPermission.setOnClickListener {
            onRequestOverlayPermission?.invoke()
        }

        binding.switchOverlayEnabled.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !Settings.canDrawOverlays(requireContext())) {
                binding.switchOverlayEnabled.isChecked = false
                onRequestOverlayPermission?.invoke()
            } else {
                viewModel.updateOverlaySettings { it.copy(overlayEnabled = isChecked) }
                onOverlayToggleChanged?.invoke(isChecked)
            }
        }

        binding.cbShowDayReset.setOnCheckedChangeListener { _, checked ->
            viewModel.updateOverlaySettings { it.copy(showDayReset = checked) }
        }
        binding.cbShowPerformance.setOnCheckedChangeListener { _, checked ->
            viewModel.updateOverlaySettings { it.copy(showPerformanceTimers = checked) }
        }
        binding.cbShowCustom.setOnCheckedChangeListener { _, checked ->
            viewModel.updateOverlaySettings { it.copy(showCustomTimers = checked) }
        }
        binding.sliderMaxTimers.addOnChangeListener { _, value, _ ->
            val maxCount = value.toInt().coerceIn(1, 6)
            binding.tvMaxTimersValue.text = maxCount.toString()
            viewModel.updateOverlaySettings { it.copy(maxSimultaneousTimers = maxCount) }
        }

        binding.switchSoundEnabled.setOnCheckedChangeListener { _, checked ->
            viewModel.updateFeedbackSettings { it.copy(soundEffectsEnabled = checked) }
            if (checked) {
                CyberFeedbackManager.getInstance(requireContext()).playBeepProgress()
            }
        }

        binding.switchHapticsEnabled.setOnCheckedChangeListener { _, checked ->
            viewModel.updateFeedbackSettings { it.copy(hapticsEnabled = checked) }
            if (checked) {
                CyberFeedbackManager.getInstance(requireContext()).hapticDoubleConfirm()
            }
        }

        binding.btnCloseSettings.setOnClickListener {
            CyberFeedbackManager.getInstance(requireContext()).playTerminalClick()
            dismiss()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { ui ->
                    applyPalette(ui.palette)
                }
            }
        }
    }

    private fun applyPalette(palette: ResolvedCyberPalette) {
        binding.settingsRootContainer.setBackgroundColor(palette.surfaceColor)
        binding.tvSettingsHeader.setTextColor(palette.primaryNeon)
        binding.tvMaxTimersValue.setTextColor(palette.primaryNeon)
        binding.tvMaxTimersValue.backgroundTintList = ColorStateList.valueOf(palette.capsuleFillColor)
        binding.btnCloseSettings.backgroundTintList = ColorStateList.valueOf(palette.primaryNeon)
        binding.btnCloseSettings.setTextColor(palette.onPrimaryColor)
        binding.btnGrantOverlayPermission.setTextColor(palette.secondaryNeon)
        binding.btnGrantOverlayPermission.strokeColor = ColorStateList.valueOf(palette.secondaryNeon)
        binding.sliderMaxTimers.thumbTintList = ColorStateList.valueOf(palette.primaryNeon)
        binding.sliderMaxTimers.trackActiveTintList = ColorStateList.valueOf(palette.primaryNeon)
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionButton()
    }

    private fun refreshPermissionButton() {
        val granted = Settings.canDrawOverlays(requireContext())
        binding.btnGrantOverlayPermission.visibility = if (granted) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
