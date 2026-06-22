package com.codex.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.codex.app.R
import com.codex.app.adapters.ChatRoomAdapter
import com.codex.app.databinding.FragmentChatsBinding
import com.codex.app.models.ChatRoom
import com.codex.app.utils.FirebaseHelper
import com.codex.app.utils.WindowInsetsHelper
import com.google.android.material.tabs.TabLayout
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ChatsFragment : Fragment() {

    private var _binding: FragmentChatsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ChatRoomAdapter
    private var allRooms: List<ChatRoom> = emptyList()
    private var favoriteIds: Set<String> = emptySet()
    private var currentTab = FirebaseHelper.TAB_RECENT
    private var searchQuery = ""
    private var searchJob: Job? = null

    private var listenerHandle: FirebaseHelper.ChatRoomsListenerHandle? = null
    private var favoritesListener: ListenerRegistration? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        WindowInsetsHelper.applyBottomSafeArea(binding.chatsRoot)

        val canModerate = FirebaseHelper.resolveRole(FirebaseHelper.currentUser).canModerate()
        binding.modManageBtn.visibility = if (canModerate) View.VISIBLE else View.GONE
        binding.modManageBtn.setOnClickListener {
            startActivity(Intent(requireContext(), ModerationManageActivity::class.java))
        }
        binding.leaderboardBtn.setOnClickListener { openLeaderboard() }
        binding.browseTopicsBtn.setOnClickListener { openBrowseTopics() }
        binding.newMessageBtn.setOnClickListener { openNewMessage() }
        binding.emptyBrowseBtn.setOnClickListener { openBrowseTopics() }

        adapter = ChatRoomAdapter(
            onRoomClick = { room -> openChatRoom(room.id, room.title) },
            onFavoriteClick = { room -> toggleFavorite(room) },
            onRoomLongPress = { room -> showRoomLongPressMenu(room) }
        )
        binding.roomsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.roomsRecycler.adapter = adapter

        setupTabs()
        setupSearch()
    }

    override fun onStart() {
        super.onStart()
        binding.loadingBar.visibility = View.VISIBLE
        listenerHandle?.remove()
        listenerHandle = FirebaseHelper.listenChatRooms(
            onUpdate = { rooms ->
                if (!isAdded) return@listenChatRooms
                allRooms = rooms
                applyFilter()
                _binding?.loadingBar?.visibility = View.GONE
            },
            onError = { err ->
                if (!isAdded) return@listenChatRooms
                _binding?.loadingBar?.visibility = View.GONE
                Toast.makeText(
                    requireContext(),
                    err.message ?: getString(R.string.error_generic),
                    Toast.LENGTH_LONG
                ).show()
            }
        )
        favoritesListener?.remove()
        favoritesListener = FirebaseHelper.listenFavoriteRoomIds { ids ->
            if (!isAdded) return@listenFavoriteRoomIds
            favoriteIds = ids
            adapter.setFavoriteIds(ids)
            applyFilter()
        }
    }

    override fun onStop() {
        listenerHandle?.remove()
        listenerHandle = null
        favoritesListener?.remove()
        favoritesListener = null
        super.onStop()
    }

    private fun setupTabs() {
        binding.chatTabs.addTab(binding.chatTabs.newTab().setText(R.string.chat_recent))
        binding.chatTabs.addTab(binding.chatTabs.newTab().setText(R.string.chat_favorites))
        binding.chatTabs.addTab(binding.chatTabs.newTab().setText(R.string.chat_topics))
        binding.chatTabs.addTab(binding.chatTabs.newTab().setText(R.string.chat_dms))
        binding.chatTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = when (tab?.position) {
                    1 -> FirebaseHelper.TAB_FAVORITES
                    2 -> FirebaseHelper.TAB_TOPICS
                    3 -> FirebaseHelper.TAB_DMS
                    else -> FirebaseHelper.TAB_RECENT
                }
                applyFilter()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupSearch() {
        binding.searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                searchJob?.cancel()
                searchJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(300)
                    searchQuery = s?.toString().orEmpty()
                    applyFilter()
                }
            }
        })
    }

    private fun applyFilter() {
        val filtered = FirebaseHelper.filterRooms(allRooms, searchQuery, currentTab, favoriteIds)
        adapter.submitList(filtered)
        adapter.setFavoriteIds(favoriteIds)

        val isEmpty = filtered.isEmpty()
        _binding?.emptyContainer?.visibility = if (isEmpty) View.VISIBLE else View.GONE
        if (isEmpty) {
            _binding?.emptyText?.text = when (currentTab) {
                FirebaseHelper.TAB_FAVORITES -> getString(R.string.no_favorites)
                FirebaseHelper.TAB_TOPICS -> getString(R.string.no_chats) + "\n" + getString(R.string.browse_topics_hint)
                FirebaseHelper.TAB_DMS -> getString(R.string.no_chats)
                else -> if (searchQuery.isNotBlank()) getString(R.string.no_chats_match) else getString(R.string.no_chats)
            }
            _binding?.emptyBrowseBtn?.visibility =
                if (currentTab == FirebaseHelper.TAB_TOPICS || currentTab == FirebaseHelper.TAB_RECENT) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
        } else {
            _binding?.emptyContainer?.visibility = View.GONE
        }
    }

    private fun toggleFavorite(room: ChatRoom) {
        viewLifecycleOwner.lifecycleScope.launch {
            val res = FirebaseHelper.toggleFavorite(room.id)
            if (res.isSuccess) {
                val added = res.getOrThrow()
                Toast.makeText(
                    requireContext(),
                    if (added) R.string.favorite_added else R.string.favorite_removed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showRoomLongPressMenu(room: ChatRoom) {
        val isFavorite = favoriteIds.contains(room.id)
        val canModerate = FirebaseHelper.resolveRole(FirebaseHelper.currentUser).canModerate()
        val options = mutableListOf(
            if (isFavorite) getString(R.string.unpin_chat) else getString(R.string.pin_chat),
            getString(R.string.live_chat)
        )
        if (canModerate && room.isTopic()) {
            options.add(getString(R.string.delete_topic))
        }
        AlertDialog.Builder(requireContext())
            .setTitle(room.title)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    getString(R.string.unpin_chat), getString(R.string.pin_chat) -> toggleFavorite(room)
                    getString(R.string.live_chat) -> openChatRoom(room.id, room.title)
                    getString(R.string.delete_topic) -> confirmDeleteTopic(room)
                }
            }
            .show()
    }

    private fun confirmDeleteTopic(room: ChatRoom) {
        AlertDialog.Builder(requireContext())
            .setMessage(getString(R.string.delete_topic_full_confirm, room.title))
            .setPositiveButton(R.string.delete_topic) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val res = FirebaseHelper.deleteTopicAndCategoryFully(
                        categoryName = room.title,
                        categoryId = room.topicId
                    )
                    if (res.isSuccess) {
                        Toast.makeText(requireContext(), R.string.topic_deleted, Toast.LENGTH_SHORT).show()
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

    private fun openChatRoom(roomId: String, title: String) {
        if (roomId.isBlank()) {
            Toast.makeText(requireContext(), R.string.error_generic, Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            Intent(requireContext(), ChatRoomActivity::class.java)
                .putExtra(ChatRoomActivity.EXTRA_ROOM_ID, roomId)
                .putExtra(ChatRoomActivity.EXTRA_ROOM_TITLE, title)
        )
    }

    private fun openNewMessage() {
        startActivity(Intent(requireContext(), NewMessageActivity::class.java))
    }

    private fun openBrowseTopics() {
        startActivity(Intent(requireContext(), BrowseTopicsActivity::class.java))
    }

    private fun openLeaderboard() {
        startActivity(Intent(requireContext(), LeaderboardActivity::class.java))
    }

    override fun onDestroyView() {
        searchJob?.cancel()
        super.onDestroyView()
        _binding = null
    }
}