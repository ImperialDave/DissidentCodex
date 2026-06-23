package com.codex.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.codex.app.MainActivity
import com.codex.app.R
import com.codex.app.adapters.PostAdapter
import com.codex.app.databinding.ActivityUserProfileBinding
import com.codex.app.models.ChessGame
import com.codex.app.models.Role
import com.codex.app.models.User
import com.codex.app.utils.ChessHelper
import com.codex.app.utils.FirebaseHelper
import com.codex.app.utils.MediaUploadHelper
import com.codex.app.utils.WindowInsetsHelper
import com.google.android.material.chip.Chip
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class UserProfileActivity : BaseThemedActivity() {

    private lateinit var binding: ActivityUserProfileBinding
    private lateinit var postAdapter: PostAdapter
    private var targetUserId: String? = null
    private var targetUser: User? = null
    private var incomingRequestId: String? = null
    private var chessListener: ListenerRegistration? = null
    private var currentChessGame: ChessGame? = null
    private var isFriendsWithTarget = false
    private var canShowChessPanel = false
    private var isBlockedUser = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        targetUserId = intent.getStringExtra(EXTRA_USER_ID)
        if (targetUserId.isNullOrBlank()) {
            finish()
            return
        }

        binding.toolbar.setNavigationOnClickListener { finish() }
        WindowInsetsHelper.applyTopSafeArea(binding.toolbar)

        setupPostsRecycler()
        binding.messageBtn.setOnClickListener { openDm() }
        binding.friendActionBtn.setOnClickListener { handleFriendAction() }
        binding.blockUserBtn.setOnClickListener { handleBlockAction() }
        binding.editProfileBtn.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_PROFILE, true))
            finish()
        }

        loadProfile()
    }

    override fun onStart() {
        super.onStart()
        startChessListenerIfNeeded()
    }

    private fun startChessListenerIfNeeded() {
        val uid = targetUserId ?: return
        if (!canShowChessPanel) return
        chessListener?.remove()
        chessListener = null
        lifecycleScope.launch {
            chessListener = ChessHelper.listenGameWithUserIfExists(
                opponentUid = uid,
                onUpdate = { game -> runOnUiThread { bindChessPanel(game) } },
                onError = { err ->
                    runOnUiThread {
                        Toast.makeText(this@UserProfileActivity, err.message ?: "Chess sync error", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    override fun onStop() {
        chessListener?.remove()
        chessListener = null
        super.onStop()
    }

    private fun setupPostsRecycler() {
        postAdapter = PostAdapter(
            onPostClick = { post ->
                startActivity(Intent(this, PostDetailActivity::class.java).putExtra("postId", post.id))
            },
            onLikeClick = { },
            onDeleteClick = { },
            currentUserRole = { FirebaseHelper.resolveRole(FirebaseHelper.currentUser) },
            onCategoryClick = null,
            onAuthorClick = null
        )
        binding.postsRecycler.layoutManager = LinearLayoutManager(this)
        binding.postsRecycler.adapter = postAdapter
    }

    private fun loadProfile() {
        val uid = targetUserId ?: return
        lifecycleScope.launch {
            val user = FirebaseHelper.fetchUser(uid)
            if (user == null) {
                Toast.makeText(this@UserProfileActivity, R.string.user_not_found, Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            targetUser = user
            bindUser(user)
            isBlockedUser = FirebaseHelper.isBlocked(uid)
            bindBlockState(uid)
            bindFriendActions(uid)
            bindFavorites(uid)
            loadPosts(uid)
        }
    }

    private fun bindUser(user: User) {
        binding.displayNameText.text = user.displayName.ifBlank { "Codex User" }
        binding.bioText.text = user.bio.ifBlank { getString(R.string.no_bio) }
        binding.chessEloText.text = if (user.chessGamesPlayed > 0) {
            getString(
                R.string.chess_elo_profile,
                user.chessElo,
                user.chessWins,
                user.chessLosses,
                user.chessDraws
            )
        } else {
            getString(R.string.chess_elo_unrated)
        }

        val role = FirebaseHelper.resolveRole(user)
        binding.roleBadge.setImageResource(roleBadge(role))

        if (!user.flair.isNullOrBlank()) {
            binding.flairChip.visibility = View.VISIBLE
            binding.flairChip.text = user.flair
        } else {
            binding.flairChip.visibility = View.GONE
        }

        Glide.with(binding.profileAvatar)
            .load(user.photoUrl?.takeIf { it.isNotBlank() } ?: R.drawable.default_avatar)
            .placeholder(R.drawable.default_avatar)
            .into(binding.profileAvatar)

        if (!user.backgroundUrl.isNullOrBlank()) {
            MediaUploadHelper.loadMediaInto(binding.profileBanner, user.backgroundUrl, null)
        } else {
            binding.profileBanner.setImageDrawable(null)
        }

        val myUid = FirebaseHelper.getCurrentFirebaseUser()?.uid
        val isSelf = myUid == user.uid
        binding.editProfileBtn.visibility = if (isSelf) View.VISIBLE else View.GONE
        binding.actionRow.visibility = if (isSelf) View.GONE else View.VISIBLE

        val restricted = user.isBanned() || user.isSuspended()
        if (restricted) {
            binding.statusBanner.visibility = View.VISIBLE
            binding.statusBanner.text = if (user.isBanned()) {
                getString(R.string.user_banned_profile)
            } else {
                getString(R.string.user_suspended_profile)
            }
            binding.friendActionBtn.isEnabled = false
            binding.messageBtn.isEnabled = false
            binding.chessPanelInclude.chessPanel.visibility = View.GONE
        } else {
            binding.statusBanner.visibility = View.GONE
        }
    }

    private fun bindBlockState(uid: String) {
        val myUid = FirebaseHelper.getCurrentFirebaseUser()?.uid
        val isSelf = myUid == uid
        binding.blockUserBtn.visibility = if (isSelf) View.GONE else View.VISIBLE
        if (isSelf) return

        binding.blockUserBtn.text = getString(
            if (isBlockedUser) R.string.unblock_user else R.string.block_user
        )
        if (isBlockedUser) {
            binding.statusBanner.visibility = View.VISIBLE
            binding.statusBanner.text = getString(R.string.user_blocked_banner)
            binding.friendActionBtn.isEnabled = false
            binding.messageBtn.isEnabled = false
            binding.chessPanelInclude.chessPanel.visibility = View.GONE
        } else if (targetUser?.isBanned() != true && targetUser?.isSuspended() != true) {
            binding.statusBanner.visibility = View.GONE
        }
    }

    private fun bindFriendActions(uid: String) {
        lifecycleScope.launch {
            if (isBlockedUser) {
                binding.friendActionBtn.visibility = View.GONE
                binding.messageBtn.visibility = View.GONE
                binding.chessPanelInclude.chessPanel.visibility = View.GONE
                return@launch
            }
            binding.friendActionBtn.visibility = View.VISIBLE
            binding.messageBtn.visibility = View.VISIBLE
            val status = FirebaseHelper.getFriendshipStatus(uid)
            isFriendsWithTarget = status == FirebaseHelper.FriendshipStatus.FRIENDS
            canShowChessPanel = status != FirebaseHelper.FriendshipStatus.SELF &&
                targetUser?.isBanned() != true &&
                targetUser?.isSuspended() != true
            when (status) {
                FirebaseHelper.FriendshipStatus.SELF -> {
                    binding.friendActionBtn.visibility = View.GONE
                    binding.chessPanelInclude.chessPanel.visibility = View.GONE
                }
                FirebaseHelper.FriendshipStatus.FRIENDS -> {
                    binding.friendActionBtn.text = getString(R.string.friends_label)
                    binding.friendActionBtn.isEnabled = true
                }
                FirebaseHelper.FriendshipStatus.PENDING_OUT -> {
                    binding.friendActionBtn.text = getString(R.string.request_pending)
                    binding.friendActionBtn.isEnabled = false
                }
                FirebaseHelper.FriendshipStatus.PENDING_IN -> {
                    val requests = FirebaseHelper.getIncomingFriendRequests()
                    incomingRequestId = requests.find { it.fromUid == uid }?.id
                    binding.friendActionBtn.text = getString(R.string.accept_friend)
                    binding.friendActionBtn.isEnabled = true
                }
                FirebaseHelper.FriendshipStatus.NONE -> {
                    binding.friendActionBtn.text = getString(R.string.add_friend)
                    binding.friendActionBtn.isEnabled = true
                }
            }
            if (canShowChessPanel) {
                bindChessPanel(ChessHelper.getGameWithUser(uid))
                startChessListenerIfNeeded()
            } else {
                chessListener?.remove()
                chessListener = null
                binding.chessPanelInclude.chessPanel.visibility = View.GONE
            }
        }
    }

    private fun bindChessPanel(game: ChessGame?) {
        if (!canShowChessPanel) {
            binding.chessPanelInclude.chessPanel.visibility = View.GONE
            return
        }
        val panel = binding.chessPanelInclude
        val myUid = FirebaseHelper.getCurrentFirebaseUser()?.uid ?: return
        val oppName = targetUser?.displayName?.ifBlank { "Friend" } ?: "Friend"

        panel.chessPanel.visibility = View.VISIBLE
        currentChessGame = game

        if (game == null || game.status == ChessGame.STATUS_DECLINED) {
            panel.chessStatusText.text = getString(R.string.chess_no_game_with_user, oppName)
            panel.chessPrimaryBtn.text = getString(R.string.chess_play)
            panel.chessPrimaryBtn.isEnabled = true
            panel.chessPrimaryBtn.setOnClickListener { sendChessChallenge() }
            panel.chessSecondaryBtn.visibility = View.GONE
            return
        }

        when (game.status) {
            ChessGame.STATUS_PENDING -> {
                if (game.challengerUid == myUid) {
                    panel.chessStatusText.text = getString(R.string.chess_waiting_for, oppName)
                    panel.chessPrimaryBtn.text = getString(R.string.chess_open_board)
                    panel.chessPrimaryBtn.isEnabled = true
                    panel.chessPrimaryBtn.setOnClickListener { openChessGame(game) }
                    panel.chessSecondaryBtn.visibility = View.VISIBLE
                    panel.chessSecondaryBtn.text = getString(R.string.chess_cancel)
                    panel.chessSecondaryBtn.setOnClickListener { cancelChessChallenge(game) }
                } else {
                    panel.chessStatusText.text = getString(R.string.chess_incoming_challenge, oppName)
                    panel.chessPrimaryBtn.text = getString(R.string.chess_accept_play)
                    panel.chessPrimaryBtn.isEnabled = true
                    panel.chessPrimaryBtn.setOnClickListener { acceptAndPlay(game) }
                    panel.chessSecondaryBtn.visibility = View.VISIBLE
                    panel.chessSecondaryBtn.text = getString(R.string.decline)
                    panel.chessSecondaryBtn.setOnClickListener { declineChessChallenge(game) }
                }
            }
            ChessGame.STATUS_ACTIVE -> {
                panel.chessStatusText.text = if (game.isMyTurn(myUid)) {
                    getString(R.string.chess_active_your_turn, oppName)
                } else {
                    getString(R.string.chess_active_their_turn, oppName)
                }
                panel.chessPrimaryBtn.text = getString(R.string.chess_play_now)
                panel.chessPrimaryBtn.isEnabled = true
                panel.chessPrimaryBtn.setOnClickListener { openChessGame(game) }
                panel.chessSecondaryBtn.visibility = View.GONE
            }
            ChessGame.STATUS_FINISHED -> {
                panel.chessStatusText.text = chessResultSummary(game, myUid, oppName)
                panel.chessPrimaryBtn.text = getString(R.string.chess_view_game)
                panel.chessPrimaryBtn.isEnabled = true
                panel.chessPrimaryBtn.setOnClickListener { openChessGame(game) }
                panel.chessSecondaryBtn.visibility = View.VISIBLE
                panel.chessSecondaryBtn.text = getString(R.string.chess_rematch)
                panel.chessSecondaryBtn.setOnClickListener { sendChessChallenge() }
            }
            else -> {
                panel.chessStatusText.text = getString(R.string.chess_no_game_with_user, oppName)
                panel.chessPrimaryBtn.text = getString(R.string.chess_play)
                panel.chessPrimaryBtn.setOnClickListener { sendChessChallenge() }
                panel.chessSecondaryBtn.visibility = View.GONE
            }
        }
    }

    private fun chessResultSummary(game: ChessGame, myUid: String, oppName: String): String {
        return when (game.result) {
            ChessGame.RESULT_CHECKMATE ->
                if (game.winnerUid == myUid) getString(R.string.chess_you_won_vs, oppName)
                else getString(R.string.chess_you_lost_vs, oppName)
            ChessGame.RESULT_RESIGN ->
                if (game.winnerUid == myUid) getString(R.string.chess_opponent_resigned, oppName)
                else getString(R.string.chess_you_resigned_vs, oppName)
            ChessGame.RESULT_STALEMATE -> getString(R.string.chess_draw_vs, oppName)
            else -> getString(R.string.chess_game_over)
        }
    }

    private fun openChessGame(game: ChessGame) {
        startActivity(ChessGameActivity.intent(this, game.id))
    }

    private fun sendChessChallenge() {
        val uid = targetUserId ?: return
        lifecycleScope.launch {
            try {
                val res = ChessHelper.sendChallenge(uid)
                if (res.isSuccess) {
                    val game = res.getOrThrow()
                    bindChessPanel(game)
                    Toast.makeText(this@UserProfileActivity, R.string.chess_game_started, Toast.LENGTH_SHORT).show()
                    startChessListenerIfNeeded()
                    if (game.status == ChessGame.STATUS_ACTIVE) {
                        openChessGame(game)
                    }
                } else {
                    Toast.makeText(
                        this@UserProfileActivity,
                        res.exceptionOrNull()?.message ?: getString(R.string.error_generic),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@UserProfileActivity, e.message ?: getString(R.string.error_generic), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun acceptAndPlay(game: ChessGame) {
        lifecycleScope.launch {
            try {
                val res = ChessHelper.respondToChallenge(game.id, accept = true)
                if (res.isSuccess) {
                    openChessGame(res.getOrThrow())
                } else {
                    Toast.makeText(this@UserProfileActivity, res.exceptionOrNull()?.message, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@UserProfileActivity, e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun declineChessChallenge(game: ChessGame) {
        lifecycleScope.launch {
            val res = ChessHelper.respondToChallenge(game.id, accept = false)
            if (res.isSuccess) {
                bindChessPanel(null)
            } else {
                Toast.makeText(this@UserProfileActivity, res.exceptionOrNull()?.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun cancelChessChallenge(game: ChessGame) {
        lifecycleScope.launch {
            val res = ChessHelper.cancelChallenge(game.id)
            if (res.isSuccess) {
                bindChessPanel(null)
            } else {
                Toast.makeText(this@UserProfileActivity, res.exceptionOrNull()?.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindFavorites(uid: String) {
        lifecycleScope.launch {
            val favorites = FirebaseHelper.getFavoriteCategories(uid)
            binding.favoriteCommunitiesGroup.removeAllViews()
            if (favorites.isEmpty()) {
                binding.noFavoritesText.visibility = View.VISIBLE
                return@launch
            }
            binding.noFavoritesText.visibility = View.GONE
            favorites.forEach { fav ->
                val chip = Chip(this@UserProfileActivity).apply {
                    text = fav.name
                    isClickable = false
                }
                binding.favoriteCommunitiesGroup.addView(chip)
            }
        }
    }

    private fun loadPosts(uid: String) {
        lifecycleScope.launch {
            val posts = FirebaseHelper.getPostsByUser(uid).take(20)
            postAdapter.submitList(posts)
            binding.noPostsText.visibility = if (posts.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun handleFriendAction() {
        val uid = targetUserId ?: return
        lifecycleScope.launch {
            val status = FirebaseHelper.getFriendshipStatus(uid)
            val res = when (status) {
                FirebaseHelper.FriendshipStatus.PENDING_IN -> {
                    val reqId = incomingRequestId ?: FirebaseHelper.getIncomingFriendRequests()
                        .find { it.fromUid == uid }?.id
                    if (reqId == null) {
                        Toast.makeText(this@UserProfileActivity, R.string.error_generic, Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    FirebaseHelper.respondToFriendRequest(reqId, accept = true)
                }
                FirebaseHelper.FriendshipStatus.FRIENDS -> {
                    FirebaseHelper.removeFriend(uid)
                }
                else -> FirebaseHelper.sendFriendRequest(uid)
            }
            if (res.isSuccess) {
                bindFriendActions(uid)
                Toast.makeText(
                    this@UserProfileActivity,
                    when (status) {
                        FirebaseHelper.FriendshipStatus.FRIENDS -> R.string.friend_removed
                        FirebaseHelper.FriendshipStatus.PENDING_IN -> R.string.friend_added
                        else -> R.string.friend_request_sent
                    },
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this@UserProfileActivity,
                    res.exceptionOrNull()?.message ?: getString(R.string.error_generic),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun handleBlockAction() {
        val uid = targetUserId ?: return
        val name = targetUser?.displayName?.ifBlank { "this user" } ?: "this user"
        lifecycleScope.launch {
            if (!isBlockedUser) {
                androidx.appcompat.app.AlertDialog.Builder(this@UserProfileActivity)
                    .setMessage(getString(R.string.block_user_confirm, name))
                    .setPositiveButton(R.string.block_user) { _, _ -> performBlockToggle(uid) }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            } else {
                performBlockToggle(uid)
            }
        }
    }

    private fun performBlockToggle(uid: String) {
        lifecycleScope.launch {
            val wasBlocked = isBlockedUser
            val res = if (wasBlocked) {
                FirebaseHelper.unblockUser(uid)
            } else {
                FirebaseHelper.blockUser(uid)
            }
            if (res.isSuccess) {
                isBlockedUser = !wasBlocked
                Toast.makeText(
                    this@UserProfileActivity,
                    getString(if (wasBlocked) R.string.user_unblocked_toast else R.string.user_blocked_toast),
                    Toast.LENGTH_SHORT
                ).show()
                bindBlockState(uid)
                bindFriendActions(uid)
                loadPosts(uid)
            } else {
                Toast.makeText(
                    this@UserProfileActivity,
                    res.exceptionOrNull()?.message ?: getString(R.string.error_generic),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun openDm() {
        val uid = targetUserId ?: return
        lifecycleScope.launch {
            val res = FirebaseHelper.getOrCreateDmRoom(uid)
            if (res.isSuccess) {
                val room = res.getOrThrow()
                startActivity(
                    Intent(this@UserProfileActivity, ChatRoomActivity::class.java)
                        .putExtra(ChatRoomActivity.EXTRA_ROOM_ID, room.id)
                        .putExtra(ChatRoomActivity.EXTRA_ROOM_TITLE, room.title)
                )
            } else {
                Toast.makeText(
                    this@UserProfileActivity,
                    res.exceptionOrNull()?.message ?: getString(R.string.error_generic),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun roleBadge(role: Role): Int = when (role) {
        Role.FOUNDER -> R.drawable.badge_founder
        Role.ADMIN -> R.drawable.badge_admin
        Role.MOD -> R.drawable.badge_mod
        Role.SUSPENDED -> R.drawable.badge_suspended
        Role.BANNED -> R.drawable.badge_banned
        else -> R.drawable.badge_user
    }

    companion object {
        const val EXTRA_USER_ID = "userId"

        fun intent(context: Context, userId: String): Intent {
            return Intent(context, UserProfileActivity::class.java).putExtra(EXTRA_USER_ID, userId)
        }
    }
}