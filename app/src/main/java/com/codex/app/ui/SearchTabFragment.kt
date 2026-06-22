package com.codex.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.codex.app.R
import com.codex.app.adapters.ChatRoomAdapter
import com.codex.app.adapters.PostAdapter
import com.codex.app.adapters.SearchTopicAdapter
import com.codex.app.databinding.FragmentSearchResultsBinding
import com.codex.app.utils.FirebaseHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchTabFragment : Fragment() {

    enum class TabType { POSTS, TOPICS, CHATS }

    private var _binding: FragmentSearchResultsBinding? = null
    private val binding get() = _binding!!

    private var tabType = TabType.POSTS
    private var currentQuery = ""
    private var searchJob: Job? = null

    private var postAdapter: PostAdapter? = null
    private var topicAdapter: SearchTopicAdapter? = null
    private var chatAdapter: ChatRoomAdapter? = null

    var onQueryChanged: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tabType = TabType.entries[arguments?.getInt(ARG_TAB_TYPE) ?: 0]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchResultsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.resultsRecycler.layoutManager = LinearLayoutManager(requireContext())
        setupAdapter()
        if (currentQuery.isNotBlank()) runSearch(currentQuery)
    }

    private fun setupAdapter() {
        when (tabType) {
            TabType.POSTS -> {
                postAdapter = PostAdapter(
                    onPostClick = { post ->
                        startActivity(
                            Intent(requireContext(), PostDetailActivity::class.java)
                                .putExtra("postId", post.id)
                        )
                    },
                    onLikeClick = { },
                    onDeleteClick = { },
                    currentUserRole = { FirebaseHelper.resolveRole(FirebaseHelper.currentUser) }
                )
                binding.resultsRecycler.adapter = postAdapter
                binding.emptyText.text = getString(R.string.no_posts_match)
            }
            TabType.TOPICS -> {
                topicAdapter = SearchTopicAdapter(onTopicClick = { topic ->
                    lifecycleScope.launch {
                        val res = FirebaseHelper.getOrCreateTopicRoom(topic.id, topic.name)
                        if (res.isSuccess) {
                            val room = res.getOrThrow()
                            startActivity(
                                Intent(requireContext(), ChatRoomActivity::class.java)
                                    .putExtra(ChatRoomActivity.EXTRA_ROOM_ID, room.id)
                                    .putExtra(ChatRoomActivity.EXTRA_ROOM_TITLE, room.title)
                            )
                        } else {
                            android.widget.Toast.makeText(
                                requireContext(),
                                res.exceptionOrNull()?.message ?: getString(R.string.error_generic),
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                })
                binding.resultsRecycler.adapter = topicAdapter
                binding.emptyText.text = getString(R.string.no_topics_match)
            }
            TabType.CHATS -> {
                chatAdapter = ChatRoomAdapter(onRoomClick = { room ->
                    startActivity(
                        Intent(requireContext(), ChatRoomActivity::class.java)
                            .putExtra(ChatRoomActivity.EXTRA_ROOM_ID, room.id)
                            .putExtra(ChatRoomActivity.EXTRA_ROOM_TITLE, room.title)
                    )
                })
                binding.resultsRecycler.adapter = chatAdapter
                binding.emptyText.text = getString(R.string.no_chats_match)
            }
        }
    }

    fun updateQuery(query: String) {
        currentQuery = query
        searchJob?.cancel()
        if (query.isBlank()) {
            clearResults()
            binding.emptyText.visibility = View.GONE
            return
        }
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(300)
            if (!isAdded) return@launch
            runSearch(query)
        }
    }

    private fun runSearch(query: String) {
        binding.loadingBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            when (tabType) {
                TabType.POSTS -> {
                    val posts = FirebaseHelper.searchPosts(query)
                    if (!isAdded) return@launch
                    postAdapter?.submitList(posts)
                    showEmpty(posts.isEmpty())
                }
                TabType.TOPICS -> {
                    val topics = FirebaseHelper.searchTopics(query)
                    if (!isAdded) return@launch
                    topicAdapter?.submitList(topics)
                    showEmpty(topics.isEmpty())
                }
                TabType.CHATS -> {
                    val rooms = FirebaseHelper.searchChatRooms(query)
                    if (!isAdded) return@launch
                    chatAdapter?.submitList(rooms)
                    showEmpty(rooms.isEmpty())
                }
            }
            _binding?.loadingBar?.visibility = View.GONE
        }
    }

    private fun clearResults() {
        when (tabType) {
            TabType.POSTS -> postAdapter?.submitList(emptyList())
            TabType.TOPICS -> topicAdapter?.submitList(emptyList())
            TabType.CHATS -> chatAdapter?.submitList(emptyList())
        }
    }

    private fun showEmpty(empty: Boolean) {
        _binding?.emptyText?.visibility = if (empty && currentQuery.isNotBlank()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        searchJob?.cancel()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TAB_TYPE = "tabType"

        fun newInstance(type: TabType): SearchTabFragment {
            return SearchTabFragment().apply {
                arguments = Bundle().apply { putInt(ARG_TAB_TYPE, type.ordinal) }
            }
        }
    }
}