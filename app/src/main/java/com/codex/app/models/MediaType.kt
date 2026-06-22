package com.codex.app.models

object MediaType {
    const val IMAGE = "image"
    const val GIF = "gif"
    const val VIDEO = "video"

    fun fromUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val lower = url.lowercase()
        return when {
            lower.contains(".mp4") || lower.contains(".webm") || lower.contains(".mov") ||
                lower.contains("video") -> VIDEO
            lower.contains(".gif") || lower.contains("giphy.com") || lower.contains("tenor.com") -> GIF
            else -> IMAGE
        }
    }

    fun resolve(mediaType: String?, url: String?): String? {
        return mediaType?.takeIf { it.isNotBlank() } ?: fromUrl(url)
    }

    fun isGif(mediaType: String?, url: String?): Boolean {
        return resolve(mediaType, url) == GIF
    }

    fun isVideo(mediaType: String?, url: String?): Boolean {
        return resolve(mediaType, url) == VIDEO
    }

    fun messageType(mediaType: String?, url: String?): String {
        return when (resolve(mediaType, url)) {
            GIF -> ChatMessage.TYPE_GIF
            VIDEO -> ChatMessage.TYPE_VIDEO
            IMAGE -> ChatMessage.TYPE_IMAGE
            else -> ChatMessage.TYPE_TEXT
        }
    }
}