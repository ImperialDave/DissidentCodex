package com.codex.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.codex.app.R
import com.codex.app.databinding.ItemPostBinding
import com.codex.app.models.Post
import com.codex.app.models.Role
import com.codex.app.utils.FirebaseHelper
import com.codex.app.utils.MediaUploadHelper
import java.text.SimpleDateFormat
import java.util.*

class PostAdapter(
    private val onPostClick: (Post) -> Unit,
    private val onLikeClick: (Post) -> Unit,
    private val onDeleteClick: (Post) -> Unit,
    private val currentUserRole: () -> Role,
    private val onCategoryClick: ((String) -> Unit)? = null,
    private val onAuthorClick: ((Post) -> Unit)? = null,
    private val likedPostIds: () -> Set<String> = { emptySet() },
    private val onHideClick: ((Post) -> Unit)? = null
) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    private var posts: List<Post> = emptyList()

    fun submitList(newPosts: List<Post>) {
        posts = newPosts
        notifyDataSetChanged()
    }

    fun notifyLikesChanged() {
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemPostBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(posts[position])
    }

    override fun getItemCount() = posts.size

    inner class PostViewHolder(private val binding: ItemPostBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(post: Post) {
            binding.authorName.text = post.authorName
            binding.postTitle.text = post.title
            binding.postBody.text = post.body.take(180) + if (post.body.length > 180) "…" else ""
            binding.timeText.text = getRelativeTime(post.createdAt)
            binding.likeCount.text = post.likeCount.toString()
            binding.commentCount.text = post.commentCount.toString()
            binding.categoryChip.text = post.category
            if (onCategoryClick != null) {
                binding.categoryChip.setOnClickListener { onCategoryClick.invoke(post.category) }
                binding.categoryChip.isClickable = true
            }

            val avatar = post.authorPhotoUrl
            if (!avatar.isNullOrEmpty()) {
                Glide.with(binding.authorAvatar)
                    .load(avatar)
                    .placeholder(R.drawable.default_avatar)
                    .into(binding.authorAvatar)
            } else {
                binding.authorAvatar.setImageResource(R.drawable.default_avatar)
            }

            val authorClick = View.OnClickListener { onAuthorClick?.invoke(post) }
            binding.authorAvatar.setOnClickListener(authorClick)
            binding.authorName.setOnClickListener(authorClick)

            val badgeRes = when (post.getRole()) {
                Role.FOUNDER -> R.drawable.badge_founder
                Role.ADMIN -> R.drawable.badge_admin
                Role.MOD -> R.drawable.badge_mod
                Role.SUSPENDED -> R.drawable.badge_suspended
                Role.BANNED -> R.drawable.badge_banned
                else -> R.drawable.badge_user
            }
            binding.roleBadge.setImageResource(badgeRes)

            MediaUploadHelper.loadMediaInto(binding.postImage, post.imageUrl, post.mediaType)
            if (!post.imageUrl.isNullOrBlank()) {
                binding.postImage.setOnClickListener {
                    MediaUploadHelper.openLightbox(binding.root.context, post.imageUrl, post.mediaType)
                }
            } else {
                binding.postImage.setOnClickListener(null)
            }

            val role = currentUserRole()
            val canDelete = role.canModerate() || post.authorId == FirebaseHelper.getCurrentFirebaseUser()?.uid
            binding.deleteButton.visibility = if (canDelete) View.VISIBLE else View.GONE
            binding.deleteButton.setOnClickListener { onDeleteClick(post) }

            val canModerate = role.canModerate()
            if (canModerate && onHideClick != null) {
                binding.hideButton.visibility = View.VISIBLE
                binding.hideButton.text = binding.root.context.getString(
                    if (post.hiddenFromFeed) R.string.unhide_from_feed else R.string.hide_from_feed
                )
                binding.hideButton.setOnClickListener { onHideClick.invoke(post) }
                binding.hiddenBadge.visibility = if (post.hiddenFromFeed) View.VISIBLE else View.GONE
                binding.root.alpha = if (post.hiddenFromFeed) 0.72f else 1f
            } else {
                binding.hideButton.visibility = View.GONE
                binding.hiddenBadge.visibility = View.GONE
                binding.root.alpha = 1f
            }

            binding.root.setOnClickListener { onPostClick(post) }

            val liked = likedPostIds().contains(post.id)
            binding.likeIcon.alpha = if (liked) 1f else 0.45f
            binding.likeIcon.setOnClickListener { onLikeClick(post) }
        }
    }

    companion object {
        private val timeFormat = SimpleDateFormat("MMM d", Locale.getDefault())

        fun getRelativeTime(ts: com.google.firebase.Timestamp?): String {
            if (ts == null) return ""
            val diff = System.currentTimeMillis() - ts.toDate().time
            val min = diff / 60000
            if (min < 1) return "just now"
            if (min < 60) return "${min}m ago"
            val hr = min / 60
            if (hr < 24) return "${hr}h ago"
            val day = hr / 24
            return if (day < 7) "${day}d ago" else timeFormat.format(ts.toDate())
        }
    }
}