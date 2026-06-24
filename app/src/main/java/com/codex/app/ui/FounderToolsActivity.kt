package com.codex.app.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import com.codex.app.utils.setupInsideScrollView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.codex.app.R
import com.codex.app.adapters.ModerationCommentAdapter
import com.codex.app.adapters.ModerationUserAdapter
import com.codex.app.adapters.PostAdapter
import com.codex.app.databinding.ActivityFounderToolsBinding
import com.codex.app.models.Post
import com.codex.app.models.Role
import com.codex.app.models.User
import com.codex.app.utils.FirebaseHelper
import com.codex.app.utils.ModerationUiHelper
import com.codex.app.utils.WindowInsetsHelper
import kotlinx.coroutines.launch

/**
 * Founder control center for ericdanielevans@gmail.com.
 * Access is granted by founder email OR FOUNDER role in Firestore.
 */
class FounderToolsActivity : BaseThemedActivity() {

    private lateinit var binding: ActivityFounderToolsBinding
    private lateinit var userAdapter: ModerationUserAdapter
    private lateinit var postAdapter: PostAdapter
    private lateinit var commentAdapter: ModerationCommentAdapter

    private var allUsers = listOf<User>()
    private var allPosts = listOf<Post>()
    private var roleFilter: Role? = null
    private var showActiveOnly = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!FirebaseHelper.isFounderAccount()) {
            Toast.makeText(this, "This menu is exclusively for the app founder.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding = ActivityFounderToolsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.founder_tools)

        lifecycleScope.launch {
            FirebaseHelper.syncFounderRole()
            setupUi()
            loadEverything()
        }
    }

    private fun setupUi() {
        WindowInsetsHelper.applyTopSafeArea(binding.founderToolsRoot)
        binding.manageTopicsBtn.setOnClickListener {
            startActivity(Intent(this, ModerationManageActivity::class.java))
        }

        userAdapter = ModerationUserAdapter { user ->
            ModerationUiHelper.showQuickActions(
                context = this,
                scope = lifecycleScope,
                target = user,
                actorRole = Role.FOUNDER,
                includeFounder = true,
                onSuccess = { loadUsers() }
            )
        }
        binding.founderUsersRecycler.setupInsideScrollView()
        binding.founderUsersRecycler.adapter = userAdapter

        postAdapter = PostAdapter(
            onPostClick = { post ->
                startActivity(Intent(this, PostDetailActivity::class.java).putExtra("postId", post.id))
            },
            onLikeClick = { },
            onDeleteClick = { post ->
                lifecycleScope.launch {
                    val res = FirebaseHelper.deletePost(post.id, Role.FOUNDER)
                    if (res.isSuccess) {
                        Toast.makeText(this@FounderToolsActivity, "Post deleted", Toast.LENGTH_SHORT).show()
                        loadPosts()
                    } else {
                        Toast.makeText(this@FounderToolsActivity, res.exceptionOrNull()?.message ?: "Delete failed", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            currentUserRole = { Role.FOUNDER },
            onCategoryClick = null,
            onAuthorClick = null
        )
        binding.founderPostsRecycler.setupInsideScrollView()
        binding.founderPostsRecycler.adapter = postAdapter

        commentAdapter = ModerationCommentAdapter { comment ->
            lifecycleScope.launch {
                val res = FirebaseHelper.deleteComment(comment.id, comment.postId)
                if (res.isSuccess) {
                    Toast.makeText(this@FounderToolsActivity, getString(R.string.comment_deleted), Toast.LENGTH_SHORT).show()
                    loadComments()
                } else {
                    Toast.makeText(this@FounderToolsActivity, res.exceptionOrNull()?.message ?: "Delete failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
        binding.founderCommentsRecycler.setupInsideScrollView()
        binding.founderCommentsRecycler.adapter = commentAdapter

        binding.founderChipAll.isChecked = true
        binding.founderChipAllActive.isChecked = true

        binding.founderChipAll.setOnClickListener { applyRoleFilter(null) }
        binding.founderChipMembers.setOnClickListener { applyRoleFilter(Role.USER) }
        binding.founderChipMods.setOnClickListener { applyRoleFilter(Role.MOD) }
        binding.founderChipSuspended.setOnClickListener { applyRoleFilter(Role.SUSPENDED) }
        binding.founderChipBanned.setOnClickListener { applyRoleFilter(Role.BANNED) }
        binding.founderChipActive.setOnClickListener {
            showActiveOnly = true
            binding.founderChipActive.isChecked = true
            binding.founderChipAllActive.isChecked = false
            refreshUserList()
        }
        binding.founderChipAllActive.setOnClickListener {
            showActiveOnly = false
            binding.founderChipAllActive.isChecked = true
            binding.founderChipActive.isChecked = false
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
        binding.founderUserSearchInput.addTextChangedListener(watcher)
        binding.founderPostSearchInput.addTextChangedListener(watcher)

        binding.refreshUsersBtn.setOnClickListener { loadUsers() }
        binding.loadAllPostsBtn.setOnClickListener { loadPosts() }
        binding.refreshPostsBtn.setOnClickListener { loadPosts() }
        binding.refreshCommentsBtn.setOnClickListener { loadComments() }
        binding.refreshAllBtn.setOnClickListener { loadEverything() }
        binding.syncFounderRoleBtn.setOnClickListener {
            lifecycleScope.launch {
                val res = FirebaseHelper.syncFounderRole()
                if (res.isSuccess) {
                    Toast.makeText(this@FounderToolsActivity, getString(R.string.founder_role_synced), Toast.LENGTH_SHORT).show()
                    invalidateOptionsMenu()
                } else {
                    Toast.makeText(this@FounderToolsActivity, res.exceptionOrNull()?.message ?: "Sync failed", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun applyRoleFilter(role: Role?) {
        roleFilter = role
        binding.founderChipAll.isChecked = role == null
        binding.founderChipMembers.isChecked = role == Role.USER
        binding.founderChipMods.isChecked = role == Role.MOD
        binding.founderChipSuspended.isChecked = role == Role.SUSPENDED
        binding.founderChipBanned.isChecked = role == Role.BANNED
        refreshUserList()
    }

    private fun loadEverything() {
        loadUsers()
        loadPosts()
        loadComments()
    }

    private fun loadUsers() {
        lifecycleScope.launch {
            val result = FirebaseHelper.getUsersForModeration(500)
            allUsers = result.users
            if (result.error != null) {
                Toast.makeText(this@FounderToolsActivity, result.error, Toast.LENGTH_LONG).show()
            }
            updateDashboard()
            refreshUserList()
        }
    }

    private fun updateDashboard() {
        val stats = FirebaseHelper.computeModerationStats(allUsers)
        binding.founderStatsText.text = getString(
            R.string.founder_stats_format,
            stats.total, stats.activeRecently, stats.suspended, stats.banned, stats.mods, stats.admins
        )
        binding.founderStatMembers.text = "Members\n${stats.members}"
        binding.founderStatMods.text = "Mods\n${stats.mods}"
        binding.founderStatAdmins.text = "Admins\n${stats.admins + stats.founders}"
    }

    private fun refreshUserList() {
        val query = binding.founderUserSearchInput.text?.toString().orEmpty()
        val filtered = FirebaseHelper.filterUsers(allUsers, query, activeOnly = showActiveOnly, roleFilter = roleFilter)
        userAdapter.submitList(filtered)
        binding.founderUsersEmptyText.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.founderUserListStatus.text = getString(R.string.users_loaded, filtered.size)
    }

    private fun loadPosts() {
        lifecycleScope.launch {
            FirebaseHelper.getPosts(null, 200)
                .onSuccess { allPosts = it; refreshPostList() }
                .onFailure { err ->
                    allPosts = emptyList()
                    refreshPostList()
                    Toast.makeText(this@FounderToolsActivity, err.message ?: "Failed to load posts", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun refreshPostList() {
        val q = binding.founderPostSearchInput.text?.toString()?.trim()?.lowercase().orEmpty()
        val filtered = if (q.isEmpty()) allPosts else allPosts.filter { p ->
            p.title.lowercase().contains(q) || p.body.lowercase().contains(q) || p.authorName.lowercase().contains(q)
        }
        postAdapter.submitList(filtered)
        binding.founderEmptyText.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun loadComments() {
        lifecycleScope.launch {
            val comments = FirebaseHelper.getRecentComments(50)
            commentAdapter.submitList(comments)
            binding.founderCommentsEmptyText.visibility = if (comments.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}