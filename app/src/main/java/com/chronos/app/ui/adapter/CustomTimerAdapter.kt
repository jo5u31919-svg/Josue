package com.chronos.app.ui.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chronos.app.databinding.ItemCustomTimerSummaryBinding
import com.chronos.app.domain.ChronosTimeEngine
import com.chronos.app.domain.ProjectedTimelineNode
import com.chronos.app.feedback.CyberFeedbackManager
import com.chronos.app.theme.CyberThemeId
import com.chronos.app.theme.ResolvedCyberPalette

class CustomTimerAdapter(
    private val onNodeClick: (ProjectedTimelineNode) -> Unit
) : ListAdapter<ProjectedTimelineNode, CustomTimerAdapter.NodeViewHolder>(DiffCallback) {

    private var currentPalette: ResolvedCyberPalette = CyberThemeId.CYBERPUNK_CLASSIC.resolve(true)

    fun applyPalette(palette: ResolvedCyberPalette) {
        currentPalette = palette
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NodeViewHolder {
        val binding = ItemCustomTimerSummaryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NodeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NodeViewHolder, position: Int) {
        holder.bind(getItem(position), currentPalette, onNodeClick)
    }

    class NodeViewHolder(
        private val binding: ItemCustomTimerSummaryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            node: ProjectedTimelineNode,
            palette: ResolvedCyberPalette,
            onNodeClick: (ProjectedTimelineNode) -> Unit
        ) {
            val timer = node.timer
            binding.tvCustomName.text = timer.name
            binding.tvCustomName.setTextColor(palette.textPrimary)

            val startStr = ChronosTimeEngine.formatMinuteOfDay(timer.startMinuteOfDay)
            val periodLabel = timer.parsedResetPeriod().label
            val daysSummary = timer.formatDaysSummary()
            binding.tvCustomSchedule.text = "$startStr · ${timer.durationMinutes} min · $periodLabel · $daysSummary"
            binding.tvCustomSchedule.setTextColor(palette.textSecondary)

            binding.tvCustomCountdown.text = ChronosTimeEngine.formatDurationHms(node.liveRemainingSeconds)
            val accent = if (node.isCurrentlyActiveWindow) palette.secondaryNeon else palette.primaryNeon
            binding.tvCustomCountdown.setTextColor(accent)
            binding.tvCustomCountdown.backgroundTintList = ColorStateList.valueOf(palette.capsuleFillColor)

            binding.cardCustomItem.setCardBackgroundColor(palette.glassSurfaceColor)
            binding.cardCustomItem.strokeColor = if (node.isCurrentlyActiveWindow) accent else palette.rimHighlightColor
            binding.viewStatusDot.backgroundTintList = ColorStateList.valueOf(accent)

            binding.root.setOnClickListener {
                CyberFeedbackManager.getInstance(binding.root.context).playTerminalClick()
                onNodeClick(node)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ProjectedTimelineNode>() {
        override fun areItemsTheSame(
            oldItem: ProjectedTimelineNode,
            newItem: ProjectedTimelineNode
        ): Boolean = oldItem.timer.id == newItem.timer.id

        override fun areContentsTheSame(
            oldItem: ProjectedTimelineNode,
            newItem: ProjectedTimelineNode
        ): Boolean = oldItem == newItem
    }
}
