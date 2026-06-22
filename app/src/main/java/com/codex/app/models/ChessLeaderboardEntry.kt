package com.codex.app.models

data class ChessLeaderboardEntry(
    val rank: Int = 0,
    val uid: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val elo: Int = 1200,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0
)