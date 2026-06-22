package com.codex.app.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Post(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorPhotoUrl: String? = null,
    val authorRole: String = Role.USER.name,
    val title: String = "",
    val body: String = "",
    val imageUrl: String? = null,
    val mediaType: String? = null,
    val category: String = "General Discussion",
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val hiddenFromFeed: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun getRole(): Role = Role.fromString(authorRole)
}