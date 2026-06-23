package com.codex.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.codex.app.R
import com.codex.app.databinding.ItemModerationRestrictedTopicBinding

data class ModerationHideTopicItem(
    val name: String,
    val hidden: Boolean,
    val hiddenId: String? = null
)

class ModerationHideTopicAdapter(
    private val onToggle: (ModerationHideTopicItem) -> Unit
) : RecyclerView.Adapter<ModerationHideTopicAdapter.ViewHolder>() {

    private var items: List<ModerationHideTopicItem> = emptyList()

    fun submitList(newItems: List<ModerationHideTopicItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemModerationRestrictedTopicBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(private val binding: ItemModerationRestrictedTopicBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ModerationHideTopicItem) {
            val ctx = binding.root.context
            binding.restrictedTitle.text = item.name
            binding.restrictedMeta.text = if (item.hidden) {
                ctx.getString(R.string.topic_status_feed_hidden)
            } else {
                ""
            }
            binding.restrictedActionBtn.text = if (item.hidden) {
                ctx.getString(R.string.unhide_topic)
            } else {
                ctx.getString(R.string.hide_topic)
            }
            binding.restrictedActionBtn.setOnClickListener { onToggle(item) }
        }
    }
}