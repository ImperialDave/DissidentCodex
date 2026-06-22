package com.codex.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.codex.app.databinding.ItemModerationChatBinding
import com.codex.app.models.ChatMessage
import com.codex.app.models.ChatRoom
import com.codex.app.models.MediaType

class ModerationChatAdapter(
    private val onDelete: (ChatRoom, ChatMessage) -> Unit
) : RecyclerView.Adapter<ModerationChatAdapter.ChatViewHolder>() {

    private var items: List<Pair<ChatRoom, ChatMessage>> = emptyList()

    fun submitList(newItems: List<Pair<ChatRoom, ChatMessage>>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemModerationChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ChatViewHolder(private val binding: ItemModerationChatBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(pair: Pair<ChatRoom, ChatMessage>) {
            val (room, msg) = pair
            binding.chatRoomLabel.text = room.title
            binding.chatAuthor.text = msg.authorName
            binding.chatText.text = moderationPreview(msg)
            binding.deleteChatBtn.setOnClickListener { onDelete(room, msg) }
        }
    }

    companion object {
        fun moderationPreview(msg: ChatMessage): String {
            val text = msg.text.trim()
            if (text.isNotEmpty()) return text
            return when (msg.mediaType?.lowercase()) {
                MediaType.GIF -> "[GIF]"
                MediaType.VIDEO -> "[Video]"
                MediaType.IMAGE -> "[Photo]"
                else -> if (msg.hasMedia()) "[Media]" else ""
            }
        }
    }
}