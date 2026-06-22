package com.codex.app.models

enum class Role {
    FOUNDER,  // Special founder role for ericdanielevans@gmail.com - has ALL permissions
    ADMIN,
    MOD,
    USER,
    SUSPENDED,
    BANNED;

    companion object {
        fun fromString(value: String?): Role {
            return when (value?.uppercase()) {
                "FOUNDER" -> FOUNDER
                "ADMIN" -> ADMIN
                "MOD", "MODERATOR" -> MOD
                "SUSPENDED" -> SUSPENDED
                "BANNED" -> BANNED
                else -> USER
            }
        }
    }

    fun displayName(): String = when (this) {
        FOUNDER -> "Founder"
        ADMIN -> "Admin"
        MOD -> "Moderator"
        USER -> "Member"
        SUSPENDED -> "Suspended"
        BANNED -> "Banned"
    }

    fun canPost(): Boolean = this == FOUNDER || this == ADMIN || this == MOD || this == USER
    fun canComment(): Boolean = canPost()
    fun canModerate(): Boolean = this == FOUNDER || this == ADMIN || this == MOD
    fun isActive(): Boolean = this != BANNED && this != SUSPENDED
    // Founder has literally all permissions (used for extra actions like self-promote, full delete, etc.)
    fun isFounder(): Boolean = this == FOUNDER
}