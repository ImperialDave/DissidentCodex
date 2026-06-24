package com.codex.app.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.codex.app.utils.setupInsideScrollView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.codex.app.R
import com.codex.app.adapters.ModerationChatAdapter
import com.codex.app.adapters.ModerationCommentAdapter
import com.codex.app.adapters.ModerationTopicAdapter
import com.codex.app.adapters.ModerationUserAdapter
import com.codex.app.adapters.PostAdapter
import com.codex.app.databinding.ActivityModToolsBinding
import com.codex.app.models.Post
import com.codex.app.models.Role
import com.codex.app.models.User
import com.codex.app.utils.FirebaseHelper
import com.codex.app.utils.ModerationUiHelper
import com.codex.app.utils.WindowInsetsHelper
import kotlinx.coroutines.launch

/**
 * Moderator / Admin tools. Founder accounts are redirected to [FounderToolsActivity].
 */
class ModToolsActivity : BaseThemedActivity() {

    private lateinit var binding: ActivityModToolsBinding
    private lateinit var userAdapter: ModerationUserAdapter
    private lateinit var postAdapter: PostAdapter
    private lateinit var commentAdapter: ModerationCommentAdapter
    private lateinit var chatAdapter: ModerationChatAdapter
    private lateinit var topicAdapter: ModerationTopicAdapter

    private var allUsers = listOf<User>()
    private var allPosts = listOf<Post>()
    private var showActiveOnly = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Founder always uses the dedicated founder menu
        if (FirebaseHelper.isFounderAccount()) {
            startActivity(Intent(this, FounderToolsActivity::class.java))
            finish()
            return
        }

        val actorRole = FirebaseHelper.resolveRole(FirebaseHelper.currentUser)
        if (!actorRole.canModerate()) {
            Toast.makeText(this, getString(R.string.no_privileges), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding = ActivityModToolsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.mod_tools_title)
        WindowInsetsHelper.applyTopSafeArea(binding.modToolsRoot)

        setupUserList()
        setupPostList(actorRole)
        setupCommentList()
        setupChatList()
        setupTopicList()
        setupSearchAndFilters()
        setupButtons()
        loadEverything()
    }

    private fun setupUserList() {
        userAdapter = ModerationUserAdapter { user ->
            ModerationUiHelper.showQuickActions(
                context = this,
                scope = lifecycleScope,
                target = user,
                actorRole = FirebaseHelper.resolveRole(FirebaseHelper.currentUser),
                includeFounder = false,
                onSuccess = { loadUsers() }
            )
        }
        binding.usersRecycler.setupInsideScrollView()
        binding.usersRecycler.adapter = userAdapter
    }

