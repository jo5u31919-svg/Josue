package com.chronos.app.ui.dialog

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chronos.app.data.local.CustomTimerEntity
import com.chronos.app.databinding.DialogNodePopupBinding
import com.chronos.app.domain.ChronosTimeEngine
import com.chronos.app.feedback.CyberFeedbackManager
import com.chronos.app.theme.ResolvedCyberPalette
import com.chronos.app.ui.viewmodel.ChronosViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class NodePopupDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogNodePopupBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ChronosViewModel
    private var timerId: Long = 0L

    var onEditRequested: ((CustomTimerEntity) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogNodePopupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        timerId = arguments?.getLong(ARG_TIMER_ID) ?: 0L
        viewModel = ViewModelProvider(requireActivity())[ChronosViewModel::class.java]

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val timer = state.allCustomTimers.firstOrNull { it.id == timerId }
                    if (timer == null) {
                        dismissAllowingStateLoss()
                        return@collect
                    }
                    val node = state.projectedNodes.firstOrNull { it.timer.id == timerId }
                    val remaining = node?.liveRemainingSeconds ?: ChronosTimeEngine.computeLiveRemainingSeconds(
                        timer.remainingSeconds,
                        timer.isRunning,
                        timer.lastTickEpochMs,
                        state.nowMillis
                    )
                    bindNode(timer, remaining, state.palette)
                }
            }
        }
    }

    private fun bindNode(
        timer: CustomTimerEntity,
        liveRemainingSeconds: Long,
        palette: ResolvedCyberPalette
    ) {
        binding.root.setBackgroundColor(palette.surfaceColor)
        binding.tvNodePopupTag.setTextColor(palette.secondaryNeon)
        binding.tvNodeTitle.text = timer.name
        binding.tvNodeTitle.setTextColor(palette.textPrimary)

        binding.tvNodeCountdown.text = ChronosTimeEngine.formatDurationHms(liveRemainingSeconds)
        binding.tvNodeCountdown.setTextColor(palette.primaryNeon)
        binding.tvNodeCountdown.backgroundTintList = ColorStateList.valueOf(palette.capsuleFillColor)

        binding.tvNodeMeta.text =
            "Inicio: ${ChronosTimeEngine.formatMinuteOfDay(timer.startMinuteOfDay)} · " +
                "Duración: ${timer.durationMinutes} min · " +
                "Reinicio: ${timer.parsedResetPeriod().label} · " +
                "Días: ${timer.formatDaysSummary()}"
        binding.tvNodeMeta.setTextColor(palette.textSecondary)

        binding.cardPrivateNotesVault.setCardBackgroundColor(palette.backgroundColor)
        binding.cardPrivateNotesVault.strokeColor = palette.secondaryNeon
        binding.tvPrivateNotesContent.setTextColor(palette.textPrimary)
        binding.tvPrivateNotesContent.text = if (timer.privateNotes.isBlank()) {
            "Sin notas privadas guardadas para este nodo."
        } else {
            timer.privateNotes
        }

        binding.btnToggleNodeActive.text = if (timer.isRunning) {
            "PAUSAR TEMPORIZADOR"
        } else {
            "ACTIVAR AHORA"
        }
        binding.btnToggleNodeActive.setTextColor(palette.primaryNeon)
        binding.btnToggleNodeActive.strokeColor = ColorStateList.valueOf(palette.primaryNeon)

        binding.btnNodeEdit.setTextColor(palette.textPrimary)
        binding.btnNodeEdit.iconTint = ColorStateList.valueOf(palette.primaryNeon)
        binding.btnNodeEdit.strokeColor = ColorStateList.valueOf(palette.subtleBorderColor)

        binding.btnNodeRename.setTextColor(palette.textPrimary)
        binding.btnNodeRename.strokeColor = ColorStateList.valueOf(palette.subtleBorderColor)

        binding.btnNodeEditNotes.setTextColor(palette.secondaryNeon)
        binding.btnNodeEditNotes.iconTint = ColorStateList.valueOf(palette.secondaryNeon)
        binding.btnNodeEditNotes.strokeColor = ColorStateList.valueOf(palette.secondaryNeon)

        binding.btnNodeDelete.setTextColor(palette.secondaryNeon)
        binding.btnNodeDelete.iconTint = ColorStateList.valueOf(palette.secondaryNeon)
        binding.btnNodeDelete.strokeColor = ColorStateList.valueOf(palette.secondaryNeon)

        binding.btnToggleNodeActive.setOnClickListener {
            CyberFeedbackManager.getInstance(requireContext()).playBeepProgress()
            viewModel.toggleCustomTimerRunning(timer.id)
        }

        binding.btnNodeEdit.setOnClickListener {
            CyberFeedbackManager.getInstance(requireContext()).playTerminalClick()
            dismiss()
            onEditRequested?.invoke(timer)
        }

        binding.btnNodeRename.setOnClickListener {
            CyberFeedbackManager.getInstance(requireContext()).playTerminalClick()
            showRenameDialog(timer)
        }

        binding.btnNodeEditNotes.setOnClickListener {
            CyberFeedbackManager.getInstance(requireContext()).playTerminalClick()
            showPrivateNotesDialog(timer)
        }

        binding.btnNodeDelete.setOnClickListener {
            CyberFeedbackManager.getInstance(requireContext()).playTerminalClick()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Eliminar nodo")
                .setMessage("¿Seguro que deseas eliminar \"${timer.name}\" de la barra temporal?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar") { _, _ ->
                    CyberFeedbackManager.getInstance(requireContext()).playErrorGlitch()
                    viewModel.deleteCustomTimer(timer.id)
                    dismiss()
                }
                .show()
        }
    }

    private fun showRenameDialog(timer: CustomTimerEntity) {
        val input = EditText(requireContext()).apply {
            setText(timer.name)
            setSelection(text.length)
            setPadding(48, 32, 48, 24)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Configurar nombre")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    CyberFeedbackManager.getInstance(requireContext()).playBeepProgress(withHaptic = false)
                    CyberFeedbackManager.getInstance(requireContext()).hapticDoubleConfirm()
                    viewModel.renameCustomTimer(timer.id, newName)
                } else {
                    CyberFeedbackManager.getInstance(requireContext()).playErrorGlitch()
                }
            }
            .show()
    }

    private fun showPrivateNotesDialog(timer: CustomTimerEntity) {
        val input = EditText(requireContext()).apply {
            setText(timer.privateNotes)
            hint = "Escribe notas privadas visibles solo al pulsar el nodo..."
            minLines = 3
            setPadding(48, 32, 48, 24)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Notas privadas del nodo")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar") { _, _ ->
                CyberFeedbackManager.getInstance(requireContext()).playBeepProgress(withHaptic = false)
                CyberFeedbackManager.getInstance(requireContext()).hapticDoubleConfirm()
                viewModel.updatePrivateNotes(timer.id, input.text.toString())
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TIMER_ID = "arg_timer_id"

        fun newInstance(timerId: Long): NodePopupDialogFragment {
            return NodePopupDialogFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TIMER_ID, timerId)
                }
            }
        }
    }
}
