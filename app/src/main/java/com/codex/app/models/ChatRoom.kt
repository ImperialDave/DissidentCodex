package com.codex.app.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class ChatRoom(
    val id: String = "",
    val type: String = TYPE_TOPIC,
    val title: String = "",
    val topicId: String? = null,
    val topicName: String? = null,
    val memberIds: List<String> = emptyList(),
    val createdBy: String = "",
    val createdAt: Timestamp? = null,
    val lastMessageAt: Timestamp? = null,
    val lastMessagePreview: String = "",
    val lastMessageAuthorId: String = "",
    val messageCount: Long = 0,
    val locked: Boolean = false,
    val lockedBy: String? = null,
    val lockedAt: Timestamp? = null
) {
    fun isTopic(): Boolean = type == TYPE_TOPIC
    fun isDm(): Boolean = type == TYPE_DM

    companion object {
        const val TYPE_TOPIC = "topic"
        const val TYPE_DM = "dm"

        fun topicRoomId(categoryId: String) = "topic_$categoryId"

        fun dmRoomId(uidA: String, uidB: String): String {
            val sorted = listOf(uidA, uidB).sorted()
            return "dm_${sorted[0]}_${sorted[1]}"
        }
    }
}