package com.codex.app.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class FeedHiddenTopic(
    val id: String = "",
    val name: String = "",
    val hiddenBy: String? = null,
    val hiddenAt: Timestamp? = null
)