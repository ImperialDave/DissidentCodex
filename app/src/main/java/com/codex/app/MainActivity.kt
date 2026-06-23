package com.codex.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.codex.app.auth.AuthActivity
import com.codex.app.databinding.ActivityMainBinding
import com.codex.app.ui.BaseThemedActivity
import com.codex.app.ui.ChatsFragment
import com.codex.app.ui.CreatePostFragment
import com.codex.app.ui.FeedFragment
import com.codex.app.ui.FounderToolsActivity
import com.codex.app.ui.LeaderboardActivity
import com.codex.app.ui.ModToolsActivity
import com.codex.app.ui.ModerationManageActivity
import com.codex.app.utils.WindowInsetsHelper
import com.codex.app.ui.NotificationsFragment
import com.codex.app.ui.ProfileFragment
import com.codex.app.ui.SearchHubActivity
import com.codex.app.utils.FirebaseHelper
import com.codex.app.utils.NotificationHelper
import com.google.android.material.badge.BadgeDrawable
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class MainActivity : BaseThemedActivity() {

    companion object {
        private const val TAG = "MainActivity"
        const val EXTRA_OPEN_PROFILE = "open_profile"
        const val EXTRA_FEED_CATEGORY = "feed_category"
    }

    private lateinit var binding: ActivityMainBinding
    private var authListener: FirebaseAuth.AuthStateListener? = null
    private var unreadListener: ListenerRegistration? = null
    private var notificationBadge: BadgeDrawable? = null
    private var isPublishing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val fbUser = FirebaseHelper.auth.currentUser
        if (fbUser == null) {
            goToAuth()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowInsetsHelper.applyTopSafeArea(binding.toolbar)

        setupToolbarAndModMenu()
        setupBottomNav()
        setupNotificationBadge()

        if (savedInstanceState == null) {
            when {
                intent.getBooleanExtra(EXTRA_OPEN_PROFILE, false) -> {
                    binding.bottomNavigation.selectedItemId = R.id.nav_profile
                    loadFragment(ProfileFragment())
                }
                else -> openFeedWithOptionalCategory(intent.getStringExtra(EXTRA_FEED_CATEGORY))
            }
        }

        authListener = FirebaseAuth.AuthStateListener { auth ->
            if (auth.currentUser == null) {
                goToAuth()
            }
        }
        FirebaseHelper.addAuthStateListener(authListener!!)

        lifecycleScope.launch {
            val res = FirebaseHelper.loadCurrentUserAndCheckBan()
            if (res.isFailure) {
                val msg = res.exceptionOrNull()?.message.orEmpty()
                when {
                    msg.contains("banned", ignoreCase = true) -> {
                        showToast(msg)
                        goToAuth()
                    }
                    msg.contains("not signed in", ignoreCase = true) -> goToAuth()
                }
            } else {
                if (FirebaseHelper.isFounderEmail(FirebaseHelper.auth.currentUser?.email)) {
                    FirebaseHelper.syncFounderRole()
                }
                refreshModMenuIfNeeded()
            }
        }
    }

    private fun setupBottomNav() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_feed -> {
                    loadFragment(FeedFragment())
                    true
                }
                R.id.nav_chats -> {
                    loadFragment(ChatsFragment())
                    true
                }
                R.id.nav_create -> {
                    val role = FirebaseHelper.resolveRole(FirebaseHelper.currentUser)
                    if (!role.canPost()) {
                        Toast.makeText(this, getString(R.string.cannot_post_suspended), Toast.LENGTH_LONG).show()
                        false
                    } else {
                        loadFragment(CreatePostFragment())
                        true
                    }
                }
                R.id.nav_notifications -> {
                    loadFragment(NotificationsFragment())
                    true
                }
                R.id.nav_profile -> {
                    loadFragment(ProfileFragment())
                    true
                }
                else -> false
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val category = intent.getStringExtra(EXTRA_FEED_CATEGORY)
        if (!category.isNullOrBlank()) {
            intent.removeExtra(EXTRA_FEED_CATEGORY)
            binding.bottomNavigation.selectedItemId = R.id.nav_feed
            openFeedWithOptionalCategory(category)
        }
    }

    private fun openFeedWithOptionalCategory(category: String?) {
        binding.bottomNavigation.selectedItemId = R.id.nav_feed
        loadFragment(
            if (!category.isNullOrBlank()) FeedFragment.newInstance(category) else FeedFragment()
        )
    }

    private fun loadFragment(fragment: Fragment) {
        val tx = supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
        if (supportFragmentManager.isStateSaved) {
            tx.commitAllowingStateLoss()
        } else {
            tx.commit()
        }
    }

    private fun goToAuth() {
        startActivity(Intent(this, AuthActivity::class.java))
        finish()
    }

    private fun setupNotificationBadge() {
        val density = resources.displayMetrics.density
        notificationBadge = binding.bottomNavigation.getOrCreateBadge(R.id.nav_notifications).apply {
            isVisible = false
            maxCharacterCount = 3
            horizontalOffset = (2 * density).toInt()
            verticalOffset = (10 * density).toInt()
        }
        unreadListener = NotificationHelper.listenUnreadCount(
            onCount = { count -> runOnUiThread { updateNotificationBadge(count) } },
            onError = { /* badge optional */ }
        )
    }

    private fun updateNotificationBadge(count: Int) {
        val badge = notificationBadge ?: return
        if (count > 0) {
            badge.number = count.coerceAtMost(99)
            badge.isVisible = true
        } else {
            badge.clearNumber()
            badge.isVisible = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unreadListener?.remove()
        authListener?.let { FirebaseHelper.removeAuthStateListener(it) }
    }

    fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    /**
     * Publishes a post entirely from the Activity — no fragment callbacks that can crash
     * if the create screen is destroyed mid-request (the root cause of post-vs-comment crashes).
     */
    fun publishNewPost(
        title: String,
        body: String,
        category: String,
        imageUri: Uri?,
        remoteImageUrl: String? = null,
        mediaType: String? = null
    ) {
        if (isPublishing) return
        isPublishing = true

        lifecycleScope.launch {
            try {
                var imageUrl = remoteImageUrl
                var resolvedMediaType = mediaType
                if (imageUri != null) {
                    val uploadRes = FirebaseHelper.uploadMedia(imageUri, "post_images")
                    if (uploadRes.isFailure) {
                        showToast(uploadRes.exceptionOrNull()?.message ?: getString(R.string.image_upload_failed))
                        return@launch
                    }
                    imageUrl = uploadRes.getOrNull()?.url
                    resolvedMediaType = uploadRes.getOrNull()?.mediaType
                }

                val createRes = FirebaseHelper.createPost(
                    title, body, category.trim(), imageUrl, resolvedMediaType
                )
                if (createRes.isFailure) {
                    showToast(createRes.exceptionOrNull()?.message ?: "Failed to publish")
                    return@launch
                }

                // Best-effort category registry — never blocks or crashes publish
                try {
                    FirebaseHelper.resolveOrCreateCategory(category)
                } catch (e: Exception) {
                    Log.w(TAG, "Category registry skipped", e)
                }

                showToast(getString(R.string.post_created))
                showFeedAfterPublish()
            } catch (e: Exception) {
                Log.e(TAG, "publishNewPost failed", e)
                showToast(e.message ?: getString(R.string.error_generic))
            } finally {
                isPublishing = false
            }
        }
    }

    private fun showFeedAfterPublish() {
        binding.bottomNavigation.setOnItemSelectedListener(null)
        loadFragment(FeedFragment())
        binding.bottomNavigation.selectedItemId = R.id.nav_feed
        setupBottomNav()
    }

    private fun setupToolbarAndModMenu() {
        setSupportActionBar(binding.toolbar)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_toolbar_menu, menu)
        val role = FirebaseHelper.resolveRole(FirebaseHelper.currentUser)
        if (role.canModerate()) {
            menuInflater.inflate(R.menu.main_overflow_menu, menu)
            val modItem = menu.findItem(R.id.action_mod_tools)
            val founderItem = menu.findItem(R.id.action_founder_tools)
            if (FirebaseHelper.isFounderAccount()) {
                modItem?.isVisible = false
                founderItem?.isVisible = true
            } else {
                modItem?.isVisible = true
                founderItem?.isVisible = false
            }
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                startActivity(Intent(this, SearchHubActivity::class.java))
                true
            }
            R.id.action_leaderboard -> {
                startActivity(Intent(this, LeaderboardActivity::class.java))
                true
            }
            R.id.action_manage_topics -> {
                startActivity(Intent(this, ModerationManageActivity::class.java))
                true
            }
            R.id.action_mod_tools -> {
                startActivity(Intent(this, ModToolsActivity::class.java))
                true
            }
            R.id.action_founder_tools -> {
                startActivity(Intent(this, FounderToolsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun refreshModMenuIfNeeded() {
        invalidateOptionsMenu()
    }
}