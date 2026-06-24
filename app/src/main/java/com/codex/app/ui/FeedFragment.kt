package com.codex.app.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.codex.app.MainActivity
import com.codex.app.R
import com.codex.app.adapters.PostAdapter
import com.codex.app.databinding.FragmentFeedBinding
import com.codex.app.models.Post
import com.codex.app.utils.FirebaseHelper
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch

class FeedFragment : Fragment() {

    private var _binding: FragmentFeedBinding? = null
    private val binding get() = _binding!!

    private lateinit var postAdapter: PostAdapter
    private var currentCategory: String = FirebaseHelper.ALL_CATEGORY_LABEL
    private var categoryNames: List<String> = listOf(FirebaseHelper.ALL_CATEGORY_LABEL)
    private var allPosts: List<Post> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecycler()
        setupSearch()
        binding.swipeRefresh.setOnRefreshListener { refreshFeed() }
        refreshFeed()
        binding.fabCreate.setOnClickListener {
            (activity as? MainActivity)?.findViewById<BottomNavigationView>(R.id.bottomNavigation)?.selectedItemId = R.id.nav_create
        }
        binding.joinTopicChatBtn.setOnClickListener {
            if (currentCategory != FirebaseHelper.ALL_CATEGORY_LABEL) {
                joinTopicChat(currentCategory)
            }
        }
    }

    private fun refreshFeed() {
        viewLifecycleOwner.lifecycleScope.launch {
            categoryNames = FirebaseHelper.getFeedCategoryNames()
            if (!isAdded) return@launch
            if (!categoryNames.contains(currentCategory)) {
                currentCategory = FirebaseHelper.ALL_CATEGORY_LABEL
            }
            setupChips()
            loadPosts()
        }
    }

    private fun setupChips() {
        val group = _binding?.categoryChips ?: return
        group.removeAllViews()
        categoryNames.forEach { cat ->
            val chip = Chip(requireContext()).apply {
                text = cat
                isCheckable = true
                isChecked = cat == currentCategory
                setOnClickListener {
                    currentCategory = cat
                    for (i in 0 until group.childCount) {
                        (group.getChildAt(i) as? Chip)?.isChecked = group.getChildAt(i) == this
                    }
                    updateJoinTopicButton()
                    loadPosts()
                }
                if (cat != FirebaseHelper.ALL_CATEGORY_LABEL) {
                    chipIcon = context.getDrawable(R.drawable.ic_chat)
                    isChipIconVisible = true
                    setOnLongClickListener {
                        showCategoryLongPressMenu(cat)
                        true
                    }
                }
            }
            group.addView(chip)
        }
        updateJoinTopicButton()
    }

    private fun updateJoinTopicButton() {
        val show = currentCategory != FirebaseHelper.ALL_CATEGORY_LABEL
        _binding?.joinTopicChatBtn?.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            _binding?.joinTopicChatBtn?.text = getString(R.string.join_live_chat) + " · $currentCategory"
        }
    }

    private fun setupRecycler() {
        postAdapter = PostAdapter(
            onPostClick = { post ->
                startActivity(Intent(requireContext(), PostDetailActivity::class.java).putExtra("postId", post.id))
            },
            onLikeClick = { post ->
                lifecycleScope.launch {
                    val res = FirebaseHelper.toggleLikePost(post.id)
                    if (res.isSuccess) {
                        postAdapter.notifyLikesChanged()
                        loadPosts()
                    } else {
                        Toast.makeText(
                            requireContext(),
                            res.exceptionOrNull()?.message ?: "Like failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            likedPostIds = { FirebaseHelper.getCachedLikedPostIds() },
            onDeleteClick = { post ->
                val role = FirebaseHelper.resolveRole(FirebaseHelper.currentUser)
                lifecycleScope.launch {
                    val res = FirebaseHelper.deletePost(post.id, role)
                    if (res.isSuccess) {
                        (activity as? MainActivity)?.showToast("Post deleted")
                        loadPosts()
                    } else {
                        Toast.makeText(requireContext(), res.exceptionOrNull()?.message ?: "Delete failed", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            currentUserRole = { FirebaseHelper.resolveRole(FirebaseHelper.currentUser) },
            onCategoryClick = { cat ->
                currentCategory = cat
                setupChips()
                loadPosts()
            },
            onAuthorClick = { post ->
                startActivity(UserProfileActivity.intent(requireContext(), post.authorId))
            },
            onHideClick = { post ->
                lifecycleScope.launch {
                    val res = FirebaseHelper.togglePostFeedVisibility(post.id)
                    if (res.isSuccess) {
                        loadPosts()
                    } else {
                        Toast.makeText(
                            requireContext(),
                            res.exceptionOrNull()?.message ?: getString(R.string.hide_post_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
        binding.postsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.postsRecycler.adapter = postAdapter
    }

    private fun setupSearch() {
        binding.feedSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                applyFeedFilter()
            }
        })
    }

    private fun applyFeedFilter() {
        val query = binding.feedSearchInput.text?.toString()?.trim()?.lowercase().orEmpty()
        val filtered = if (query.isEmpty()) {
            allPosts
        } else {
            allPosts.filter { post ->
                post.title.lowercase().contains(query) ||
                    post.body.lowercase().contains(query) ||
                    post.authorName.lowercase().contains(query)
            }
        }
        postAdapter.submitList(filtered)
        binding.emptyText.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun loadPosts() {
        val b = _binding ?: return
        b.swipeRefresh.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            FirebaseHelper.refreshLikedPostCache()
            val includeHidden = FirebaseHelper.resolveRole(FirebaseHelper.currentUser).canModerate()
            val result = FirebaseHelper.getPosts(
                category = if (currentCategory == FirebaseHelper.ALL_CATEGORY_LABEL) null else currentCategory,
                includeHidden = includeHidden
            )
            if (!isAdded) return@launch
            val feedBinding = _binding ?: return@launch
            result.onSuccess { posts ->
                allPosts = posts
                applyFeedFilter()
                if (allPosts.isEmpty()) {
                    feedBinding.emptyText.text = getString(R.string.no_posts) + "\nTap here to create the first post!"
                    feedBinding.emptyText.setOnClickListener {
                        (activity as? MainActivity)?.findViewById<BottomNavigationView>(R.id.bottomNavigation)?.selectedItemId = R.id.nav_create
                    }
                }
            }.onFailure { err ->
                allPosts = emptyList()
                applyFeedFilter()
                feedBinding.emptyText.text = err.message ?: getString(R.string.no_posts)
                Toast.makeText(requireContext(), err.message ?: "Failed to load feed", Toast.LENGTH_LONG).show()
            }
            _binding?.swipeRefresh?.isRefreshing = false
        }
    }

    private fun showCategoryLongPressMenu(categoryName: String) {
        val canModerate = FirebaseHelper.resolveRole(FirebaseHelper.currentUser).canModerate()
        val options = mutableListOf(getString(R.string.join_live_chat))
        if (canModerate) {
            options.add(getString(R.string.manage_topics_categories))
            options.add(getString(R.string.delete_category))
        }
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(categoryName)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    getString(R.string.join_live_chat) -> joinTopicChat(categoryName)
                    getString(R.string.manage_topics_categories) -> {
                        startActivity(android.content.Intent(requireContext(), ModerationManageActivity::class.java))
                    }
                    getString(R.string.delete_category) -> confirmDeleteCategory(categoryName)
                }
            }
            .show()
    }

    private fun confirmDeleteCategory(categoryName: String) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setMessage(getString(R.string.delete_category_confirm, categoryName))
            .setPositiveButton(R.string.delete_category) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val res = FirebaseHelper.deleteCategoryByName(categoryName)
                    if (res.isSuccess) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.category_deleted, categoryName),
                            Toast.LENGTH_SHORT
                        ).show()
                        refreshFeed()
                    } else {
                        Toast.makeText(
                            requireContext(),
                            res.exceptionOrNull()?.message ?: getString(R.string.error_generic),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun joinTopicChat(categoryName: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val res = FirebaseHelper.getOrCreateTopicRoomByName(categoryName)
            if (res.isSuccess) {
                val room = res.getOrThrow()
                startActivity(
                    Intent(requireContext(), ChatRoomActivity::class.java)
                        .putExtra(ChatRoomActivity.EXTRA_ROOM_ID, room.id)
                        .putExtra(ChatRoomActivity.EXTRA_ROOM_TITLE, room.title)
                )
            } else {
                Toast.makeText(
                    requireContext(),
                    res.exceptionOrNull()?.message ?: getString(R.string.error_generic),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}