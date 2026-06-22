package com.codex.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.codex.app.R
import com.codex.app.databinding.ItemFriendBinding
import com.codex.app.models.Friend

class FriendAdapter(
    private val onFriendClick: (Friend) -> Unit
) : RecyclerView.Adapter<FriendAdapter.VH>() {

    private var items: List<Friend> = emptyList()

    fun submitList(list: List<Friend>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemFriendBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size

    inner class VH(private val b: ItemFriendBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(friend: Friend) {
            b.friendName.text = friend.displayName.ifBlank { "Codex User" }
            Glide.with(b.friendAvatar)
                .load(friend.photoUrl?.takeIf { it.isNotBlank() } ?: R.drawable.default_avatar)
                .placeholder(R.drawable.default_avatar)
                .into(b.friendAvatar)
            b.root.setOnClickListener { onFriendClick(friend) }
        }
    }
}