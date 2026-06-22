package com.codex.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.codex.app.utils.setupInsideScrollView
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.codex.app.R
import com.codex.app.adapters.CommentAdapter
import com.codex.app.adapters.PostAdapter
import com.codex.app.databinding.ActivityPostDetailBinding
import com.codex.app.models.Comment
import com.codex.app.models.Post
import com.codex.app.models.Role
import com.codex.app.utils.FirebaseHelper
import com.codex.app.utils.MediaUploadHelper
import com.codex.app.utils.TypingPreviewHelper
import com.codex.app.utils.WindowInsetsHelper
import kotlinx.coroutines.launch

class PostDetailActivity : BaseThemedActivity(), GifPickerBottomSheet.Listener {

    private lateinit var binding: ActivityPostDetailBinding
    private var postId: String? = null
    private var currentPost: Post? = null
    private lateinit var commentAdapter: CommentAdapter

    private var commentImageUri: Uri? = null
    private var commentRemoteUrl: String? = null
    private var commentMediaType: String? = null
    private var replyingTo: Comment? = null

    private val pickCommentVideo = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                commentImageUri = uri
                commentRemoteUrl = null
                commentMediaType = com.codex.app.models.MediaType.VIDEO
                binding.commentMediaPreviewRow.visibility = View.VISIBLE
                binding.commentMediaPreview.setImageResource(R.drawable.ic_chat)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPostDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        postId = intent.getStringExtra("postId")
        if (postId == null) {
            finish()
            return
        }

        binding.toolbar.setNavigationOnClickListener { finish() }
        WindowInsetsHelper.applyCommentSafeArea(
            binding.toolbar,
            binding.commentBar,
            binding.contentScroll
        )

        setupCommentsRecycler()
        loadPostAndComments()

