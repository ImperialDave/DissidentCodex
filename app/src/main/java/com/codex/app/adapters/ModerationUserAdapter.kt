package com.codex.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.codex.app.R
import com.codex.app.databinding.ItemModerationUserBinding
import com.codex.app.models.Role
import com.codex.app.models.User
import com.codex.app.utils.FirebaseHelper

class ModerationUserAdapter(
    private val onUserClick: (User) -> Unit
) : RecyclerView.Adapter<ModerationUserAdapter.VH>() {

    private var users: List<User> = emptyList()

    fun submitList(newUsers: List<User>) {
        users = newUsers
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemModerationUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(users[position])
    }

    override fun getItemCount() = users.size

    inner class VH(private val binding: ItemModerationUserBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(user: User) {
            binding.userName.text = user.displayName.ifBlank { "Codex User" }
            binding.userEmail.text = user.email
            val role = user.getRoleEnum()
            binding.roleBadge.setImageResource(roleBadgeRes(role))
            val lastActive = user.lastActive
            binding.lastActiveText.text = if (lastActive != null) {
                val rel = PostAdapter.getRelativeTime(lastActive)
                if (FirebaseHelper.isRecentlyActive(user)) "Active · $rel" else "Last seen $rel"
            } else {
                "No activity recorded"
            }
            if (!user.photoUrl.isNullOrEmpty()) {
                Glide.with(binding.userAvatar).load(user.photoUrl)
                    .placeholder(R.drawable.default_avatar).into(binding.userAvatar)
            } else {
                binding.userAvatar.setImageResource(R.drawable.default_avatar)
            }
            binding.root.setOnClickListener { onUserClick(user) }
        }

        private fun roleBadgeRes(role: Role): Int = when (role) {
            Role.FOUNDER -> R.drawable.badge_founder
            Role.ADMIN -> R.drawable.badge_admin
            Role.MOD -> R.drawable.badge_mod
            Role.SUSPENDED -> R.drawable.badge_suspended
            Role.BANNED -> R.drawable.badge_banned
            else -> R.drawable.badge_user
        }
    }
}