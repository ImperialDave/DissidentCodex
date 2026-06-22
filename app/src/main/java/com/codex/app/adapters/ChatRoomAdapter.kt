package com.codex.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.codex.app.R
import com.codex.app.databinding.ItemChatRoomBinding
import com.codex.app.models.ChatRoom
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors

class ChatRoomAdapter(
    private val onRoomClick: (ChatRoom) -> Unit,
    private val onFavoriteClick: ((ChatRoom) -> Unit)? = null,
    private val onRoomLongPress: ((ChatRoom) -> Unit)? = null,
    private var favoriteIds: Set<String> = emptySet()
) : RecyclerView.Adapter<ChatRoomAdapter.RoomViewHolder>() {

    private var rooms: List<ChatRoom> = emptyList()

    fun submitList(newRooms: List<ChatRoom>) {
        rooms = newRooms
        notifyDataSetChanged()
    }

    fun setFavoriteIds(ids: Set<String>) {
        favoriteIds = ids
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomViewHolder {
        val binding = ItemChatRoomBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RoomViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RoomViewHolder, position: Int) {
        holder.bind(rooms[position])
    }

    override fun getItemCount() = rooms.size

    inner class RoomViewHolder(private val binding: ItemChatRoomBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(room: ChatRoom) {
            binding.roomTitle.text = room.title
            binding.roomPreview.text = room.lastMessagePreview.ifBlank {
                binding.root.context.getString(R.string.message_hint)
            }
            binding.roomTime.text = PostAdapter.getRelativeTime(room.lastMessageAt)

            val isFavorite = favoriteIds.contains(room.id)
            binding.favoriteBtn.setImageResource(
                if (isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star
            )
            binding.favoriteBtn.setOnClickListener {
                onFavoriteClick?.invoke(room)
            }

            if (room.isTopic()) {
                binding.roomAvatar.visibility = View.GONE
                binding.roomIcon.visibility = View.VISIBLE
                if (room.locked) {
                    binding.roomIcon.setImageResource(R.drawable.ic_lock)
                    binding.roomIcon.imageTintList =
                        ContextCompat.getColorStateList(binding.root.context, R.color.warning)
                } else {
                    binding.roomIcon.setImageResource(R.drawable.ic_topic)
                    val accent = MaterialColors.getColor(binding.root, R.attr.codexAccent)
                    binding.roomIcon.imageTintList = ColorStateList.valueOf(accent)
                }
            } else {
                binding.roomIcon.visibility = View.GONE
                binding.roomAvatar.visibility = View.VISIBLE
                Glide.with(binding.root)
                    .load(R.drawable.default_avatar)
                    .placeholder(R.drawable.default_avatar)
                    .into(binding.roomAvatar)
            }

            binding.root.setOnClickListener { onRoomClick(room) }
            binding.root.setOnLongClickListener {
                onRoomLongPress?.invoke(room)
                true
            }
        }

    }
}