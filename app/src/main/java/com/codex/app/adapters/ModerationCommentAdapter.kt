package com.codex.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.codex.app.databinding.ItemModerationCommentBinding
import com.codex.app.models.Comment

class ModerationCommentAdapter(
    private val onDeleteClick: (Comment) -> Unit
) : RecyclerView.Adapter<ModerationCommentAdapter.VH>() {

    private var comments: List<Comment> = emptyList()

    fun submitList(newComments: List<Comment>) {
        comments = newComments
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemModerationCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(comments[position])
    }

    override fun getItemCount() = comments.size

    inner class VH(private val binding: ItemModerationCommentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(comment: Comment) {
            binding.commentAuthor.text = comment.authorName
            binding.commentTime.text = PostAdapter.getRelativeTime(comment.createdAt)
            binding.commentText.text = comment.text
            binding.deleteCommentBtn.setOnClickListener { onDeleteClick(comment) }
        }
    }
}