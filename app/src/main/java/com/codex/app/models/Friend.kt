package com.codex.app.models

import com.google.firebase.Timestamp

data class Friend(
    val uid: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val since: Timestamp? = null
)