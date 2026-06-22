package com.codex.app.models

import com.google.firebase.Timestamp

data class FavoriteCategory(
    val categoryId: String = "",
    val name: String = "",
    val pinnedAt: Timestamp? = null
)