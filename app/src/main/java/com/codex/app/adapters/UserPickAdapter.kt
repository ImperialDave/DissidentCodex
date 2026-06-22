package com.codex.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.codex.app.R
import com.codex.app.databinding.ItemUserPickBinding
import com.codex.app.models.User

class UserPickAdapter(
    private val onUserClick: (User) -> Unit,
    private val onUserLongClick: ((User) -> Unit)? = null
) : RecyclerView.Adapter<UserPickAdapter.UserViewHolder>() {

    private var users: List<User> = emptyList()

    fun submitList(newUsers: List<User>) {
        users = newUsers
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUserPickBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(users[position])
    }

    override fun getItemCount() = users.size

    inner class UserViewHolder(private val binding: ItemUserPickBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(user: User) {
            binding.userName.text = user.displayName
            binding.userEmail.text = user.email
            Glide.with(binding.root)
                .load(user.photoUrl?.takeIf { it.isNotBlank() } ?: R.drawable.default_avatar)
                .placeholder(R.drawable.default_avatar)
                .into(binding.userAvatar)
            binding.root.setOnClickListener { onUserClick(user) }
            binding.root.setOnLongClickListener {
                onUserLongClick?.invoke(user)
                onUserLongClick != null
            }
        }
    }
}