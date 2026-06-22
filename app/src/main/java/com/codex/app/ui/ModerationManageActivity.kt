package com.codex.app.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.codex.app.R
import com.codex.app.adapters.ModerationCategoryAdapter
import com.codex.app.adapters.ModerationCategoryItem
import com.codex.app.adapters.ModerationRestrictedItem
import com.codex.app.adapters.ModerationRestrictedTopicAdapter
import com.codex.app.adapters.ModerationTopicAdapter
import com.codex.app.databinding.ActivityModerationManageBinding
import com.codex.app.models.BannedTopic
import com.codex.app.models.ChatRoom
import com.codex.app.utils.FirebaseHelper
import com.codex.app.utils.WindowInsetsHelper
import com.codex.app.utils.setupInsideScrollView
import kotlinx.coroutines.launch

class ModerationManageActivity : BaseThemedActivity() {

    private lateinit var binding: ActivityModerationManageBinding
    private lateinit var topicAdapter: ModerationTopicAdapter
    private lateinit var restrictedAdapter: ModerationRestrictedTopicAdapter
    private lateinit var categoryAdapter: ModerationCategoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!FirebaseHelper.resolveRole(FirebaseHelper.currentUser).canModerate()) {
            Toast.makeText(this, R.string.no_privileges, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding = ActivityModerationManageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowInsetsHelper.applyTopSafeArea(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        restrictedAdapter = ModerationRestrictedTopicAdapter(
            onUnban = { topic -> confirmUnban(topic) },
            onUnlock = { room ->
                lifecycleScope.launch {
                    val res = FirebaseHelper.lockTopicRoom(room.id, lock = false)
                    if (res.isSuccess) {
                        Toast.makeText(this@ModerationManageActivity, R.string.topic_unlocked, Toast.LENGTH_SHORT).show()
                        loadData()
                    } else {
                        showError(res.exceptionOrNull()?.message)
                    }
                }
            }
        )
        binding.restrictedTopicsRecycler.setupInsideScrollView()
        binding.restrictedTopicsRecycler.layoutManager = LinearLayoutManager(this)
        binding.restrictedTopicsRecycler.adapter = restrictedAdapter

        topicAdapter = ModerationTopicAdapter(
            onLock = { room ->
                lifecycleScope.launch {
                    val res = FirebaseHelper.lockTopicRoom(room.id, !room.locked)
                    if (res.isSuccess) {
                        Toast.makeText(this@ModerationManageActivity, R.string.action_success, Toast.LENGTH_SHORT).show()
                        loadData()
                    } else {
                        showError(res.exceptionOrNull()?.message)
                    }
                }
            },
            onReset = { room ->
                confirm(getString(R.string.reset_topic_confirm), getString(R.string.reset_topic)) {
                    lifecycleScope.launch {
                        val res = FirebaseHelper.resetTopicRoom(room.id)
                        if (res.isSuccess) {
                            Toast.makeText(this@ModerationManageActivity, R.string.topic_reset, Toast.LENGTH_SHORT).show()
                            loadData()
                        } else {
                            showError(res.exceptionOrNull()?.message)
                        }
                    }
                }
            },
            onBan = { room -> confirmBan(room) },
            onDelete = { room ->
                confirm(getString(R.string.delete_topic_full_confirm, room.title)) {
                    lifecycleScope.launch {
                        val res = FirebaseHelper.deleteTopicAndCategoryFully(
                            categoryName = room.title,
                            categoryId = room.topicId
                        )
                        if (res.isSuccess) {
                            Toast.makeText(this@ModerationManageActivity, R.string.topic_deleted, Toast.LENGTH_SHORT).show()
                            loadData()
                        } else {
                            showError(res.exceptionOrNull()?.message)
                        }
                    }
                }
            }
        )
        binding.topicRoomsRecycler.setupInsideScrollView()
        binding.topicRoomsRecycler.layoutManager = LinearLayoutManager(this)
        binding.topicRoomsRecycler.adapter = topicAdapter

        categoryAdapter = ModerationCategoryAdapter { item ->
            confirm(getString(R.string.delete_category_confirm, item.name)) {
                lifecycleScope.launch {
                    val res = if (item.isRegistered) {
                        FirebaseHelper.deleteCategory(item.id)
                    } else {
                        FirebaseHelper.deleteCategoryByName(item.name)
                    }
                    if (res.isSuccess) {
                        Toast.makeText(
                            this@ModerationManageActivity,
                            getString(R.string.category_deleted, item.name),
                            Toast.LENGTH_SHORT
                        ).show()
                        loadData()
                    } else {
                        showError(res.exceptionOrNull()?.message)
                    }
                }
            }
        }
        binding.categoriesRecycler.setupInsideScrollView()
        binding.categoriesRecycler.layoutManager = LinearLayoutManager(this)
        binding.categoriesRecycler.adapter = categoryAdapter

        loadData()
    }

    private fun loadData() {
        binding.loadingBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val banned = FirebaseHelper.getBannedTopics()
            val bannedNames = banned.map { it.name.lowercase() }.toSet()
            val allTopicRooms = FirebaseHelper.getChatRoomsForInbox().filter { it.isTopic() }

            val restricted = mutableListOf<ModerationRestrictedItem>()
            banned.forEach { topic ->
                restricted.add(ModerationRestrictedItem.Banned(topic))
            }
            allTopicRooms
                .filter { it.locked && !bannedNames.contains(it.title.lowercase()) }
                .forEach { room ->
                    restricted.add(ModerationRestrictedItem.Locked(room))
                }
            restrictedAdapter.submitList(restricted)
            binding.noRestrictedText.visibility = if (restricted.isEmpty()) View.VISIBLE else View.GONE

            val activeRooms = allTopicRooms.filter { !bannedNames.contains(it.title.lowercase()) }
            topicAdapter.submitList(activeRooms)
            binding.noTopicsText.visibility = if (activeRooms.isEmpty()) View.VISIBLE else View.GONE

            val registered = FirebaseHelper.getCategories()
            val feedNames = FirebaseHelper.getFeedCategoryNames()
                .filter { it != FirebaseHelper.ALL_CATEGORY_LABEL }
            val items = feedNames.map { name ->
                val match = registered.find { it.name.equals(name, ignoreCase = true) }
                if (match != null) {
                    ModerationCategoryItem(id = match.id, name = match.name, isRegistered = true)
                } else {
                    ModerationCategoryItem(
                        id = FirebaseHelper.resolveTopicCategoryId(name, registered),
                        name = name,
                        isRegistered = false
                    )
                }
            }.sortedBy { it.name.lowercase() }

            categoryAdapter.submitList(items)
            binding.noCategoriesText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            binding.loadingBar.visibility = View.GONE
        }
    }

    private fun confirmBan(room: ChatRoom) {
        confirm(
            message = getString(R.string.ban_topic_confirm, room.title),
            positiveLabel = getString(R.string.ban_topic)
        ) {
            lifecycleScope.launch {
                val res = FirebaseHelper.banTopic(room.title, room.topicId)
                if (res.isSuccess) {
                    Toast.makeText(this@ModerationManageActivity, R.string.topic_banned, Toast.LENGTH_SHORT).show()
                    loadData()
                } else {
                    showError(res.exceptionOrNull()?.message)
                }
            }
        }
    }

    private fun confirmUnban(topic: BannedTopic) {
        confirm(
            message = getString(R.string.unban_topic_confirm, topic.name),
            positiveLabel = getString(R.string.unban_topic)
        ) {
            lifecycleScope.launch {
                val res = FirebaseHelper.unbanTopic(topic.name)
                if (res.isSuccess) {
                    Toast.makeText(this@ModerationManageActivity, R.string.topic_unbanned, Toast.LENGTH_SHORT).show()
                    loadData()
                } else {
                    showError(res.exceptionOrNull()?.message)
                }
            }
        }
    }

    private fun showError(message: String?) {
        Toast.makeText(
            this,
            message ?: getString(R.string.error_generic),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun confirm(
        message: String,
        positiveLabel: String = getString(R.string.delete_topic),
        onConfirm: () -> Unit
    ) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton(positiveLabel) { _, _ -> onConfirm() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}