    private fun setupPostList(actorRole: Role) {
        postAdapter = PostAdapter(
            onPostClick = { post ->
                startActivity(Intent(this, PostDetailActivity::class.java).putExtra("postId", post.id))
            },
            onLikeClick = { },
            onDeleteClick = { post ->
                lifecycleScope.launch {
                    val res = FirebaseHelper.deletePost(post.id, actorRole)
                    if (res.isSuccess) {
                        Toast.makeText(this@ModToolsActivity, "Post deleted", Toast.LENGTH_SHORT).show()
                        loadPosts()
                    } else {
                        Toast.makeText(this@ModToolsActivity, res.exceptionOrNull()?.message ?: "Delete failed", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            currentUserRole = { FirebaseHelper.resolveRole(FirebaseHelper.currentUser) },
            onCategoryClick = null,
            onAuthorClick = null,
            onHideClick = { post ->
                lifecycleScope.launch {
                    val res = FirebaseHelper.togglePostFeedVisibility(post.id)
                    if (res.isSuccess) {
                        loadPosts()
                    } else {
                        Toast.makeText(
                            this@ModToolsActivity,
                            res.exceptionOrNull()?.message ?: getString(R.string.hide_post_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
        binding.modPostsRecycler.setupInsideScrollView()
        binding.modPostsRecycler.adapter = postAdapter
    }

    private fun setupCommentList() {
        commentAdapter = ModerationCommentAdapter { comment ->
            lifecycleScope.launch {
                val res = FirebaseHelper.deleteComment(comment.id, comment.postId)
                if (res.isSuccess) {
                    Toast.makeText(this@ModToolsActivity, getString(R.string.comment_deleted), Toast.LENGTH_SHORT).show()
                    loadComments()
                } else {
                    Toast.makeText(this@ModToolsActivity, res.exceptionOrNull()?.message ?: "Delete failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
        binding.commentsRecycler.setupInsideScrollView()
        binding.commentsRecycler.adapter = commentAdapter
    }

    private fun setupSearchAndFilters() {
        // Default: show ALL users (active-only was hiding everyone without lastActive)
        binding.chipAllUsers.isChecked = true
        binding.chipActiveOnly.isChecked = false
        showActiveOnly = false

        binding.chipAllUsers.setOnClickListener {
            showActiveOnly = false
            binding.chipAllUsers.isChecked = true
            binding.chipActiveOnly.isChecked = false
            refreshUserList()
        }
        binding.chipActiveOnly.setOnClickListener {
            showActiveOnly = true
            binding.chipActiveOnly.isChecked = true
            binding.chipAllUsers.isChecked = false
            refreshUserList()
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                refreshUserList()
                refreshPostList()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        binding.userSearchInput.addTextChangedListener(watcher)
        binding.postSearchInput.addTextChangedListener(watcher)
    }

    private fun setupChatList() {
        chatAdapter = ModerationChatAdapter { room, msg ->
            lifecycleScope.launch {
                val res = FirebaseHelper.deleteChatMessage(room.id, msg.id, msg.authorId)
                if (res.isSuccess) {
                    Toast.makeText(this@ModToolsActivity, getString(R.string.message_deleted), Toast.LENGTH_SHORT).show()
                    loadChatMessages()
                } else {
                    Toast.makeText(this@ModToolsActivity, res.exceptionOrNull()?.message ?: "Delete failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
        binding.chatMessagesRecycler.setupInsideScrollView()
        binding.chatMessagesRecycler.adapter = chatAdapter
    }

    private fun setupTopicList() {
        topicAdapter = ModerationTopicAdapter(
            onLock = { room ->
                lifecycleScope.launch {
                    val res = FirebaseHelper.lockTopicRoom(room.id, !room.locked)
                    if (res.isSuccess) {
                        Toast.makeText(this@ModToolsActivity, R.string.action_success, Toast.LENGTH_SHORT).show()
                        loadTopicRooms()
                    } else {
                        Toast.makeText(this@ModToolsActivity, res.exceptionOrNull()?.message ?: "Failed", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onReset = { room ->
                lifecycleScope.launch {
                    val res = FirebaseHelper.resetTopicRoom(room.id)
                    if (res.isSuccess) {
                        Toast.makeText(this@ModToolsActivity, R.string.topic_reset, Toast.LENGTH_SHORT).show()
                        loadTopicRooms()
                    } else {
                        Toast.makeText(this@ModToolsActivity, res.exceptionOrNull()?.message ?: "Failed", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onBan = { room ->
                AlertDialog.Builder(this)
                    .setMessage(getString(R.string.ban_topic_confirm, room.title))
                    .setPositiveButton(R.string.ban_topic) { _, _ ->
                        lifecycleScope.launch {
                            val res = FirebaseHelper.banTopic(room.title, room.topicId)
                            if (res.isSuccess) {
                                Toast.makeText(this@ModToolsActivity, R.string.topic_banned, Toast.LENGTH_SHORT).show()
                                loadTopicRooms()
                            } else {
                                Toast.makeText(
                                    this@ModToolsActivity,
                                    res.exceptionOrNull()?.message ?: "Failed",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            },
            onDelete = { room ->
                lifecycleScope.launch {
                    val res = FirebaseHelper.deleteTopicAndCategoryFully(
                        categoryName = room.title,
                        categoryId = room.topicId
                    )
                    if (res.isSuccess) {
                        Toast.makeText(this@ModToolsActivity, R.string.topic_deleted, Toast.LENGTH_SHORT).show()
                        loadTopicRooms()
                    } else {
                        Toast.makeText(this@ModToolsActivity, res.exceptionOrNull()?.message ?: "Failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
        binding.topicRoomsRecycler.setupInsideScrollView()
        binding.topicRoomsRecycler.adapter = topicAdapter
    }

    private fun setupButtons() {
        binding.manageTopicsBtn.setOnClickListener {
            startActivity(Intent(this, ModerationManageActivity::class.java))
        }
        binding.refreshUsersBtn.setOnClickListener { loadUsers() }
        binding.refreshPostsBtn.setOnClickListener { loadPosts() }
        binding.refreshCommentsBtn.setOnClickListener { loadComments() }
        binding.refreshChatBtn.setOnClickListener { loadChatMessages() }
        binding.refreshTopicsBtn.setOnClickListener { loadTopicRooms() }
    }

    private fun loadEverything() {
        loadUsers()
        loadPosts()
        loadComments()
        loadChatMessages()
        loadTopicRooms()
    }

    private fun loadUsers() {
        lifecycleScope.launch {
            binding.userListStatus.text = getString(R.string.loading)
            val result = FirebaseHelper.getUsersForModeration(300)
            allUsers = result.users

            if (result.error != null) {
                Toast.makeText(this@ModToolsActivity, result.error, Toast.LENGTH_LONG).show()
            } else if (allUsers.isEmpty()) {
                Toast.makeText(this@ModToolsActivity, getString(R.string.no_users_found), Toast.LENGTH_LONG).show()
            }

            updateStats()
            refreshUserList()
        }
    }

    private fun updateStats() {
        val stats = FirebaseHelper.computeModerationStats(allUsers)
        binding.statTotalText.text = "${getString(R.string.stats_total)}\n${stats.total}"
        binding.statActiveText.text = "${getString(R.string.stats_active)}\n${stats.activeRecently}"
        binding.statSuspendedText.text = "${getString(R.string.stats_suspended)}\n${stats.suspended}"
        binding.statBannedText.text = "${getString(R.string.stats_banned)}\n${stats.banned}"
    }

    private fun refreshUserList() {
        val query = binding.userSearchInput.text?.toString().orEmpty()
        val filtered = FirebaseHelper.filterUsers(allUsers, query, activeOnly = showActiveOnly)
        userAdapter.submitList(filtered)

        binding.usersEmptyText.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.usersEmptyText.text = when {
            allUsers.isEmpty() -> getString(R.string.no_users_found)
            showActiveOnly && filtered.isEmpty() -> "No users active in the last 24 hours. Try \"All users\"."
            query.isNotBlank() && filtered.isEmpty() -> getString(R.string.no_users_match)
            else -> getString(R.string.no_users_match)
        }

        val activeCount = FirebaseHelper.computeModerationStats(allUsers).activeRecently
        binding.userListStatus.text = getString(R.string.users_loaded, filtered.size) +
            " · " + getString(R.string.active_users_count, activeCount)
    }

    private fun loadPosts() {
        lifecycleScope.launch {
            FirebaseHelper.getPosts(null, 100, includeHidden = true)
                .onSuccess { allPosts = it; refreshPostList() }
                .onFailure { err ->
                    allPosts = emptyList()
                    refreshPostList()
                    Toast.makeText(this@ModToolsActivity, err.message ?: "Failed to load posts", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun refreshPostList() {
        val q = binding.postSearchInput.text?.toString()?.trim()?.lowercase().orEmpty()
        val filtered = if (q.isEmpty()) {
            allPosts
        } else {
            allPosts.filter { p ->
                p.title.lowercase().contains(q) ||
                    p.body.lowercase().contains(q) ||
                    p.authorName.lowercase().contains(q)
            }
        }
        postAdapter.submitList(filtered)
        binding.modEmptyText.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun loadComments() {
        lifecycleScope.launch {
            val comments = FirebaseHelper.getRecentComments(30)
            commentAdapter.submitList(comments)
            binding.commentsEmptyText.visibility = if (comments.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun loadChatMessages() {
        lifecycleScope.launch {
            val messages = FirebaseHelper.getRecentChatMessages(25)
            chatAdapter.submitList(messages)
            binding.chatEmptyText.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun loadTopicRooms() {
        lifecycleScope.launch {
            val rooms = FirebaseHelper.getTopicRooms()
            topicAdapter.submitList(rooms)
            binding.topicsEmptyText.visibility = if (rooms.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}