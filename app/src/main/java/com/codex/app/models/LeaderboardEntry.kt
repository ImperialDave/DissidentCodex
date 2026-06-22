package com.codex.app.models

import com.google.firebase.Timestamp

data class LeaderboardEntry(
    val rank: Int = 0,
    val title: String = "",
    val roomId: String = "",
    val messageCount: Long = 0,
    val postCount: Long = 0,
    val score: Long = 0,
    val lastMessageAt: Timestamp? = null,
    val isTopic: Boolean = true
)

data class LeaderboardData(
    val topTopics: List<LeaderboardEntry> = emptyList(),
    val topChats: List<LeaderboardEntry> = emptyList()
)