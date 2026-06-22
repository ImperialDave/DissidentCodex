package com.codex.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope

import com.bumptech.glide.Glide
import com.codex.app.MainActivity
import com.codex.app.R
import com.codex.app.adapters.PostAdapter
import com.codex.app.adapters.ProfileCommentAdapter
import com.codex.app.auth.AuthActivity
import com.codex.app.databinding.FragmentProfileBinding
import com.codex.app.models.Role
import com.codex.app.models.ChessGame
import com.codex.app.utils.ChessHelper
import com.codex.app.utils.FirebaseHelper
import com.codex.app.utils.MediaUploadHelper
import com.codex.app.utils.ModerationUiHelper
import com.codex.app.utils.setupInsideScrollView
import com.codex.app.utils.ThemeManager
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var myPostsAdapter: PostAdapter
    private lateinit var myCommentsAdapter: ProfileCommentAdapter
    private var selectedNewPicUri: Uri? = null
    private var selectedBannerUri: Uri? = null
    private var favoriteCategoryIds: Set<String> = emptySet()
    private var feedCategories: List<String> = emptyList()

    private val pickProfilePic = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            res.data?.data?.let { uri ->
                selectedNewPicUri = uri
                Glide.with(this).load(uri).into(binding.profileAvatar)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    private val pickBanner = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            res.data?.data?.let { uri ->
                selectedBannerUri = uri
                Glide.with(this).load(uri).centerCrop().into(binding.profileBanner)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMyPostsRecycler()
        setupMyCommentsRecycler()
        setupThemePicker()
        loadCurrentUser()
        setupActions()
        setupFlairPreview()
    }

    private fun setupFlairPreview() {
        binding.flairInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val flair = s?.toString()?.trim().orEmpty()
                if (flair.isNotBlank()) {
                    binding.flairPreviewChip.visibility = View.VISIBLE
                    binding.flairPreviewChip.text = flair
                } else {
                    binding.flairPreviewChip.visibility = View.GONE
                }
            }
        })
    }

    private fun setupThemePicker() {
        val current = ThemeManager.getPalette(requireContext())
        binding.themeChipGroup.removeAllViews()
        ThemeManager.Palette.entries.forEach { palette ->
            val chip = Chip(requireContext()).apply {
                text = getString(palette.labelRes)
                isCheckable = true
                isChecked = palette == current
                setOnClickListener {
                    if (palette != ThemeManager.getPalette(requireContext())) {
                        ThemeManager.savePalette(requireContext(), palette)
                        Toast.makeText(requireContext(), getString(R.string.theme_applied), Toast.LENGTH_SHORT).show()
                        activity?.recreate()
                    }
                }
            }
            binding.themeChipGroup.addView(chip)
        }
    }

    private fun setupMyCommentsRecycler() {
        myCommentsAdapter = ProfileCommentAdapter { comment ->
            startActivity(
                Intent(requireContext(), PostDetailActivity::class.java).putExtra("postId", comment.postId)
            )
        }
        binding.myCommentsRecycler.setupInsideScrollView()
        binding.myCommentsRecycler.adapter = myCommentsAdapter
    }

    private fun setupMyPostsRecycler() {
        myPostsAdapter = PostAdapter(
            onPostClick = { post ->
                val i = Intent(requireContext(), PostDetailActivity::class.java)
                i.putExtra("postId", post.id)
                startActivity(i)
            },
            onLikeClick = { /* no op in profile for simplicity */ },
            onDeleteClick = { post ->
                lifecycleScope.launch {
                    val role = FirebaseHelper.resolveRole(FirebaseHelper.currentUser)
                    val res = FirebaseHelper.deletePost(post.id, role)
                    if (res.isSuccess) {
                        (activity as? MainActivity)?.showToast("Post deleted")
                    } else {
                        Toast.makeText(requireContext(), res.exceptionOrNull()?.message ?: "Delete failed", Toast.LENGTH_SHORT).show()
                    }
                    loadMyPosts()
                }
            },
            currentUserRole = { FirebaseHelper.resolveRole(FirebaseHelper.currentUser) },
            onCategoryClick = null,
            onAuthorClick = null
        )
        binding.myPostsRecycler.setupInsideScrollView()
        binding.myPostsRecycler.adapter = myPostsAdapter
    }

    private fun loadCurrentUser() {
        lifecycleScope.launch {
            val fb = FirebaseHelper.getCurrentFirebaseUser()
            val fetched = fb?.uid?.let { FirebaseHelper.fetchUser(it) } ?: FirebaseHelper.currentUser
            if (fetched == null) return@launch

            val user = if (FirebaseHelper.isFounderEmail(fb?.email ?: fetched.email)) {
                FirebaseHelper.syncFounderRole().getOrNull() ?: fetched
            } else {
                fetched
            }
            FirebaseHelper.currentUser = user

            binding.displayNameText.setText(user.displayName.ifBlank { "Codex User" })
            binding.emailText.text = user.email
            binding.bioInput.setText(user.bio)
            binding.flairInput.setText(user.flair.orEmpty())
            if (!user.flair.isNullOrBlank()) {
                binding.flairPreviewChip.visibility = View.VISIBLE
                binding.flairPreviewChip.text = user.flair
            }

            if (!user.backgroundUrl.isNullOrBlank()) {
                MediaUploadHelper.loadMediaInto(binding.profileBanner, user.backgroundUrl, null)
            }

            val role = FirebaseHelper.resolveRole(user, fb?.email)
            binding.roleText.text = role.displayName()
            binding.chessEloText.text = formatChessElo(user)
            binding.roleBadgeLarge.setImageResource(
                when (role) {
                    Role.FOUNDER -> R.drawable.badge_founder
                    Role.ADMIN -> R.drawable.badge_admin
                    Role.MOD -> R.drawable.badge_mod
                    Role.SUSPENDED -> R.drawable.badge_suspended
                    Role.BANNED -> R.drawable.badge_banned
                    else -> R.drawable.badge_user
                }
            )

            // Load avatar
            if (!user.photoUrl.isNullOrEmpty()) {
                Glide.with(this@ProfileFragment)
                    .load(user.photoUrl)
                    .placeholder(R.drawable.default_avatar)
                    .into(binding.profileAvatar)
            } else {
                binding.profileAvatar.setImageResource(R.drawable.default_avatar)
            }

            // Show moderation tools
            val canMod = role.canModerate()
            val isFounder = role.isFounder()
            binding.moderationPanel.visibility = if (canMod || isFounder) View.VISIBLE else View.GONE
            // Use the button for mod or founder - label updated dynamically if needed
            binding.openModMenuButton.visibility = if (canMod || isFounder) View.VISIBLE else View.GONE
            if (isFounder) {
                binding.openModMenuButton.text = getString(R.string.founder_tools)
            } else if (canMod) {
                binding.openModMenuButton.text = getString(R.string.mod_tools)
            }

            loadMyPosts()
            loadMyComments()
            loadFavoriteCommunities(user.uid)
        }
    }

    private fun loadFavoriteCommunities(uid: String) {
        lifecycleScope.launch {
            feedCategories = FirebaseHelper.getFeedCategoryNames()
                .filter { it != FirebaseHelper.ALL_CATEGORY_LABEL }
            favoriteCategoryIds = FirebaseHelper.getFavoriteCategories(uid)
                .map { it.categoryId }
                .toSet()
            renderFavoriteCommunityChips()
        }
    }

    private fun renderFavoriteCommunityChips() {
        val b = _binding ?: return
        b.favoriteCommunitiesGroup.removeAllViews()
        feedCategories.forEach { name ->
            val categoryId = name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
            val chip = Chip(requireContext()).apply {
                text = name
                isCheckable = true
                isChecked = favoriteCategoryIds.contains(categoryId)
                setOnClickListener {
                    lifecycleScope.launch {
                        val res = FirebaseHelper.toggleFavoriteCategory(categoryId, name)
                        if (res.isFailure) {
                            Toast.makeText(
                                requireContext(),
                                res.exceptionOrNull()?.message ?: getString(R.string.error_generic),
                                Toast.LENGTH_SHORT
                            ).show()
                            isChecked = !isChecked
                        } else {
                            val uid = FirebaseHelper.getCurrentFirebaseUser()?.uid ?: return@launch
                            favoriteCategoryIds = FirebaseHelper.getFavoriteCategories(uid)
                                .map { it.categoryId }
                                .toSet()
                            renderFavoriteCommunityChips()
                        }
                    }
                }
            }
            b.favoriteCommunitiesGroup.addView(chip)
        }
    }

    private fun loadMyComments() {
        val uid = FirebaseHelper.getCurrentFirebaseUser()?.uid ?: return
        lifecycleScope.launch {
            val comments = FirebaseHelper.getCommentsByUser(uid)
            myCommentsAdapter.submitList(comments)
            binding.noCommentsText.visibility = if (comments.isEmpty()) View.VISIBLE else View.GONE
            binding.yourCommentsHeader.text = if (comments.isEmpty()) {
                "${getString(R.string.your_comments)} (0)"
            } else {
                "${getString(R.string.your_comments)} (${comments.size})"
            }
        }
    }

    private fun loadMyPosts() {
        val uid = FirebaseHelper.getCurrentFirebaseUser()?.uid ?: return
        lifecycleScope.launch {
            val myPosts = FirebaseHelper.getPostsByUser(uid)
            myPostsAdapter.submitList(myPosts)
            // Polish: dynamic header with count + empty hint
            val headerText = if (myPosts.isEmpty()) {
                getString(R.string.your_posts) + " (0) — Start posting from the Create tab!"
            } else {
                "${getString(R.string.your_posts)} (${myPosts.size})"
            }
            binding.yourPostsHeader.text = headerText
        }
    }

    private fun formatChessElo(user: com.codex.app.models.User): String {
        return if (user.chessGamesPlayed > 0) {
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
    }

    private fun openMyChessGames() {
        binding.openChessButton.isEnabled = false
        lifecycleScope.launch {
            try {
                val games = ChessHelper.getMyGames()
                val active = games.filter { it.status == ChessGame.STATUS_ACTIVE }
                when {
                    active.size == 1 -> {
                        startActivity(ChessGameActivity.intent(requireContext(), active.first().id))
                    }
                    active.isNotEmpty() -> {
                        startActivity(ChessLobbyActivity.intent(requireContext()))
                    }
                    games.isNotEmpty() -> {
                        startActivity(ChessLobbyActivity.intent(requireContext()))
                    }
                    else -> {
                        Toast.makeText(
                            requireContext(),
                            R.string.chess_no_games,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    e.message ?: getString(R.string.error_generic),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                binding.openChessButton.isEnabled = true
            }
        }
    }

    private fun setupActions() {
        binding.changePicButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
            pickProfilePic.launch(intent)
        }

        binding.changeBannerButton.setOnClickListener {
            pickBanner.launch(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" })
        }

        binding.openFriendsButton.setOnClickListener {
            startActivity(Intent(requireContext(), FriendsActivity::class.java))
        }

        binding.openChessButton.setOnClickListener {
            openMyChessGames()
        }

        binding.saveProfileButton.setOnClickListener {
            val newName = binding.displayNameText.text?.toString()?.trim()
            val newBio = binding.bioInput.text?.toString()?.trim()
            val newFlair = binding.flairInput.text?.toString()?.trim()

            if (!newName.isNullOrBlank() && newName.length > 50) {
                Toast.makeText(requireContext(), "Display name too long (max 50)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (newBio != null && newBio.length > 300) {
                Toast.makeText(requireContext(), "Bio too long (max 300 chars)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                var photoUrl: String? = null
                var backgroundUrl: String? = null
                if (selectedNewPicUri != null) {
                    val upload = FirebaseHelper.uploadImage(selectedNewPicUri!!, "profile_pics")
                    if (upload.isSuccess) photoUrl = upload.getOrNull()
                    else Toast.makeText(requireContext(), getString(R.string.image_upload_failed), Toast.LENGTH_SHORT).show()
                }
                if (selectedBannerUri != null) {
                    val upload = FirebaseHelper.uploadImage(selectedBannerUri!!, "profile_backgrounds")
                    if (upload.isSuccess) backgroundUrl = upload.getOrNull()
                    else Toast.makeText(requireContext(), getString(R.string.image_upload_failed), Toast.LENGTH_SHORT).show()
                }

                val res = FirebaseHelper.updateProfile(
                    displayName = if (!newName.isNullOrBlank()) newName else null,
                    bio = newBio,
                    photoUrl = photoUrl,
                    backgroundUrl = backgroundUrl,
                    flair = newFlair
                )
                if (res.isSuccess) {
                    (activity as? MainActivity)?.showToast("Profile updated")
                    selectedNewPicUri = null
                    selectedBannerUri = null
                    loadCurrentUser()
                } else {
                    Toast.makeText(requireContext(), res.exceptionOrNull()?.message ?: "Update failed", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.manageUsersButton.setOnClickListener {
            showUserManagementDialog()
        }

        binding.openModMenuButton.setOnClickListener {
            val intent = if (FirebaseHelper.isFounderAccount()) {
                Intent(requireContext(), FounderToolsActivity::class.java)
            } else {
                Intent(requireContext(), ModToolsActivity::class.java)
            }
            startActivity(intent)
        }

        binding.changePasswordButton.setOnClickListener {
            showChangePasswordDialog()
        }

        binding.logoutButton.setOnClickListener {
            FirebaseHelper.logout()
            startActivity(Intent(requireContext(), AuthActivity::class.java))
            activity?.finish()
        }
    }

    private fun showChangePasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_password, null)
        val currentInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.currentPasswordInput)
        val newInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.newPasswordInput)
        val confirmInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.confirmPasswordInput)

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.change_password)
            .setView(dialogView)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val current = currentInput.text?.toString().orEmpty()
                        val next = newInput.text?.toString().orEmpty()
                        val confirm = confirmInput.text?.toString().orEmpty()
                        when {
                            next.length < 6 -> {
                                Toast.makeText(requireContext(), R.string.password_too_short, Toast.LENGTH_SHORT).show()
                            }
                            next != confirm -> {
                                Toast.makeText(requireContext(), R.string.password_mismatch, Toast.LENGTH_SHORT).show()
                            }
                            else -> {
                                lifecycleScope.launch {
                                    val res = FirebaseHelper.changePassword(current, next)
                                    if (res.isSuccess) {
                                        Toast.makeText(requireContext(), R.string.password_changed, Toast.LENGTH_SHORT).show()
                                        dialog.dismiss()
                                    } else {
                                        Toast.makeText(
                                            requireContext(),
                                            res.exceptionOrNull()?.message ?: getString(R.string.error_generic),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun showUserManagementDialog() {
        lifecycleScope.launch {
            val result = FirebaseHelper.getUsersForModeration(200)
            val users = result.users
            val role = FirebaseHelper.resolveRole(FirebaseHelper.currentUser)
            if (result.error != null) {
                Toast.makeText(requireContext(), result.error, Toast.LENGTH_LONG).show()
            }
            if (users.isEmpty()) {
                Toast.makeText(requireContext(), getString(R.string.no_users_found), Toast.LENGTH_SHORT).show()
                return@launch
            }

            val names = users.map { u ->
                val active = if (FirebaseHelper.isRecentlyActive(u)) " · active" else ""
                "${u.displayName} (${u.getRoleEnum().displayName()})$active"
            }.toTypedArray()

            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.user_management))
                .setItems(names) { _, which ->
                    ModerationUiHelper.showQuickActions(
                        requireContext(), lifecycleScope, users[which], role,
                        includeFounder = FirebaseHelper.isFounderAccount(),
                        onSuccess = { loadCurrentUser() }
                    )
                }
                .setNegativeButton("Close", null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}