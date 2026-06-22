package com.codex.app.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class User(
    val uid: String = "",
    val email: String = "",
    @get:PropertyName("displayName") @set:PropertyName("displayName")
    var displayName: String = "",
    @get:PropertyName("photoUrl") @set:PropertyName("photoUrl")
    var photoUrl: String? = null,
    @get:PropertyName("bio") @set:PropertyName("bio")
    var bio: String = "",
    @get:PropertyName("backgroundUrl") @set:PropertyName("backgroundUrl")
    var backgroundUrl: String? = null,
    @get:PropertyName("flair") @set:PropertyName("flair")
    var flair: String? = null,
    val friendCount: Long = 0,
    val chessElo: Int = 1200,
    val chessGamesPlayed: Int = 0,
    val chessWins: Int = 0,
    val chessLosses: Int = 0,
    val chessDraws: Int = 0,
    val role: String = Role.USER.name,
    val createdAt: Timestamp? = null,
    val lastActive: Timestamp? = null
) {
    fun getRoleEnum(): Role = Role.fromString(role)

    fun canPost() = getRoleEnum().canPost()
    fun canComment() = getRoleEnum().canComment()
    fun canModerate() = getRoleEnum().canModerate()
    fun isBanned() = getRoleEnum() == Role.BANNED
    fun isSuspended() = getRoleEnum() == Role.SUSPENDED
}