package com.codex.app.models

import com.google.firebase.Timestamp

data class FriendRequest(
    val id: String = "",
    val fromUid: String = "",
    val toUid: String = "",
    val status: String = STATUS_PENDING,
    val createdAt: Timestamp? = null
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_ACCEPTED = "accepted"
        const val STATUS_DECLINED = "declined"
    }
}