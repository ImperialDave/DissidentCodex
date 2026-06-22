package com.codex.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.codex.app.R
import com.codex.app.databinding.ItemChatMessageReceivedBinding
import com.codex.app.databinding.ItemChatMessageSentBinding
import com.codex.app.models.ChatMessage
import com.codex.app.models.MediaType
import com.codex.app.models.Role
import com.codex.app.utils.MediaUploadHelper


class ChatMessageAdapter(
    private val onLongPress: ((ChatMessage) -> Unit)? = null,
    private val onAuthorClick: ((String) -> Unit)? = null
) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(Diff) {

    private var currentUserId: String = ""

    fun setCurrentUserId(uid: String) {
        currentUserId = uid
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).authorId == currentUserId) TYPE_SENT else TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SENT -> SentHolder(ItemChatMessageSentBinding.inflate(inflater, parent, false))
            else -> ReceivedHolder(ItemChatMessageReceivedBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = getItem(position)
        when (holder) {
            is SentHolder -> holder.bind(msg)
            is ReceivedHolder -> holder.bind(msg)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        when (holder) {
            is SentHolder -> holder.release()
            is ReceivedHolder -> holder.release()
        }
        super.onViewRecycled(holder)
    }

    private fun bindMessageContent(
        msg: ChatMessage,
        bubble: android.widget.TextView,
        mediaView: android.widget.ImageView,
        videoView: androidx.media3.ui.PlayerView
    ) {
        if (msg.text.isBlank()) {
            bubble.visibility = View.GONE
        } else {
            bubble.visibility = View.VISIBLE
            bubble.text = msg.text
        }

        if (MediaType.isVideo(msg.mediaType, msg.imageUrl)) {
            mediaView.visibility = View.GONE
            mediaView.setOnClickListener(null)
            MediaUploadHelper.bindVideoPlayer(videoView, msg.imageUrl, msg.mediaType)
        } else {
            MediaUploadHelper.releasePlayer(videoView)
            MediaUploadHelper.loadMediaInto(mediaView, msg.imageUrl, msg.mediaType)
            if (!msg.imageUrl.isNullOrBlank()) {
                mediaView.setOnClickListener {
                    MediaUploadHelper.openLightbox(mediaView.context, msg.imageUrl, msg.mediaType)
                }
            } else {
                mediaView.setOnClickListener(null)
            }
        }
    }

    inner class SentHolder(private val binding: ItemChatMessageSentBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(msg: ChatMessage) {
            bindMessageContent(msg, binding.msgBubble, binding.msgMedia, binding.msgVideo)
            binding.msgTime.text = PostAdapter.getRelativeTime(msg.createdAt)
            binding.root.setOnLongClickListener {
                onLongPress?.invoke(msg)
                true
            }
        }

        fun release() {
            MediaUploadHelper.releasePlayer(binding.msgVideo)
        }
    }

    inner class ReceivedHolder(private val binding: ItemChatMessageReceivedBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(msg: ChatMessage) {
            binding.msgAuthor.text = msg.authorName
            bindMessageContent(msg, binding.msgBubble, binding.msgMedia, binding.msgVideo)
            binding.msgTime.text = PostAdapter.getRelativeTime(msg.createdAt)

            Glide.with(binding.root)
                .load(msg.authorPhotoUrl?.takeIf { it.isNotBlank() } ?: R.drawable.default_avatar)
                .placeholder(R.drawable.default_avatar)
                .into(binding.msgAvatar)

            val authorClick = android.view.View.OnClickListener { onAuthorClick?.invoke(msg.authorId) }
            binding.msgAuthor.setOnClickListener(authorClick)
            binding.msgAvatar.setOnClickListener(authorClick)

            val badge = when (msg.getRole()) {
                Role.FOUNDER -> R.drawable.badge_founder
                Role.ADMIN -> R.drawable.badge_admin
                Role.MOD -> R.drawable.badge_mod
                Role.SUSPENDED -> R.drawable.badge_suspended
                Role.BANNED -> R.drawable.badge_banned
                else -> R.drawable.badge_user
            }
            binding.msgRoleBadge.setImageResource(badge)

            binding.root.setOnLongClickListener {
                onLongPress?.invoke(msg)
                true
            }
        }

        fun release() {
            MediaUploadHelper.releasePlayer(binding.msgVideo)
        }
    }

    companion object {
        private const val TYPE_SENT = 1
        private const val TYPE_RECEIVED = 2

        private val Diff = object : DiffUtil.ItemCallback<ChatMessage>() {
            override fun areItemsTheSame(a: ChatMessage, b: ChatMessage) = a.id == b.id
            override fun areContentsTheSame(a: ChatMessage, b: ChatMessage) = a == b
        }
    }
}