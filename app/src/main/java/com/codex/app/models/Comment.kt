package com.codex.app.models

import com.google.firebase.Timestamp

data class Comment(
    val id: String = "",
    val postId: String = "",
    val parentCommentId: String? = null,
    val replyToAuthorName: String? = null,
    val authorId: String = "",
    val authorName: String = "",
    val authorPhotoUrl: String? = null,
    val authorRole: String = Role.USER.name,
    val text: String = "",
    val imageUrl: String? = null,
    val mediaType: String? = null,
    val likeCount: Int = 0,
    val createdAt: Timestamp? = null
) {
    fun getRole(): Role = Role.fromString(authorRole)

    fun isReply(): Boolean = !parentCommentId.isNullOrBlank()
}