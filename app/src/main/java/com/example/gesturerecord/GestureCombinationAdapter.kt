package com.example.gesturerecord

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.gesturerecord.data.GestureCombination

class GestureCombinationAdapter(
    private val onClick: (GestureCombination) -> Unit,
    private val onMoreOptionsClick: (View, GestureCombination) -> Unit
) : ListAdapter<GestureCombination, GestureCombinationAdapter.GestureViewHolder>(GestureDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GestureViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gesture_combination, parent, false)
        return GestureViewHolder(view, onClick, onMoreOptionsClick)
    }

    override fun onBindViewHolder(holder: GestureViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class GestureViewHolder(
        itemView: View,
        val onClick: (GestureCombination) -> Unit,
        val onMoreOptionsClick: (View, GestureCombination) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val ivMore: View = itemView.findViewById(R.id.ivMore)
        private var currentCombo: GestureCombination? = null

        init {
            itemView.setOnClickListener {
                currentCombo?.let { onClick(it) }
            }
            ivMore.setOnClickListener { view ->
                currentCombo?.let { onMoreOptionsClick(view, it) }
            }
        }

        fun bind(combination: GestureCombination) {
            currentCombo = combination
            tvName.text = combination.name
        }
    }
}

class GestureDiffCallback : DiffUtil.ItemCallback<GestureCombination>() {
    override fun areItemsTheSame(oldItem: GestureCombination, newItem: GestureCombination): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: GestureCombination, newItem: GestureCombination): Boolean {
        return oldItem == newItem
    }
}
