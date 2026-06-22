package com.codex.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.codex.app.R
import com.codex.app.databinding.ItemModerationRestrictedTopicBinding
import com.codex.app.models.BannedTopic
import com.codex.app.models.ChatRoom

sealed class ModerationRestrictedItem {
    data class Banned(val topic: BannedTopic) : ModerationRestrictedItem()
    data class Locked(val room: ChatRoom) : ModerationRestrictedItem()
}

class ModerationRestrictedTopicAdapter(
    private val onUnban: (BannedTopic) -> Unit,
    private val onUnlock: (ChatRoom) -> Unit
) : RecyclerView.Adapter<ModerationRestrictedTopicAdapter.ViewHolder>() {

    private var items: List<ModerationRestrictedItem> = emptyList()

    fun submitList(newItems: List<ModerationRestrictedItem>) {
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
        fun bind(item: ModerationRestrictedItem) {
            val ctx = binding.root.context
            when (item) {
                is ModerationRestrictedItem.Banned -> {
                    binding.restrictedTitle.text = item.topic.name
                    binding.restrictedMeta.text = ctx.getString(R.string.topic_status_banned)
                    binding.restrictedActionBtn.text = ctx.getString(R.string.unban_topic)
                    binding.restrictedActionBtn.setOnClickListener { onUnban(item.topic) }
                }
                is ModerationRestrictedItem.Locked -> {
                    binding.restrictedTitle.text = item.room.title
                    binding.restrictedMeta.text = ctx.getString(
                        R.string.topic_status_locked,
                        item.room.messageCount
                    )
                    binding.restrictedActionBtn.text = ctx.getString(R.string.unlock_topic)
                    binding.restrictedActionBtn.setOnClickListener { onUnlock(item.room) }
                }
            }
        }
    }
}