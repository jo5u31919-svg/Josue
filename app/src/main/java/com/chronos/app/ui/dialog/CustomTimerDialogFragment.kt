package com.chronos.app.ui.dialog

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.chronos.app.R
import com.chronos.app.data.local.CustomTimerEntity
import com.chronos.app.data.local.ResetPeriod
import com.chronos.app.databinding.DialogCustomTimerBinding
import com.chronos.app.domain.ChronosTimeEngine
import com.chronos.app.feedback.CyberFeedbackManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class CustomTimerDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogCustomTimerBinding? = null
    private val binding get() = _binding!!

    private var editingTimerId: Long = 0L
    private var selectedMinuteOfDay: Int = 9 * 60

    var onSaveTimer: ((
        id: Long,
        name: String,
        startMinuteOfDay: Int,
        durationMinutes: Int,
        activeDaysMask: Int,
        resetPeriod: ResetPeriod,
        privateNotes: String
    ) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogCustomTimerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = arguments
        if (args != null && args.containsKey(ARG_ID)) {
            editingTimerId = args.getLong(ARG_ID, 0L)
            binding.tvDialogTimerTitle.text = "EDITAR NODO TEMPORAL"
            binding.etTimerName.setText(args.getString(ARG_NAME, ""))
            selectedMinuteOfDay = args.getInt(ARG_START_MINUTE, 9 * 60)
            binding.etDurationMinutes.setText(args.getInt(ARG_DURATION, 45).toString())
            applyDaysMask(args.getInt(ARG_DAYS_MASK, 0b0011111))
            applyResetPeriod(args.getString(ARG_RESET_PERIOD, ResetPeriod.DAILY.name))
            binding.etPrivateNotes.setText(args.getString(ARG_NOTES, ""))
            binding.btnSaveTimer.text = "ACTUALIZAR"
        } else {
            val nowMin = ChronosTimeEngine.currentMinuteOfDay()
            selectedMinuteOfDay = nowMin
        }

        updateStartTimeButtonLabel()

        binding.btnPickStartTime.setOnClickListener {
            CyberFeedbackManager.getInstance(requireContext()).playTerminalClick()
            val hour = selectedMinuteOfDay / 60
            val min = selectedMinuteOfDay % 60
            TimePickerDialog(
                requireContext(),
                { _, pickedHour, pickedMinute ->
                    CyberFeedbackManager.getInstance(requireContext()).playTerminalClick()
                    selectedMinuteOfDay = pickedHour * 60 + pickedMinute
                    updateStartTimeButtonLabel()
                },
                hour,
                min,
                true
            ).show()
        }

        binding.btnCancelTimer.setOnClickListener {
            CyberFeedbackManager.getInstance(requireContext()).playTerminalClick()
            dismiss()
        }

        binding.btnSaveTimer.setOnClickListener {
            val name = binding.etTimerName.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                CyberFeedbackManager.getInstance(requireContext()).playErrorGlitch()
                Toast.makeText(requireContext(), "Ingresa un nombre para el nodo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val duration = binding.etDurationMinutes.text?.toString()?.toIntOrNull()?.coerceIn(1, 720)
            if (duration == null) {
                CyberFeedbackManager.getInstance(requireContext()).playErrorGlitch()
                Toast.makeText(requireContext(), "Ingresa una duración válida en minutos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val daysMask = readDaysMask()
            val resetPeriod = readResetPeriod()
            val notes = binding.etPrivateNotes.text?.toString()?.trim().orEmpty()

            onSaveTimer?.invoke(
                editingTimerId,
                name,
                selectedMinuteOfDay,
                duration,
                daysMask,
                resetPeriod,
                notes
            )
            dismiss()
        }

        val viewModel = androidx.lifecycle.ViewModelProvider(requireActivity())[com.chronos.app.ui.viewmodel.ChronosViewModel::class.java]
        val palette = viewModel.uiState.value.palette
        binding.root.setBackgroundColor(palette.surfaceColor)
        binding.tvDialogTimerTitle.setTextColor(palette.primaryNeon)
        binding.btnPickStartTime.setTextColor(palette.primaryNeon)
        binding.btnPickStartTime.strokeColor = android.content.res.ColorStateList.valueOf(palette.primaryNeon)
        binding.btnCancelTimer.setTextColor(palette.textSecondary)
        binding.btnSaveTimer.backgroundTintList = android.content.res.ColorStateList.valueOf(palette.primaryNeon)
        binding.btnSaveTimer.setTextColor(palette.onPrimaryColor)
    }

    private fun updateStartTimeButtonLabel() {
        binding.btnPickStartTime.text = "INICIO: ${ChronosTimeEngine.formatMinuteOfDay(selectedMinuteOfDay)}"
    }

    private fun applyDaysMask(mask: Int) {
        val chips = listOf(
            binding.chipMon,
            binding.chipTue,
            binding.chipWed,
            binding.chipThu,
            binding.chipFri,
            binding.chipSat,
            binding.chipSun
        )
        chips.forEachIndexed { index, chip ->
            chip.isChecked = (mask and (1 shl index)) != 0
        }
    }

    private fun readDaysMask(): Int {
        val chips = listOf(
            binding.chipMon,
            binding.chipTue,
            binding.chipWed,
            binding.chipThu,
            binding.chipFri,
            binding.chipSat,
            binding.chipSun
        )
        var mask = 0
        chips.forEachIndexed { index, chip ->
            if (chip.isChecked) {
                mask = mask or (1 shl index)
            }
        }
        return if (mask == 0) 0b1111111 else mask
    }

    private fun applyResetPeriod(periodName: String?) {
        val chipId = when (periodName) {
            ResetPeriod.EVERY_4H.name -> R.id.chipReset4h
            ResetPeriod.EVERY_8H.name -> R.id.chipReset8h
            ResetPeriod.EVERY_12H.name -> R.id.chipReset12h
            ResetPeriod.WEEKLY.name -> R.id.chipResetWeekly
            ResetPeriod.ONCE.name -> R.id.chipResetOnce
            else -> R.id.chipResetDaily
        }
        binding.chipGroupResetPeriod.check(chipId)
    }

    private fun readResetPeriod(): ResetPeriod {
        return when (binding.chipGroupResetPeriod.checkedChipId) {
            R.id.chipReset4h -> ResetPeriod.EVERY_4H
            R.id.chipReset8h -> ResetPeriod.EVERY_8H
            R.id.chipReset12h -> ResetPeriod.EVERY_12H
            R.id.chipResetWeekly -> ResetPeriod.WEEKLY
            R.id.chipResetOnce -> ResetPeriod.ONCE
            else -> ResetPeriod.DAILY
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_ID = "arg_id"
        private const val ARG_NAME = "arg_name"
        private const val ARG_START_MINUTE = "arg_start_minute"
        private const val ARG_DURATION = "arg_duration"
        private const val ARG_DAYS_MASK = "arg_days_mask"
        private const val ARG_RESET_PERIOD = "arg_reset_period"
        private const val ARG_NOTES = "arg_notes"

        fun newInstance(existing: CustomTimerEntity? = null): CustomTimerDialogFragment {
            return CustomTimerDialogFragment().apply {
                if (existing != null) {
                    arguments = Bundle().apply {
                        putLong(ARG_ID, existing.id)
                        putString(ARG_NAME, existing.name)
                        putInt(ARG_START_MINUTE, existing.startMinuteOfDay)
                        putInt(ARG_DURATION, existing.durationMinutes)
                        putInt(ARG_DAYS_MASK, existing.activeDaysMask)
                        putString(ARG_RESET_PERIOD, existing.resetPeriod)
                        putString(ARG_NOTES, existing.privateNotes)
                    }
                }
            }
        }
    }
}
