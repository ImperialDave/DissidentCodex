package com.codex.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.codex.app.R
import com.codex.app.databinding.ItemCommentBinding
import com.codex.app.models.Comment
import com.codex.app.models.MediaType
import com.codex.app.models.Role
import com.codex.app.utils.CommentThread
import com.codex.app.utils.CommentThreadItem
import com.codex.app.utils.MediaUploadHelper

class CommentAdapter(
    private val likedCommentIds: () -> Set<String>,
    private val onAuthorClick: (String) -> Unit,
    private val onLikeClick: (Comment) -> Unit,
    private val onReplyClick: (Comment) -> Unit,
    private val canDeleteComment: (Comment) -> Boolean = { false },
    private val onDeleteClick: ((Comment) -> Unit)? = null
) : RecyclerView.Adapter<CommentAdapter.VH>() {

    private var items: List<CommentThreadItem> = emptyList()

    fun submit(comments: List<Comment>) {
        items = CommentThread.flatten(comments)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun onViewRecycled(holder: VH) {
        holder.release()
        super.onViewRecycled(holder)
    }

    override fun getItemCount() = items.size

    inner class VH(private val b: ItemCommentBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: CommentThreadItem) {
            val c = item.comment
            val density = b.root.resources.displayMetrics.density
            val basePad = (4 * density).toInt()
            val vertPad = (8 * density).toInt()
            val depthPad = (20 * item.depth * density).toInt()
            b.commentRoot.setPadding(depthPad + basePad, vertPad, basePad, vertPad)

            b.commentAuthor.text = c.authorName
            b.commentText.text = c.text
            b.commentText.visibility = if (c.text.isBlank()) View.GONE else View.VISIBLE
            b.commentTime.text = PostAdapter.getRelativeTime(c.createdAt)

            if (c.isReply() && !c.replyToAuthorName.isNullOrBlank()) {
                b.replyToText.visibility = View.VISIBLE
                b.replyToText.text = b.root.context.getString(R.string.replying_to, c.replyToAuthorName)
            } else {
                b.replyToText.visibility = View.GONE
            }

            Glide.with(b.commentAvatar)
                .load(c.authorPhotoUrl ?: R.drawable.default_avatar)
                .placeholder(R.drawable.default_avatar)
                .into(b.commentAvatar)

            val badgeRes = when (c.getRole()) {
                Role.FOUNDER -> R.drawable.badge_founder
                Role.ADMIN -> R.drawable.badge_admin
                Role.MOD -> R.drawable.badge_mod
                Role.SUSPENDED -> R.drawable.badge_suspended
                Role.BANNED -> R.drawable.badge_banned
                else -> R.drawable.badge_user
            }
            b.commentBadge.setImageResource(badgeRes)

            if (MediaType.isVideo(c.mediaType, c.imageUrl)) {
                b.commentImage.visibility = View.GONE
                MediaUploadHelper.bindVideoPlayer(b.commentVideo, c.imageUrl, c.mediaType)
            } else {
                MediaUploadHelper.releasePlayer(b.commentVideo)
                MediaUploadHelper.loadMediaInto(b.commentImage, c.imageUrl, c.mediaType)
                if (!c.imageUrl.isNullOrBlank()) {
                    b.commentImage.setOnClickListener {
                        MediaUploadHelper.openLightbox(b.root.context, c.imageUrl, c.mediaType)
                    }
                } else {
                    b.commentImage.setOnClickListener(null)
                }
            }

            val liked = likedCommentIds().contains(c.id)
            b.commentLikeIcon.alpha = if (liked) 1f else 0.45f
            b.commentLikeCount.text = if (c.likeCount > 0) c.likeCount.toString() else ""

            val authorClick = View.OnClickListener { onAuthorClick(c.authorId) }
            b.commentAvatar.setOnClickListener(authorClick)
            b.commentAuthor.setOnClickListener(authorClick)
            b.commentLikeIcon.setOnClickListener { onLikeClick(c) }
            b.replyBtn.setOnClickListener { onReplyClick(c) }

            val canDelete = canDeleteComment(c) && onDeleteClick != null
            b.deleteCommentBtn.visibility = if (canDelete) View.VISIBLE else View.GONE
            if (canDelete) {
                b.deleteCommentBtn.setOnClickListener { onDeleteClick?.invoke(c) }
            }
        }

        fun release() {
            MediaUploadHelper.releasePlayer(b.commentVideo)
        }
    }
}