package com.codex.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.codex.app.R
import com.codex.app.databinding.ItemModTopicRoomBinding
import com.codex.app.models.ChatRoom

class ModerationTopicAdapter(
    private val onLock: (ChatRoom) -> Unit,
    private val onReset: (ChatRoom) -> Unit,
    private val onBan: (ChatRoom) -> Unit,
    private val onDelete: (ChatRoom) -> Unit
) : RecyclerView.Adapter<ModerationTopicAdapter.TopicViewHolder>() {

    private var rooms: List<ChatRoom> = emptyList()

    fun submitList(newRooms: List<ChatRoom>) {
        rooms = newRooms
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TopicViewHolder {
        val binding = ItemModTopicRoomBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TopicViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TopicViewHolder, position: Int) {
        holder.bind(rooms[position])
    }

    override fun getItemCount() = rooms.size

    inner class TopicViewHolder(private val binding: ItemModTopicRoomBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(room: ChatRoom) {
            binding.topicTitle.text = room.title
            val status = buildString {
                append("${room.messageCount} messages")
                if (room.locked) append(" · Locked")
            }
            binding.topicMeta.text = status
            binding.lockBtn.text = if (room.locked) {
                binding.root.context.getString(R.string.unlock_topic)
            } else {
                binding.root.context.getString(R.string.lock_topic)
            }
            binding.lockBtn.setOnClickListener { onLock(room) }
            binding.resetBtn.setOnClickListener { onReset(room) }
            binding.banBtn.setOnClickListener { onBan(room) }
            binding.deleteBtn.setOnClickListener { onDelete(room) }
        }
    }
}