        TypingPreviewHelper.setup(binding.commentInput, binding.typingPreview) {
            binding.contentScroll.post {
                binding.contentScroll.smoothScrollTo(
                    0,
                    binding.contentScroll.getChildAt(0).height
                )
            }
        }
        binding.sendCommentBtn.setOnClickListener { sendComment() }
        binding.attachCommentMediaBtn.setOnClickListener { GifPickerBottomSheet.show(this) }
        binding.attachCommentVideoBtn.setOnClickListener {
            pickCommentVideo.launch(Intent(Intent.ACTION_GET_CONTENT).apply { type = "video/*" })
        }
        binding.removeCommentMediaBtn.setOnClickListener { clearCommentMedia() }
        binding.cancelReplyBtn.setOnClickListener { clearReplyTarget() }
        binding.detailLikeIcon.setOnClickListener { togglePostLike() }
        binding.shareButton.setOnClickListener {
            currentPost?.let { p ->
                val shareText = "${p.title}\n\n${p.body.take(300)}\n\n— via Codex"
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                }
                startActivity(android.content.Intent.createChooser(intent, "Share post"))
            }
        }
    }

    override fun onGifSelected(selection: GifPickerBottomSheet.Selection) {
        when (selection) {
            is GifPickerBottomSheet.Selection.Local -> {
                commentImageUri = selection.uri
                commentRemoteUrl = null
                commentMediaType = selection.mediaType
                binding.commentMediaPreviewRow.visibility = View.VISIBLE
                if (selection.mediaType == com.codex.app.models.MediaType.GIF) {
                    Glide.with(this).asGif().load(selection.uri).fitCenter()
                        .into(binding.commentMediaPreview)
                } else {
                    Glide.with(this).load(selection.uri).centerCrop()
                        .into(binding.commentMediaPreview)
                }
            }
            is GifPickerBottomSheet.Selection.Remote -> {
                commentImageUri = null
                commentRemoteUrl = selection.url
                commentMediaType = selection.mediaType
                binding.commentMediaPreviewRow.visibility = View.VISIBLE
                Glide.with(this).asGif().load(selection.url).fitCenter()
                    .into(binding.commentMediaPreview)
            }
        }
    }

    private fun clearCommentMedia() {
        commentImageUri = null
        commentRemoteUrl = null
        commentMediaType = null
        binding.commentMediaPreviewRow.visibility = View.GONE
    }

    private fun setupCommentsRecycler() {
        val uid = FirebaseHelper.getCurrentFirebaseUser()?.uid.orEmpty()
        commentAdapter = CommentAdapter(
            likedCommentIds = { FirebaseHelper.getCachedLikedCommentIds() },
            onAuthorClick = { authorId ->
                startActivity(UserProfileActivity.intent(this, authorId))
            },
            onLikeClick = { comment -> toggleCommentLike(comment) },
            onReplyClick = { comment -> startReplyTo(comment) },
            canDeleteComment = { comment ->
                val role = FirebaseHelper.resolveRole(FirebaseHelper.currentUser)
                role.canModerate() || comment.authorId == uid
            },
            onDeleteClick = { comment -> confirmDeleteComment(comment) }
        )
        binding.commentsRecycler.setupInsideScrollView()
        binding.commentsRecycler.adapter = commentAdapter
    }

    private fun loadPostAndComments() {
        val pid = postId ?: return
        lifecycleScope.launch {
            FirebaseHelper.refreshLikedCommentCache()
            val post = FirebaseHelper.getPost(pid)
            currentPost = post
            if (post == null) {
                Toast.makeText(this@PostDetailActivity, "Post not found", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            binding.detailTitle.text = post.title
            binding.detailBody.text = post.body
            binding.detailCategory.text = post.category
            binding.detailLikeCount.text = post.likeCount.toString()
            val liked = FirebaseHelper.hasLikedPost(post.id)
            binding.detailLikeIcon.alpha = if (liked) 1f else 0.45f
            binding.detailCommentCount.text = post.commentCount.toString()
            binding.detailAuthor.text = post.authorName
            binding.detailTime.text = PostAdapter.getRelativeTime(post.createdAt)

            val avatar = post.authorPhotoUrl
            Glide.with(this@PostDetailActivity)
                .load(if (!avatar.isNullOrEmpty()) avatar else R.drawable.default_avatar)
                .placeholder(R.drawable.default_avatar)
                .into(binding.detailAvatar)

            val badge = when (post.getRole()) {
                Role.FOUNDER -> R.drawable.badge_founder
                Role.ADMIN -> R.drawable.badge_admin
                Role.MOD -> R.drawable.badge_mod
                Role.SUSPENDED -> R.drawable.badge_suspended
                Role.BANNED -> R.drawable.badge_banned
                else -> R.drawable.badge_user
            }
            binding.detailRoleBadge.setImageResource(badge)

            val openAuthor = View.OnClickListener {
                startActivity(UserProfileActivity.intent(this@PostDetailActivity, post.authorId))
            }
            binding.detailAvatar.setOnClickListener(openAuthor)
            binding.detailAuthor.setOnClickListener(openAuthor)

            MediaUploadHelper.loadMediaInto(binding.detailImage, post.imageUrl, post.mediaType)
            if (!post.imageUrl.isNullOrBlank()) {
                binding.detailImage.setOnClickListener {
                    MediaUploadHelper.openLightbox(this@PostDetailActivity, post.imageUrl, post.mediaType)
                }
            }

            val myRole = FirebaseHelper.resolveRole(FirebaseHelper.currentUser)
            val canDelete = myRole.canModerate() || post.authorId == FirebaseHelper.getCurrentFirebaseUser()?.uid
            binding.detailDeleteBtn.visibility = if (canDelete) View.VISIBLE else View.GONE
            binding.detailDeleteBtn.setOnClickListener {
                lifecycleScope.launch {
                    val res = FirebaseHelper.deletePost(pid, myRole)
                    if (res.isFailure) {
                        Toast.makeText(
                            this@PostDetailActivity,
                            res.exceptionOrNull()?.message ?: "Delete failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    finish()
                }
            }

            if (myRole.canModerate()) {
                binding.detailHideBtn.visibility = View.VISIBLE
                binding.detailHideBtn.text = getString(
                    if (post.hiddenFromFeed) R.string.unhide_from_feed else R.string.hide_from_feed
                )
                binding.detailHideBtn.setOnClickListener {
                    lifecycleScope.launch {
                        val res = FirebaseHelper.togglePostFeedVisibility(pid)
                        if (res.isSuccess) {
                            loadPostAndComments()
                        } else {
                            Toast.makeText(
                                this@PostDetailActivity,
                                res.exceptionOrNull()?.message ?: getString(R.string.hide_post_failed),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } else {
                binding.detailHideBtn.visibility = View.GONE
            }
            binding.detailHiddenBadge.visibility =
                if (post.hiddenFromFeed && myRole.canModerate()) View.VISIBLE else View.GONE

            val comments = FirebaseHelper.getComments(pid)
            commentAdapter.submit(comments)
            binding.commentsRecycler.visibility = View.VISIBLE
            binding.noCommentsText.visibility = if (comments.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun togglePostLike() {
        val pid = postId ?: return
        lifecycleScope.launch {
            val res = FirebaseHelper.toggleLikePost(pid)
            if (res.isSuccess) {
                loadPostAndComments()
            } else {
                Toast.makeText(
                    this@PostDetailActivity,
                    res.exceptionOrNull()?.message ?: "Like failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun toggleCommentLike(comment: Comment) {
        lifecycleScope.launch {
            val res = FirebaseHelper.toggleLikeComment(comment.id)
            if (res.isSuccess) {
                loadPostAndComments()
            } else {
                Toast.makeText(
                    this@PostDetailActivity,
                    res.exceptionOrNull()?.message ?: "Like failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun startReplyTo(comment: Comment) {
        replyingTo = comment
        binding.replyBar.visibility = View.VISIBLE
        binding.replyHintText.text = getString(R.string.replying_to, comment.authorName)
        binding.commentInput.requestFocus()
    }

    private fun clearReplyTarget() {
        replyingTo = null
        binding.replyBar.visibility = View.GONE
    }

    private fun confirmDeleteComment(comment: Comment) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setMessage(getString(R.string.delete_comment))
            .setPositiveButton(R.string.delete_post) { _, _ ->
                lifecycleScope.launch {
                    val res = FirebaseHelper.deleteComment(comment.id, comment.postId)
                    if (res.isSuccess) {
                        loadPostAndComments()
                    } else {
                        Toast.makeText(
                            this@PostDetailActivity,
                            res.exceptionOrNull()?.message ?: getString(R.string.error_generic),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun sendComment() {
        val text = binding.commentInput.text?.toString()?.trim().orEmpty()
        val pid = postId ?: return
        if (text.isBlank() && commentImageUri == null && commentRemoteUrl == null) return
        if (text.length > 1000) {
            Toast.makeText(this, "Comment too long (max 1000 chars)", Toast.LENGTH_SHORT).show()
            return
        }

        val role = FirebaseHelper.resolveRole(FirebaseHelper.currentUser)
        if (!role.canComment()) {
            Toast.makeText(this, getString(R.string.cannot_post_suspended), Toast.LENGTH_SHORT).show()
            return
        }

        val parent = replyingTo
        binding.sendCommentBtn.isEnabled = false
        lifecycleScope.launch {
            try {
                var imageUrl = commentRemoteUrl
                var mediaType = commentMediaType
                if (commentImageUri != null) {
                    val upload = if (commentMediaType == com.codex.app.models.MediaType.VIDEO) {
                        FirebaseHelper.uploadCommentVideo(commentImageUri!!)
                    } else {
                        FirebaseHelper.uploadMedia(commentImageUri!!, "comment_images")
                    }
                    if (upload.isFailure) {
                        Toast.makeText(
                            this@PostDetailActivity,
                            upload.exceptionOrNull()?.message ?: getString(R.string.image_upload_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                        return@launch
                    }
                    imageUrl = upload.getOrNull()?.url
                    mediaType = upload.getOrNull()?.mediaType
                }

                val res = FirebaseHelper.addComment(
                    postId = pid,
                    text = text,
                    imageUrl = imageUrl,
                    mediaType = mediaType,
                    parentCommentId = parent?.id,
                    replyToAuthorName = parent?.authorName
                )
                if (res.isSuccess) {
                    TypingPreviewHelper.clearPreview(binding.commentInput, binding.typingPreview)
                    clearCommentMedia()
                    clearReplyTarget()
                    loadPostAndComments()
                } else {
                    Toast.makeText(
                        this@PostDetailActivity,
                        res.exceptionOrNull()?.message ?: "Could not post comment",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@PostDetailActivity,
                    e.message ?: "Could not post comment",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                binding.sendCommentBtn.isEnabled = true
            }
        }
    }
}