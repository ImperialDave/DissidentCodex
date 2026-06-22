package com.codex.app.models

import com.google.firebase.Timestamp

data class ChessGame(
    val id: String = "",
    val playerUids: List<String> = emptyList(),
    val whiteUid: String = "",
    val blackUid: String = "",
    val whiteName: String = "",
    val blackName: String = "",
    val challengerUid: String = "",
    val status: String = STATUS_PENDING,
    val fen: String = "",
    val turn: String = "w",
    val winnerUid: String? = null,
    val result: String? = null,
    val eloApplied: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun opponentUid(myUid: String): String =
        if (myUid == whiteUid) blackUid else whiteUid

    fun myColor(myUid: String): Boolean? = when (myUid) {
        whiteUid -> true
        blackUid -> false
        else -> null
    }

    fun isMyTurn(myUid: String): Boolean {
        val color = myColor(myUid) ?: return false
        return (turn == "w") == color
    }

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_ACTIVE = "active"
        const val STATUS_FINISHED = "finished"
        const val STATUS_DECLINED = "declined"

        const val RESULT_CHECKMATE = "checkmate"
        const val RESULT_STALEMATE = "stalemate"
        const val RESULT_RESIGN = "resign"
        const val RESULT_DRAW = "draw"
    }
}