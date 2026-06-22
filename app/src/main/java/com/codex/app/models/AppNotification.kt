package com.codex.app.models

import com.google.firebase.Timestamp

data class AppNotification(
    val id: String = "",
    val type: String = "",
    val title: String = "",
    val body: String = "",
    val actorUid: String? = null,
    val actorName: String? = null,
    val targetId: String? = null,
    val read: Boolean = false,
    val createdAt: Timestamp? = null
) {
    companion object {
        const val TYPE_CHESS_TURN = "CHESS_TURN"
        const val TYPE_POST_LIKE = "POST_LIKE"
        const val TYPE_POST_COMMENT = "POST_COMMENT"
        const val TYPE_COMMENT_REPLY = "COMMENT_REPLY"
    }
}