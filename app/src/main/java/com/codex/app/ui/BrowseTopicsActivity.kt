package com.codex.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.codex.app.R
import com.codex.app.adapters.SearchTopicAdapter
import com.codex.app.databinding.ActivityBrowseTopicsBinding
import com.codex.app.models.PostCategory
import com.codex.app.utils.FirebaseHelper
import kotlinx.coroutines.launch

class BrowseTopicsActivity : BaseThemedActivity() {

    private lateinit var binding: ActivityBrowseTopicsBinding
    private lateinit var adapter: SearchTopicAdapter
    private var canModerate = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowseTopicsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        canModerate = FirebaseHelper.resolveRole(FirebaseHelper.currentUser).canModerate()
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = SearchTopicAdapter(
            onTopicClick = { topic -> joinTopic(topic.name, topic.id) },
            onModMenu = if (canModerate) { topic -> showTopicModMenu(topic) } else null
        )
        adapter.setShowModActions(canModerate)
        binding.topicsRecycler.layoutManager = LinearLayoutManager(this)
        binding.topicsRecycler.adapter = adapter

        loadTopics()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (canModerate) {
            menuInflater.inflate(R.menu.browse_topics_menu, menu)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_manage_topics -> {
                startActivity(Intent(this, ModerationManageActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadTopics() {
        binding.loadingBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val names = FirebaseHelper.getFeedCategoryNames()
                .filter { it != FirebaseHelper.ALL_CATEGORY_LABEL }
            val categories = FirebaseHelper.getCategories()
            val topics = names.map { name ->
                categories.find { it.name.equals(name, ignoreCase = true) }
                    ?: PostCategory(
                        id = FirebaseHelper.resolveTopicCategoryId(name, categories),
                        name = name
                    )
            }.sortedBy { it.name.lowercase() }

            binding.loadingBar.visibility = View.GONE
            adapter.submitList(topics)
            binding.emptyText.visibility = if (topics.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun joinTopic(categoryName: String, categoryId: String) {
        lifecycleScope.launch {
            val res = FirebaseHelper.getOrCreateTopicRoom(categoryId, categoryName)
            if (res.isSuccess) {
                val room = res.getOrThrow()
                startActivity(
                    Intent(this@BrowseTopicsActivity, ChatRoomActivity::class.java)
                        .putExtra(ChatRoomActivity.EXTRA_ROOM_ID, room.id)
                        .putExtra(ChatRoomActivity.EXTRA_ROOM_TITLE, room.title)
                )
                finish()
            } else {
                Toast.makeText(
                    this@BrowseTopicsActivity,
                    res.exceptionOrNull()?.message ?: getString(R.string.error_generic),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showTopicModMenu(topic: PostCategory) {
        val options = arrayOf(
            getString(R.string.delete_topic),
            getString(R.string.delete_category)
        )
        AlertDialog.Builder(this)
            .setTitle(topic.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> confirmDeleteTopic(topic)
                    1 -> confirmDeleteCategory(topic)
                }
            }
            .show()
    }

    private fun confirmDeleteTopic(topic: PostCategory) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.delete_topic_full_confirm, topic.name))
            .setPositiveButton(R.string.delete_topic) { _, _ ->
                lifecycleScope.launch {
                    val res = FirebaseHelper.deleteTopicAndCategoryFully(topic.name, topic.id)
                    if (res.isSuccess) {
                        Toast.makeText(this@BrowseTopicsActivity, R.string.topic_deleted, Toast.LENGTH_SHORT).show()
                        loadTopics()
                    } else {
                        Toast.makeText(
                            this@BrowseTopicsActivity,
                            res.exceptionOrNull()?.message ?: getString(R.string.error_generic),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteCategory(topic: PostCategory) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.delete_category_confirm, topic.name))
            .setPositiveButton(R.string.delete_category) { _, _ ->
                lifecycleScope.launch {
                    val res = FirebaseHelper.deleteTopicAndCategoryFully(topic.name, topic.id)
                    if (res.isSuccess) {
                        Toast.makeText(
                            this@BrowseTopicsActivity,
                            getString(R.string.category_deleted, topic.name),
                            Toast.LENGTH_SHORT
                        ).show()
                        loadTopics()
                    } else {
                        Toast.makeText(
                            this@BrowseTopicsActivity,
                            res.exceptionOrNull()?.message ?: getString(R.string.error_generic),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}