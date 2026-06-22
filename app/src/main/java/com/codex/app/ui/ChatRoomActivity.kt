package com.codex.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.codex.app.R
import com.codex.app.adapters.ChatMessageAdapter
import com.codex.app.databinding.ActivityChatRoomBinding
import com.codex.app.models.ChatMessage
import com.codex.app.models.ChatRoom
import com.codex.app.models.MediaType
import com.codex.app.utils.FirebaseHelper
import com.codex.app.utils.MediaUploadHelper
import com.codex.app.utils.ModerationUiHelper
import com.codex.app.utils.TypingPreviewHelper
import com.codex.app.utils.WindowInsetsHelper
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class ChatRoomActivity : BaseThemedActivity(), GifPickerBottomSheet.Listener {

    private lateinit var binding: ActivityChatRoomBinding
    private lateinit var adapter: ChatMessageAdapter
    private var roomId: String? = null
    private var roomTitle: String? = null
    private var currentRoom: ChatRoom? = null
    private var messageListener: ListenerRegistration? = null
    private var roomListener: ListenerRegistration? = null
    private var isSending = false

    private var pendingLocalUri: Uri? = null
    private var pendingRemoteUrl: String? = null
    private var pendingMediaType: String? = null
    private var isFavoriteRoom = false

    private val pickVideo = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                setPendingMedia(uri, null, MediaType.VIDEO)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatRoomBinding.inflate(layoutInflater)
        setContentView(binding.root)

        roomId = intent.getStringExtra(EXTRA_ROOM_ID)
        roomTitle = intent.getStringExtra(EXTRA_ROOM_TITLE)
        if (roomId.isNullOrBlank()) {
            finish()
            return
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = roomTitle ?: getString(R.string.live_chat)
        binding.toolbar.setNavigationOnClickListener { finish() }
        WindowInsetsHelper.applyChatSafeArea(
            binding.toolbar,
            binding.messageBar,
            binding.messagesRecycler
        )

        adapter = ChatMessageAdapter(
            onLongPress = { msg -> onMessageLongPress(msg) },
            onAuthorClick = { authorId ->
                startActivity(UserProfileActivity.intent(this, authorId))
            }
        )
        adapter.setCurrentUserId(FirebaseHelper.getCurrentFirebaseUser()?.uid.orEmpty())

        val layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.messagesRecycler.layoutManager = layoutManager
        binding.messagesRecycler.adapter = adapter

        binding.sendBtn.setOnClickListener { sendMessage() }
        binding.messageInput.setOnEditorActionListener { _, _, _ ->
            sendMessage()
            true
        }
        TypingPreviewHelper.setup(binding.messageInput, binding.typingPreview)

        binding.attachMediaBtn.setOnClickListener { GifPickerBottomSheet.show(this) }
        binding.attachVideoBtn.setOnClickListener {
            pickVideo.launch(Intent(Intent.ACTION_GET_CONTENT).apply { type = "video/*" })
        }
        binding.removeMediaBtn.setOnClickListener { clearPendingMedia() }

        binding.messageInput.isEnabled = false
        binding.sendBtn.isEnabled = false
        preflightAndStart()
    }

    override fun onGifSelected(selection: GifPickerBottomSheet.Selection) {
        when (selection) {
            is GifPickerBottomSheet.Selection.Local -> {
                setPendingMedia(selection.uri, null, selection.mediaType)
            }
            is GifPickerBottomSheet.Selection.Remote -> {
                setPendingMedia(null, selection.url, selection.mediaType)
            }
        }
    }

    private fun setPendingMedia(uri: Uri?, remoteUrl: String?, mediaType: String) {
        pendingLocalUri = uri
        pendingRemoteUrl = remoteUrl
        pendingMediaType = mediaType
        binding.mediaPreviewRow.visibility = View.VISIBLE
        binding.mediaPreviewLabel.text = when (mediaType) {
            MediaType.GIF -> getString(R.string.add_gif)
            MediaType.VIDEO -> getString(R.string.attach_video)
            else -> getString(R.string.attach_media)
        }
        when {
            uri != null && mediaType == MediaType.GIF -> {
                Glide.with(this).asGif().load(uri).fitCenter().into(binding.mediaPreview)
            }
            uri != null -> {
                Glide.with(this).load(uri).centerCrop().into(binding.mediaPreview)
            }
            remoteUrl != null && mediaType == MediaType.GIF -> {
                Glide.with(this).asGif().load(remoteUrl).fitCenter().into(binding.mediaPreview)
            }
            remoteUrl != null -> {
                Glide.with(this).load(remoteUrl).centerCrop().into(binding.mediaPreview)
            }
            else -> binding.mediaPreview.setImageDrawable(null)
        }
    }

    private fun clearPendingMedia() {
        pendingLocalUri = null
        pendingRemoteUrl = null
        pendingMediaType = null
        binding.mediaPreviewRow.visibility = View.GONE
        binding.mediaPreview.setImageDrawable(null)
    }

    private fun preflightAndStart() {
        val rid = roomId ?: return
        lifecycleScope.launch {
            val res = FirebaseHelper.prepareChatRoomAccess(rid)
            if (!isChatUiActive()) return@launch
            if (res.isFailure) {
                closeWithAccessError(res.exceptionOrNull()?.message)
                return@launch
            }
            currentRoom = res.getOrThrow()
            val favIds = FirebaseHelper.getFavoriteRoomIds()
            isFavoriteRoom = favIds.contains(rid)
            invalidateOptionsMenu()
            binding.messageInput.isEnabled = true
            binding.sendBtn.isEnabled = true
            startListening()
            listenRoom()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.chat_room_menu, menu)
        val role = FirebaseHelper.resolveRole(FirebaseHelper.currentUser)
        val showModTopic = role.canModerate() && currentRoom?.isTopic() == true
        menu.findItem(R.id.action_lock_topic)?.isVisible = showModTopic
        menu.findItem(R.id.action_reset_topic)?.isVisible = showModTopic
        menu.findItem(R.id.action_delete_topic)?.isVisible = showModTopic
        if (showModTopic) {
            menu.findItem(R.id.action_lock_topic)?.title = if (currentRoom?.locked == true) {
                getString(R.string.unlock_topic)
            } else {
                getString(R.string.lock_topic)
            }
        }
        menu.findItem(R.id.action_favorite)?.setIcon(
            if (isFavoriteRoom) R.drawable.ic_star_filled else R.drawable.ic_star
        )
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val rid = roomId ?: return false
        val room = currentRoom
        return when (item.itemId) {
            R.id.action_favorite -> {
                lifecycleScope.launch {
                    val res = FirebaseHelper.toggleFavorite(rid)
                    if (res.isSuccess) {
                        isFavoriteRoom = res.getOrNull() ?: false
                        invalidateOptionsMenu()
                        Toast.makeText(
                            this@ChatRoomActivity,
                            if (isFavoriteRoom) R.string.favorite_added else R.string.favorite_removed,
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        showError(res.exceptionOrNull()?.message)
                    }
                }
                true
            }
            R.id.action_lock_topic -> {
                val lock = room?.locked != true
                lifecycleScope.launch {
                    val res = FirebaseHelper.lockTopicRoom(rid, lock)
                    if (res.isSuccess) {
                        Toast.makeText(
                            this@ChatRoomActivity,
                            if (lock) R.string.topic_locked else R.string.topic_unlocked,
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        showError(res.exceptionOrNull()?.message)
                    }
                }
                true
            }
            R.id.action_reset_topic -> {
                AlertDialog.Builder(this)
                    .setMessage(R.string.reset_topic_confirm)
                    .setPositiveButton(R.string.reset_topic) { _, _ ->
                        lifecycleScope.launch {
                            val res = FirebaseHelper.resetTopicRoom(rid)
                            if (res.isSuccess) {
                                Toast.makeText(this@ChatRoomActivity, R.string.topic_reset, Toast.LENGTH_SHORT).show()
                            } else {
                                showError(res.exceptionOrNull()?.message)
                            }
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            }
            R.id.action_delete_topic -> {
                val name = room?.topicName?.takeIf { it.isNotBlank() } ?: room?.title ?: roomTitle.orEmpty()
                AlertDialog.Builder(this)
                    .setMessage(getString(R.string.delete_topic_full_confirm, name))
                    .setPositiveButton(R.string.delete_topic) { _, _ ->
                        lifecycleScope.launch {
                            val res = FirebaseHelper.deleteTopicAndCategoryFully(
                                categoryName = name,
                                categoryId = room?.topicId
                            )
                            if (res.isSuccess) {
                                Toast.makeText(this@ChatRoomActivity, R.string.topic_deleted, Toast.LENGTH_SHORT).show()
                                setResult(RESULT_TOPIC_DELETED)
                                finish()
                            } else {
                                showError(res.exceptionOrNull()?.message)
                            }
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun listenRoom() {
        val rid = roomId ?: return
        roomListener?.remove()
        roomListener = FirebaseHelper.listenChatRoom(
            roomId = rid,
            onUpdate = { room ->
                if (!isChatUiActive()) return@listenChatRoom
                binding.root.post {
                    if (!isChatUiActive()) return@post
                    if (room == null) {
                        closeWithAccessError(getString(R.string.chat_unavailable))
                        return@post
                    }
                    currentRoom = room
                    val locked = room.locked
                    binding.lockedBanner.visibility = if (locked) View.VISIBLE else View.GONE
                    val role = FirebaseHelper.resolveRole(FirebaseHelper.currentUser)
                    val canSend = !locked || role.canModerate()
                    binding.messageInput.isEnabled = canSend
                    binding.sendBtn.isEnabled = canSend
                    binding.attachMediaBtn.isEnabled = canSend
                    binding.attachVideoBtn.isEnabled = canSend
                    invalidateOptionsMenu()
                }
            },
            onError = { err -> handleChatAccessError(err.message) }
        )
    }

    private fun startListening() {
        val rid = roomId ?: return
        messageListener?.remove()
        messageListener = FirebaseHelper.listenMessages(
            roomId = rid,
            onUpdate = { messages ->
                if (!isChatUiActive()) return@listenMessages
                binding.messagesRecycler.post {
                    if (!isChatUiActive()) return@post
                    adapter.submitList(messages) {
                        if (!isChatUiActive()) return@submitList
                        binding.emptyText.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
                        scrollToLatestMessage()
                    }
                }
            },
            onError = { err -> handleChatAccessError(err.message) }
        )
    }

    private fun scrollToLatestMessage() {
        val count = adapter.itemCount
        if (count <= 0) return
        val lastIndex = count - 1
        binding.messagesRecycler.post {
            val lm = binding.messagesRecycler.layoutManager as? LinearLayoutManager ?: return@post
            lm.scrollToPositionWithOffset(lastIndex, 0)
        }
    }

    private fun handleChatAccessError(message: String?) {
        if (!isChatUiActive()) return
        binding.root.post { closeWithAccessError(message) }
    }

    private fun closeWithAccessError(message: String?) {
        if (isFinishing || isDestroyed) return
        Toast.makeText(
            this,
            message ?: getString(R.string.chat_access_denied),
            Toast.LENGTH_LONG
        ).show()
        finish()
    }

    private fun isChatUiActive(): Boolean = !isFinishing && !isDestroyed

    private fun sendMessage() {
        if (isSending) return
        val rid = roomId ?: return
        val text = binding.messageInput.text?.toString().orEmpty()
        val hasMedia = pendingLocalUri != null || !pendingRemoteUrl.isNullOrBlank()
        if (text.isBlank() && !hasMedia) return

        isSending = true
        binding.sendBtn.isEnabled = false
        lifecycleScope.launch {
            try {
                var imageUrl = pendingRemoteUrl
                var mediaType = pendingMediaType
                val localUri = pendingLocalUri
                val uid = FirebaseHelper.getCurrentFirebaseUser()?.uid

                if (localUri != null && uid != null) {
                    val uploadRes = if (pendingMediaType == MediaType.VIDEO) {
                        MediaUploadHelper.uploadChatVideo(
                            this@ChatRoomActivity,
                            FirebaseHelper.storage,
                            localUri,
                            uid
                        )
                    } else {
                        MediaUploadHelper.uploadMedia(
                            this@ChatRoomActivity,
                            FirebaseHelper.storage,
                            localUri,
                            "chat_media",
                            uid
                        )
                    }
                    if (uploadRes.isFailure) {
                        showError(uploadRes.exceptionOrNull()?.message)
                        return@launch
                    }
                    imageUrl = uploadRes.getOrNull()?.url
                    mediaType = uploadRes.getOrNull()?.mediaType
                }

                val res = FirebaseHelper.sendChatMessage(rid, text, imageUrl, mediaType)
                if (res.isSuccess) {
                    binding.messageInput.text?.clear()
                    clearPendingMedia()
                    TypingPreviewHelper.clearPreview(binding.messageInput, binding.typingPreview)
                } else {
                    showError(res.exceptionOrNull()?.message)
                }
            } finally {
                isSending = false
                val locked = currentRoom?.locked == true
                val role = FirebaseHelper.resolveRole(FirebaseHelper.currentUser)
                val canSend = !locked || role.canModerate()
                binding.sendBtn.isEnabled = canSend
            }
        }
    }

    private fun onMessageLongPress(msg: ChatMessage) {
        val role = FirebaseHelper.resolveRole(FirebaseHelper.currentUser)
        val isOwn = msg.authorId == FirebaseHelper.getCurrentFirebaseUser()?.uid
        if (!role.canModerate() && !isOwn) return

        val options = mutableListOf(getString(R.string.delete_comment))
        if (role.canModerate() && !isOwn) {
            options.add(getString(R.string.ban_user))
        }

        AlertDialog.Builder(this)
            .setTitle(msg.authorName)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    getString(R.string.delete_comment) -> {
                        val rid = roomId ?: return@setItems
                        lifecycleScope.launch {
                            val res = FirebaseHelper.deleteChatMessage(rid, msg.id, msg.authorId)
                            if (res.isSuccess) {
                                Toast.makeText(this@ChatRoomActivity, R.string.message_deleted, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    getString(R.string.ban_user) -> {
                        lifecycleScope.launch {
                            val user = FirebaseHelper.fetchUser(msg.authorId)
                            if (user != null) {
                                ModerationUiHelper.showQuickActions(
                                    context = this@ChatRoomActivity,
                                    scope = lifecycleScope,
                                    target = user,
                                    actorRole = role,
                                    includeFounder = FirebaseHelper.isFounderAccount()
                                )
                            }
                        }
                    }
                }
            }
            .show()
    }

    private fun showError(message: String?) {
        Toast.makeText(
            this,
            message ?: getString(R.string.error_generic),
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onDestroy() {
        messageListener?.remove()
        messageListener = null
        roomListener?.remove()
        roomListener = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ROOM_ID = "roomId"
        const val EXTRA_ROOM_TITLE = "roomTitle"
        const val RESULT_TOPIC_DELETED = 100
    }
}