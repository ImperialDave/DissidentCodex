package com.codex.app.utils

import android.content.Context
import android.net.Uri
import android.view.View
import androidx.fragment.app.FragmentActivity
import android.webkit.MimeTypeMap
import android.widget.ImageView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.codex.app.R
import com.codex.app.models.MediaType
import com.codex.app.ui.MediaLightboxDialog
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import java.util.UUID

object MediaUploadHelper {

    private const val MAX_IMAGE_BYTES = 10L * 1024 * 1024
    private const val MAX_VIDEO_BYTES = 50L * 1024 * 1024

    data class UploadResult(val url: String, val mediaType: String)

    fun detectMimeType(context: Context, uri: Uri): String {
        val fromResolver = context.contentResolver.getType(uri)
        if (!fromResolver.isNullOrBlank()) return fromResolver
        val ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "image/jpeg"
    }

    fun mediaTypeFromMime(mime: String): String {
        return when {
            mime.equals("image/gif", ignoreCase = true) -> MediaType.GIF
            mime.startsWith("video/", ignoreCase = true) -> MediaType.VIDEO
            else -> MediaType.IMAGE
        }
    }

    fun extensionForMime(mime: String): String {
        return when {
            mime.equals("image/gif", ignoreCase = true) -> "gif"
            mime.equals("image/png", ignoreCase = true) -> "png"
            mime.equals("image/webp", ignoreCase = true) -> "webp"
            mime.equals("video/webm", ignoreCase = true) -> "webm"
            mime.equals("video/quicktime", ignoreCase = true) -> "mov"
            mime.startsWith("video/", ignoreCase = true) -> "mp4"
            else -> "jpg"
        }
    }

    private fun fileSizeBytes(context: Context, uri: Uri): Long? {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun uploadMedia(
        context: Context,
        storage: FirebaseStorage,
        uri: Uri,
        folder: String,
        uid: String
    ): Result<UploadResult> {
        return try {
            val mime = detectMimeType(context, uri)
            if (!mime.startsWith("image/", ignoreCase = true)) {
                return Result.failure(Exception("Only image uploads are allowed."))
            }
            val size = fileSizeBytes(context, uri)
            if (size != null && size > MAX_IMAGE_BYTES) {
                return Result.failure(Exception("Image is too large (max 10 MB)."))
            }
            val mediaType = mediaTypeFromMime(mime)
            val ext = extensionForMime(mime)
            val ref = storage.reference.child("$folder/$uid/${UUID.randomUUID()}.$ext")
            val metadata = StorageMetadata.Builder().setContentType(mime).build()
            ref.putFile(uri, metadata).await()
            val url = ref.downloadUrl.await().toString()
            Result.success(UploadResult(url, mediaType))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadCommentVideo(
        context: Context,
        storage: FirebaseStorage,
        uri: Uri,
        uid: String
    ): Result<UploadResult> {
        return try {
            val mime = detectMimeType(context, uri)
            if (!mime.startsWith("video/", ignoreCase = true)) {
                return Result.failure(Exception("Only video uploads are allowed."))
            }
            val size = fileSizeBytes(context, uri)
            if (size != null && size > MAX_VIDEO_BYTES) {
                return Result.failure(Exception("Video is too large (max 50 MB)."))
            }
            val ext = extensionForMime(mime)
            val ref = storage.reference.child("comment_media/$uid/${UUID.randomUUID()}.$ext")
            val metadata = StorageMetadata.Builder().setContentType(mime).build()
            ref.putFile(uri, metadata).await()
            val url = ref.downloadUrl.await().toString()
            Result.success(UploadResult(url, MediaType.VIDEO))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadChatVideo(
        context: Context,
        storage: FirebaseStorage,
        uri: Uri,
        uid: String
    ): Result<UploadResult> {
        return try {
            val mime = detectMimeType(context, uri)
            if (!mime.startsWith("video/", ignoreCase = true)) {
                return Result.failure(Exception("Only video uploads are allowed."))
            }
            val size = fileSizeBytes(context, uri)
            if (size != null && size > MAX_VIDEO_BYTES) {
                return Result.failure(Exception("Video is too large (max 50 MB)."))
            }
            val ext = extensionForMime(mime)
            val ref = storage.reference.child("chat_media/$uid/${UUID.randomUUID()}.$ext")
            val metadata = StorageMetadata.Builder().setContentType(mime).build()
            ref.putFile(uri, metadata).await()
            val url = ref.downloadUrl.await().toString()
            Result.success(UploadResult(url, MediaType.VIDEO))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun loadMediaInto(imageView: ImageView, url: String?, mediaType: String?) {
        if (url.isNullOrBlank()) {
            imageView.visibility = ImageView.GONE
            return
        }
        if (MediaType.isVideo(mediaType, url)) {
            imageView.visibility = ImageView.GONE
            return
        }
        imageView.visibility = ImageView.VISIBLE
        if (MediaType.isGif(mediaType, url)) {
            Glide.with(imageView).asGif().load(url).fitCenter()
                .placeholder(R.drawable.default_avatar)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(imageView)
        } else {
            Glide.with(imageView).load(url).centerCrop()
                .placeholder(R.drawable.default_avatar)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(imageView)
        }
    }

    fun bindVideoPlayer(playerView: PlayerView, url: String?, mediaType: String?) {
        if (url.isNullOrBlank() || !MediaType.isVideo(mediaType, url)) {
            playerView.visibility = View.GONE
            releasePlayer(playerView)
            return
        }
        playerView.visibility = View.VISIBLE
        val context = playerView.context
        var player = playerView.tag as? ExoPlayer
        if (player == null) {
            player = ExoPlayer.Builder(context).build()
            playerView.player = player
            playerView.tag = player
        }
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = false
    }

    fun releasePlayer(playerView: PlayerView) {
        val player = playerView.tag as? ExoPlayer ?: return
        player.release()
        playerView.player = null
        playerView.tag = null
    }

    fun openLightbox(context: Context, url: String?, mediaType: String?) {
        if (url.isNullOrBlank() || MediaType.isVideo(mediaType, url)) return
        val activity = context as? FragmentActivity ?: return
        MediaLightboxDialog.show(activity.supportFragmentManager, url, MediaType.isGif(mediaType, url))
    }
}