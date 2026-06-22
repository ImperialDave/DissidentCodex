package com.codex.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.codex.app.R
import com.codex.app.adapters.ChessLeaderboardAdapter
import com.codex.app.adapters.LeaderboardAdapter
import com.codex.app.databinding.ActivityLeaderboardBinding
import com.codex.app.models.ChessLeaderboardEntry
import com.codex.app.models.LeaderboardEntry
import com.codex.app.utils.FirebaseHelper
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class LeaderboardActivity : BaseThemedActivity() {

    private lateinit var binding: ActivityLeaderboardBinding
    private lateinit var adapter: LeaderboardAdapter
    private lateinit var chessAdapter: ChessLeaderboardAdapter
    private var topTopics: List<LeaderboardEntry> = emptyList()
    private var topChats: List<LeaderboardEntry> = emptyList()
    private var topChess: List<ChessLeaderboardEntry> = emptyList()
    private var currentTab = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLeaderboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = LeaderboardAdapter(onEntryClick = { entry -> openRoom(entry) })
        chessAdapter = ChessLeaderboardAdapter { entry ->
            startActivity(UserProfileActivity.intent(this, entry.uid))
        }
        binding.leaderboardRecycler.layoutManager = LinearLayoutManager(this)
        binding.leaderboardRecycler.adapter = adapter

        binding.leaderboardTabs.addTab(binding.leaderboardTabs.newTab().setText(R.string.top_topics))
        binding.leaderboardTabs.addTab(binding.leaderboardTabs.newTab().setText(R.string.top_chats))
        binding.leaderboardTabs.addTab(binding.leaderboardTabs.newTab().setText(R.string.top_chess))
        binding.leaderboardTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                renderTab()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        loadLeaderboard()
    }

    private fun loadLeaderboard() {
        binding.loadingBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val data = FirebaseHelper.getLeaderboardData(limit = 50)
            topTopics = data.topTopics
            topChats = data.topChats
            topChess = FirebaseHelper.getChessLeaderboard(limit = 50)
            binding.loadingBar.visibility = View.GONE
            renderTab()
        }
    }

    private fun renderTab() {
        when (currentTab) {
            0 -> {
                binding.leaderboardRecycler.adapter = adapter
                adapter.submitLists(
                    sectionTopicsTitle = getString(R.string.top_topics),
                    topTopics = topTopics,
                    sectionChatsTitle = null,
                    topChats = emptyList()
                )
                binding.emptyText.text = getString(R.string.leaderboard_empty)
                binding.emptyText.visibility = if (topTopics.isEmpty()) View.VISIBLE else View.GONE
            }
            1 -> {
                binding.leaderboardRecycler.adapter = adapter
                adapter.submitLists(
                    sectionTopicsTitle = null,
                    topTopics = emptyList(),
                    sectionChatsTitle = getString(R.string.top_chats),
                    topChats = topChats
                )
                binding.emptyText.text = getString(R.string.leaderboard_empty)
                binding.emptyText.visibility = if (topChats.isEmpty()) View.VISIBLE else View.GONE
            }
            else -> {
                binding.leaderboardRecycler.adapter = chessAdapter
                chessAdapter.submitList(topChess)
                binding.emptyText.text = getString(R.string.chess_leaderboard_empty)
                binding.emptyText.visibility = if (topChess.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun openRoom(entry: LeaderboardEntry) {
        startActivity(
            Intent(this, ChatRoomActivity::class.java)
                .putExtra(ChatRoomActivity.EXTRA_ROOM_ID, entry.roomId)
                .putExtra(ChatRoomActivity.EXTRA_ROOM_TITLE, entry.title)
        )
    }
}