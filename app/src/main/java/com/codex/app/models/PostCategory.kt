package com.codex.app.models

import com.google.firebase.Timestamp

data class PostCategory(
    val id: String = "",
    val name: String = "",
    val createdBy: String = "",
    val createdAt: Timestamp? = null
)