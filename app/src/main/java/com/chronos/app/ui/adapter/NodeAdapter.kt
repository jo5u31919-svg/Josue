package com.chronos.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chronos.app.R
import com.chronos.app.data.NodeItem

class NodeAdapter(private val nodeList: List<NodeItem>) :
    RecyclerView.Adapter<NodeAdapter.NodeViewHolder>() {

    class NodeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvNodeTitle)
        val tvTime: TextView = itemView.findViewById(R.id.tvNodeTime)
        val tvCountdownLabel: TextView = itemView.findViewById(R.id.tvCountdownLabel)
        val tvStatusLabel: TextView = itemView.findViewById(R.id.tvStatusLabel)
        val checkboxState: ImageView = itemView.findViewById(R.id.checkboxState)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NodeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_custom_node, parent, false)
        return NodeViewHolder(view)
    }

    override fun onBindViewHolder(holder: NodeViewHolder, position: Int) {
        val node = nodeList[position]
        
        holder.tvTitle.text = node.title
        holder.tvTime.text = node.timeInfo

        // Configuración de vista de conteo dinámico estilo HarmonyOS
        if (!node.isActivated) {
            holder.tvCountdownLabel.text = "> SE ACTIVA EN: 08:00"
        } else {
            holder.tvCountdownLabel.text = "> CONTEO ACTIVADO"
        }

        // Sistema de 3 estados cíclicos por toque para el chulito
        holder.checkboxState.setOnClickListener {
            node.statusState = (node.statusState + 1) % 3
            
            when (node.statusState) {
                0 -> {
                    holder.checkboxState.setImageResource(R.drawable.ic_checkbox_null)
                    holder.tvStatusLabel.text = "> NO MARCADO"
                }
                1 -> {
                    holder.checkboxState.setImageResource(R.drawable.ic_checkbox_checked)
                    holder.tvStatusLabel.text = "> OBJETIVO CUMPLIDO"
                }
                2 -> {
                    holder.checkboxState.setImageResource(R.drawable.ic_checkbox_unchecked)
                    holder.tvStatusLabel.text = "> INCOMPLETO"
                }
            }
        }
    }

    override fun getItemCount(): Int = nodeList.size
}
