package com.codex.app.models

import com.google.firebase.Timestamp

data class FavoriteChat(
    val roomId: String = "",
    val pinnedAt: Timestamp? = null
)