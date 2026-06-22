package com.codex.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.codex.app.R
import com.codex.app.databinding.ItemFriendRequestBinding
import com.codex.app.models.FriendRequest
import com.codex.app.models.User

class FriendRequestAdapter(
    private val onAccept: (FriendRequest) -> Unit,
    private val onDecline: (FriendRequest) -> Unit
) : RecyclerView.Adapter<FriendRequestAdapter.VH>() {

    data class RequestRow(val request: FriendRequest, val user: User?)

    private var items: List<RequestRow> = emptyList()

    fun submitList(list: List<RequestRow>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemFriendRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size

    inner class VH(private val b: ItemFriendRequestBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: RequestRow) {
            val name = row.user?.displayName?.ifBlank { "Codex User" } ?: "Codex User"
            b.requestName.text = name
            Glide.with(b.requestAvatar)
                .load(row.user?.photoUrl?.takeIf { it.isNotBlank() } ?: R.drawable.default_avatar)
                .placeholder(R.drawable.default_avatar)
                .into(b.requestAvatar)
            b.acceptBtn.setOnClickListener { onAccept(row.request) }
            b.declineBtn.setOnClickListener { onDecline(row.request) }
        }
    }
}