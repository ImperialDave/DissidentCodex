package com.codex.app.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class BannedTopic(
    val id: String = "",
    val name: String = "",
    val categoryId: String = "",
    val hiddenBy: String = "",
    val hiddenAt: Timestamp? = null
)