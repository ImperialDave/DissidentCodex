package com.codex.app.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class ChatMessage(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorPhotoUrl: String? = null,
    val authorRole: String = Role.USER.name,
    val text: String = "",
    val imageUrl: String? = null,
    val mediaType: String? = null,
    val createdAt: Timestamp? = null,
    val type: String = TYPE_TEXT
) {
    fun getRole(): Role = Role.fromString(authorRole)

    fun hasMedia(): Boolean = !imageUrl.isNullOrBlank()

    companion object {
        const val TYPE_TEXT = "text"
        const val TYPE_IMAGE = "image"
        const val TYPE_GIF = "gif"
        const val TYPE_VIDEO = "video"
    }
}