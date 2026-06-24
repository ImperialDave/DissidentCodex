package com.codex.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.codex.app.databinding.ItemFeedDmCardBinding
import com.codex.app.models.ChatRoom

class FeedDmStripAdapter(
    private val onRoomClick: (ChatRoom) -> Unit
) : RecyclerView.Adapter<FeedDmStripAdapter.VH>() {

    private var rooms: List<ChatRoom> = emptyList()

    fun submitList(list: List<ChatRoom>) {
        rooms = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemFeedDmCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(rooms[position])

    override fun getItemCount() = rooms.size

    inner class VH(private val b: ItemFeedDmCardBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(room: ChatRoom) {
            b.dmTitle.text = room.title
            b.dmPreview.text = room.lastMessagePreview.ifBlank { "No messages yet" }
            b.dmTime.text = PostAdapter.getRelativeTime(room.lastMessageAt)
            b.root.setOnClickListener { onRoomClick(room) }
        }
    }
}