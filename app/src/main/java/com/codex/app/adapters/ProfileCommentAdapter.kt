package com.codex.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.codex.app.databinding.ItemProfileCommentBinding
import com.codex.app.models.Comment

class ProfileCommentAdapter(
    private val onCommentClick: (Comment) -> Unit
) : RecyclerView.Adapter<ProfileCommentAdapter.VH>() {

    private var comments: List<Comment> = emptyList()

    fun submitList(list: List<Comment>) {
        comments = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemProfileCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(comments[position])

    override fun getItemCount() = comments.size

    inner class VH(private val binding: ItemProfileCommentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(comment: Comment) {
            binding.commentPreview.text = comment.text
            binding.commentMeta.text = PostAdapter.getRelativeTime(comment.createdAt)
            binding.root.setOnClickListener { onCommentClick(comment) }
        }
    }
}