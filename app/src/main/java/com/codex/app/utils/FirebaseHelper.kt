package com.codex.app.utils

import android.net.Uri
import android.util.Log
import com.codex.app.CodexApplication
import com.codex.app.models.BannedTopic
import com.codex.app.models.FeedHiddenTopic
import com.codex.app.models.ChatMessage
import com.codex.app.models.ChatRoom
import com.codex.app.models.Comment
import com.codex.app.models.FavoriteCategory
import com.codex.app.models.Friend
import com.codex.app.models.FriendRequest
import com.codex.app.models.LeaderboardData
import com.codex.app.models.LeaderboardEntry
import com.codex.app.models.MediaType
import com.codex.app.models.Post
import com.codex.app.models.PostCategory
import com.codex.app.models.Role
import com.codex.app.models.User
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import com.google.firebase.Timestamp
import kotlinx.coroutines.tasks.await
import java.util.UUID

object FirebaseHelper {
    private const val TAG = "FirebaseHelper"
    private const val USERS = "users"
    private const val POSTS = "posts"
    private const val COMMENTS = "comments"
    private const val CATEGORIES = "categories"
    private const val CHAT_ROOMS = "chatRooms"
    private const val MESSAGES = "messages"
    private const val FAVORITE_CHATS = "favoriteChats"
    private const val FAVORITE_CATEGORIES = "favoriteCategories"
    private const val FRIENDS = "friends"
    private const val FRIEND_REQUESTS = "friendRequests"
    private const val HIDDEN_TOPICS = "hiddenTopics"
    private const val FEED_HIDDEN_TOPICS = "feedHiddenTopics"
    private const val LIKED_POSTS = "likedPosts"
    private const val LIKED_COMMENTS = "likedComments"
    private const val MAX_CATEGORY = 40
    private const val MAX_CHAT_MESSAGE = 1000

    // Input limits for security/DoS prevention (Firestore has hard limits too)
    private const val MAX_TITLE = 120
    private const val MAX_BODY = 4000
    private const val MAX_COMMENT = 1000
    private const val MAX_NAME = 50
    private const val MAX_BIO = 300
    private const val MAX_FLAIR = 40
    private const val MAX_FAVORITE_CATEGORIES = 6

    const val ALL_CATEGORY_LABEL = "All"

    // Special founder email: ericdanielevans@gmail.com is the founder of the app with ALL permissions.
    // Always forced to FOUNDER role (highest, above ADMIN/MOD).
    private const val FOUNDER_EMAIL = "ericdanielevans@gmail.com"

    val auth: FirebaseAuth = Firebase.auth
    val db: FirebaseFirestore = Firebase.firestore
    val storage: FirebaseStorage = Firebase.storage

    // Current cached user (set internally or by profile refresh via internal visibility)
    var currentUser: User? = null
        internal set

    fun getCurrentFirebaseUser(): FirebaseUser? = auth.currentUser

    fun isFounderEmail(email: String?): Boolean =
        email?.equals(FOUNDER_EMAIL, ignoreCase = true) == true

    /** Founder is always recognized by email, even if Firestore role is stale (e.g. still ADMIN). */
    fun resolveRole(user: User?, emailHint: String? = null): Role {
        val email = user?.email?.takeIf { it.isNotBlank() }
            ?: emailHint?.takeIf { it.isNotBlank() }
            ?: auth.currentUser?.email
        if (isFounderEmail(email)) return Role.FOUNDER
        return user?.getRoleEnum() ?: Role.USER
    }

    fun isFounderAccount(user: User? = currentUser): Boolean {
        val email = user?.email?.takeIf { it.isNotBlank() } ?: auth.currentUser?.email
        return isFounderEmail(email) || user?.getRoleEnum()?.isFounder() == true
    }

    fun withResolvedRole(user: User, emailHint: String? = null): User {
        val role = resolveRole(user, emailHint)
        return if (user.getRoleEnum() == role) user else user.copy(role = role.name)
    }

    /** Role string stored in Firestore — rules validate against this, not client-resolved FOUNDER. */
    private suspend fun firestoreRoleForWrites(uid: String): String {
        return try {
            val snap = db.collection(USERS).document(uid).get().await()
            snap.getString("role")?.takeIf { it.isNotBlank() } ?: Role.USER.name
        } catch (e: Exception) {
            Log.w(TAG, "firestoreRoleForWrites fallback", e)
            Role.USER.name
        }
    }

    suspend fun registerUser(email: String, password: String, displayName: String): Result<User> {
        val name = displayName.trim()
        if (name.length > MAX_NAME) {
            return Result.failure(Exception("Display name too long (max $MAX_NAME chars)"))
        }
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user ?: return Result.failure(Exception("Registration failed"))

            val isFounder = email.equals(FOUNDER_EMAIL, ignoreCase = true)
            val initialRole = if (isFounder) Role.FOUNDER.name else Role.USER.name
            val user = User(
                uid = firebaseUser.uid,
                email = email,
                displayName = name.ifBlank { email.substringBefore("@") }.take(MAX_NAME),
                role = initialRole,
                createdAt = Timestamp.now(),
                lastActive = Timestamp.now()
            )

            db.collection(USERS).document(firebaseUser.uid).set(user).await()
            currentUser = withResolvedRole(user, email)
            Result.success(currentUser!!)
        } catch (e: Exception) {
            Log.e(TAG, "registerUser error", e)
            Result.failure(e)
        }
    }

    suspend fun loginUser(email: String, password: String): Result<User> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user ?: return Result.failure(Exception("Login failed"))
            loadCurrentUserAndCheckBanInternal(firebaseUser.uid, email)
        } catch (e: Exception) {
            Log.e(TAG, "loginUser error", e)
            Result.failure(e)
        }
    }

    // Called on app start / auto-login to re-validate ban status against possibly stale token.
    // Also refreshes currentUser cache (important for role changes between sessions).
    suspend fun loadCurrentUserAndCheckBan(): Result<User> {
        val fbUser = auth.currentUser ?: return Result.failure(Exception("Not signed in"))
        return try {
            loadCurrentUserAndCheckBanInternal(fbUser.uid, fbUser.email)
        } catch (e: Exception) {
            Log.e(TAG, "loadCurrentUserAndCheckBan error", e)
            Result.failure(e)
        }
    }

    private suspend fun loadCurrentUserAndCheckBanInternal(uid: String, emailHint: String?): Result<User> {
        val user = fetchUser(uid)
        if (user == null) {
            // Edge case: doc missing after auth (e.g. rules or manual delete). Create minimal as USER.
            val isFounder = (emailHint ?: "").equals(FOUNDER_EMAIL, ignoreCase = true)
            val newRole = if (isFounder) Role.FOUNDER.name else Role.USER.name
            val newUser = User(
                uid = uid,
                email = emailHint ?: "",
                displayName = (emailHint ?: "user").substringBefore("@"),
                role = newRole,
                createdAt = Timestamp.now(),
                lastActive = Timestamp.now()
            )
            db.collection(USERS).document(uid).set(newUser).await()
            val resolved = withResolvedRole(newUser, emailHint)
            currentUser = resolved
            return Result.success(resolved)
        }
        if (user.isBanned()) {
            auth.signOut()
            currentUser = null
            return Result.failure(Exception("Your account is banned."))
        }
        val finalUser = ensureFounderRole(uid, user, emailHint)
        currentUser = finalUser
        likedPostIdsCache = null
        updateLastActive(uid)
        return Result.success(finalUser)
    }

    suspend fun fetchUser(uid: String): User? {
        return try {
            val snap = db.collection(USERS).document(uid).get().await()
            val raw = snap.toObject(User::class.java)?.copy(uid = uid) ?: return null
            withResolvedRole(raw, auth.currentUser?.email)
        } catch (e: Exception) {
            Log.e(TAG, "fetchUser error", e)
            null
        }
    }

    /**
     * Founder email always resolves to FOUNDER in-app. Syncs to Firestore when rules permit.
     * Never silently drops founder access if DB still says ADMIN/USER.
     */
    private suspend fun ensureFounderRole(uid: String, user: User, emailHint: String?): User {
        val email = emailHint?.takeIf { it.isNotBlank() } ?: user.email
        if (!isFounderEmail(email)) return user

        var resolved = user.copy(role = Role.FOUNDER.name, email = email.ifBlank { user.email })
        if (user.getRoleEnum() == Role.FOUNDER) return resolved

        try {
            db.collection(USERS).document(uid).update("role", Role.FOUNDER.name).await()
            Log.i(TAG, "Synced FOUNDER role to Firestore for $email")
        } catch (e: Exception) {
            Log.w(TAG, "Founder Firestore sync failed — using client-side FOUNDER for $email", e)
            try {
                db.collection(USERS).document(uid).set(
                    mapOf("role" to Role.FOUNDER.name, "email" to email),
                    com.google.firebase.firestore.SetOptions.merge()
                ).await()
            } catch (e2: Exception) {
                Log.w(TAG, "Founder merge sync also failed", e2)
            }
        }
        return resolved
    }

    suspend fun syncFounderRole(): Result<User> {
        val fb = auth.currentUser ?: return Result.failure(Exception("Not signed in"))
        if (!isFounderEmail(fb.email)) {
            return Result.failure(Exception("Not the founder account."))
        }
        val user = fetchUser(fb.uid) ?: return Result.failure(Exception("Profile not found."))
        val synced = ensureFounderRole(fb.uid, user, fb.email)
        currentUser = synced
        return Result.success(synced)
    }

    private suspend fun updateLastActive(uid: String) {
        try {
            db.collection(USERS).document(uid)
                .update("lastActive", Timestamp.now()).await()
        } catch (_: Exception) {}
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        val fbUser = auth.currentUser ?: return Result.failure(Exception("Not logged in"))
        val email = fbUser.email ?: return Result.failure(Exception("Email login required"))
        if (newPassword.length < 6) {
            return Result.failure(Exception("New password must be at least 6 characters"))
        }
        return try {
            val credential = EmailAuthProvider.getCredential(email, currentPassword)
            fbUser.reauthenticate(credential).await()
            fbUser.updatePassword(newPassword).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "changePassword error", e)
            val msg = e.message.orEmpty()
            val friendly = when {
                msg.contains("wrong-password", ignoreCase = true) ||
                    msg.contains("invalid-credential", ignoreCase = true) ->
                    "Current password is incorrect."
                msg.contains("requires-recent-login", ignoreCase = true) ->
                    "Please log out and log back in, then try again."
                msg.contains("weak-password", ignoreCase = true) ->
                    "New password is too weak. Use at least 6 characters."
                else -> msg.ifBlank { "Could not change password." }
            }
            Result.failure(Exception(friendly))
        }
    }

    fun logout() {
        auth.signOut()
        currentUser = null
        likedPostIdsCache = null
    }

    suspend fun updateProfile(
        displayName: String? = null,
        bio: String? = null,
        photoUrl: String? = null,
        backgroundUrl: String? = null,
        flair: String? = null
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(Exception("Not logged in"))
        return try {
            val updates = mutableMapOf<String, Any>()
            displayName?.trim()?.takeIf { it.isNotBlank() }?.let {
                if (it.length > MAX_NAME) return Result.failure(Exception("Display name too long (max $MAX_NAME)"))
                updates["displayName"] = it
            }
            bio?.let {
                val trimmed = it.trim().take(MAX_BIO)
                updates["bio"] = trimmed
            }
            photoUrl?.let { updates["photoUrl"] = it }
            backgroundUrl?.let { updates["backgroundUrl"] = it }
            flair?.let {
                val trimmed = it.trim().take(MAX_FLAIR)
                updates["flair"] = trimmed
            }

            if (updates.isNotEmpty()) {
                db.collection(USERS).document(uid).update(updates).await()
            }
            currentUser = fetchUser(uid)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadMedia(uri: Uri, folder: String = "images"): Result<MediaUploadHelper.UploadResult> {
        val uid = auth.currentUser?.uid ?: return Result.failure(Exception("Not authenticated"))
        return MediaUploadHelper.uploadMedia(CodexApplication.instance, storage, uri, folder, uid)
    }

    suspend fun uploadCommentVideo(uri: Uri): Result<MediaUploadHelper.UploadResult> {
        val uid = auth.currentUser?.uid ?: return Result.failure(Exception("Not authenticated"))
        return MediaUploadHelper.uploadCommentVideo(CodexApplication.instance, storage, uri, uid)
    }

    suspend fun uploadImage(uri: Uri, folder: String = "images"): Result<String> {
        return uploadMedia(uri, folder).map { it.url }
    }

    suspend fun createPost(
        title: String,
        body: String,
        category: String,
        imageUrl: String? = null,
        mediaType: String? = null
    ): Result<Post> {
        val fbUser = auth.currentUser ?: return Result.failure(Exception("Not logged in"))
        // Always fetch fresh for permission check to catch remote role changes (suspend/ban) without restart
        val user = fetchUser(fbUser.uid) ?: currentUser ?: return Result.failure(Exception("User profile missing"))
        if (user != currentUser) currentUser = user

        val resolvedRole = resolveRole(user, fbUser.email)
        if (!resolvedRole.canPost()) {
            return Result.failure(Exception("You do not have permission to post."))
        }

        val t = title.trim()
        val b = body.trim()
        if (t.isBlank() || b.isBlank()) return Result.failure(Exception("Title and body are required"))
        if (t.length > MAX_TITLE) return Result.failure(Exception("Title too long (max $MAX_TITLE chars)"))
        if (b.length > MAX_BODY) return Result.failure(Exception("Body too long (max $MAX_BODY chars)"))

        return try {
            val postRef = db.collection(POSTS).document()
            val now = Timestamp.now()
            val authorName = user.displayName.ifBlank {
                fbUser.email?.substringBefore("@") ?: "User"
            }
            // Same write pattern as addComment() — data class .set(), which omits null fields.
            val resolvedMediaType = MediaType.resolve(mediaType, imageUrl)
            val post = Post(
                id = postRef.id,
                authorId = fbUser.uid,
                authorName = authorName,
                authorPhotoUrl = user.photoUrl,
                authorRole = firestoreRoleForWrites(fbUser.uid),
                title = t,
                body = b,
                imageUrl = imageUrl,
                mediaType = resolvedMediaType,
                category = category.trim(),
                createdAt = now,
                updatedAt = now
            )
            postRef.set(post).await()
            Result.success(post)
        } catch (e: Exception) {
            Log.e(TAG, "createPost error", e)
            Result.failure(e)
        }
    }

    // Get posts, optionally filtered by category. Latest first.
    // Client-side filter + larger fetch to avoid needing composite index on (category, createdAt).
    // Always populates .id from Firestore doc ID (was missing in list queries, breaking clicks/deletes in feed/profile/mod).
    suspend fun getPosts(
        category: String? = null,
        limit: Long = 50,
        includeHidden: Boolean = false
    ): List<Post> {
        return try {
            val snap = db.collection(POSTS)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(200) // larger to have enough after client filter
                .get().await()
            var posts = snap.documents.mapNotNull { doc -> doc.toPost() }
            if (!includeHidden) {
                posts = posts.filter { !it.hiddenFromFeed }
            }
            val effectiveCat = if (category.isNullOrBlank() || category == "All") null else category
            if (effectiveCat != null) {
                posts = posts.filter { it.category == effectiveCat }
            }
            posts.take(limit.toInt())
        } catch (e: Exception) {
            Log.e(TAG, "getPosts error", e)
            emptyList()
        }
    }

    suspend fun getPost(postId: String): Post? {
        return try {
            val snap = db.collection(POSTS).document(postId).get().await()
            snap.toPost()
        } catch (e: Exception) {
            Log.e(TAG, "getPost error", e)
            null
        }
    }

    suspend fun togglePostFeedVisibility(postId: String): Result<Boolean> {
        val uid = auth.currentUser?.uid ?: return Result.failure(Exception("Not logged in"))
        if (!resolveRole(currentUser).canModerate()) {
            return Result.failure(Exception("Moderator access required"))
        }
        val post = getPost(postId) ?: return Result.failure(Exception("Post not found"))
        val nextHidden = !post.hiddenFromFeed
        return try {
            val updates = mutableMapOf<String, Any>(
                "hiddenFromFeed" to nextHidden,
                "updatedAt" to Timestamp.now()
            )
            if (nextHidden) {
                updates["hiddenBy"] = uid
                updates["hiddenAt"] = Timestamp.now()
            } else {
                updates["hiddenBy"] = FieldValue.delete()
                updates["hiddenAt"] = FieldValue.delete()
            }
            db.collection(POSTS).document(postId).update(updates).await()
            Result.success(nextHidden)
        } catch (e: Exception) {
            Log.e(TAG, "togglePostFeedVisibility error", e)
            Result.failure(e)
        }
    }

    // Delete post (only author + mods/admins). Refreshes role from server for accuracy.
    suspend fun deletePost(postId: String, providedRole: Role? = null): Result<Unit> {
        return try {
            val post = getPost(postId) ?: return Result.failure(Exception("Post not found"))
            val isAuthor = post.authorId == auth.currentUser?.uid
            // Prefer fresh role lookup to avoid stale cache allowing/denying delete incorrectly
            val actorRole = providedRole
                ?: currentUser?.getRoleEnum()
                ?: fetchUser(auth.currentUser?.uid ?: "")?.getRoleEnum()
                ?: Role.USER
            if (!isAuthor && !actorRole.canModerate()) {
                return Result.failure(Exception("No permission to delete this post."))
            }
            db.collection(POSTS).document(postId).delete().await()
            // Cascade delete comments using batch for atomicity + reliability (prevents orphans on partial fail)
            val commentsSnap = db.collection(COMMENTS).whereEqualTo("postId", postId).get().await()
            if (commentsSnap.documents.isNotEmpty()) {
                val batch = db.batch()
                for (doc in commentsSnap.documents) {
                    batch.delete(doc.reference)
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private var likedPostIdsCache: Set<String>? = null
    private var likedCommentIdsCache: Set<String>? = null

    private fun likedPostsRef(uid: String) =
        db.collection(USERS).document(uid).collection(LIKED_POSTS)

    private fun likedCommentsRef(uid: String) =
        db.collection(USERS).document(uid).collection(LIKED_COMMENTS)

    suspend fun refreshLikedPostCache(): Set<String> {
        val uid = auth.currentUser?.uid ?: return emptySet<String>().also { likedPostIdsCache = it }
        return try {
            val snap = likedPostsRef(uid).limit(500).get().await()
            snap.documents.map { it.id }.toSet().also { likedPostIdsCache = it }
        } catch (e: Exception) {
            Log.e(TAG, "refreshLikedPostCache error", e)
            emptySet()
        }
    }

    fun getCachedLikedPostIds(): Set<String> = likedPostIdsCache ?: emptySet()

    suspend fun hasLikedPost(postId: String): Boolean {
        likedPostIdsCache?.let { return postId in it }
        val uid = auth.currentUser?.uid ?: return false
        return try {
            likedPostsRef(uid).document(postId).get().await().exists()
        } catch (e: Exception) {
            Log.e(TAG, "hasLikedPost error", e)
            false
        }
    }

    suspend fun toggleLikePost(postId: String): Result<Boolean> {
        return if (hasLikedPost(postId)) unlikePost(postId).map { false }
        else likePost(postId).map { true }
    }

    suspend fun likePost(postId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(Exception("Not logged in"))
        val user = fetchUser(uid) ?: currentUser
        if (user?.isBanned() == true || user?.isSuspended() == true) {
            return Result.failure(Exception("Restricted accounts cannot interact."))
        }
        val likeRef = likedPostsRef(uid).document(postId)
        return try {
            if (likeRef.get().await().exists()) {
                return Result.failure(Exception("You already liked this post."))
            }
            likeRef.set(mapOf("likedAt" to Timestamp.now())).await()
            try {
                db.collection(POSTS).document(postId)
                    .update("likeCount", FieldValue.increment(1)).await()
            } catch (countErr: Exception) {
                likeRef.delete().await()
                throw countErr
            }
            likedPostIdsCache = (likedPostIdsCache ?: emptySet()) + postId
            Result.success(Unit)
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                Log.w(TAG, "likedPosts rules not deployed — using legacy like increment")
                return legacyLikePost(postId)
            }
            Log.e(TAG, "likePost error", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "likePost error", e)
            Result.failure(e)
        }
    }

    suspend fun unlikePost(postId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(Exception("Not logged in"))
        val likeRef = likedPostsRef(uid).document(postId)
        return try {
            if (!likeRef.get().await().exists()) {
                return Result.failure(Exception("You have not liked this post."))
            }
            db.collection(POSTS).document(postId)
                .update("likeCount", FieldValue.increment(-1)).await()
            likeRef.delete().await()
            likedPostIdsCache = likedPostIdsCache?.minus(postId)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "unlikePost error", e)
            Result.failure(e)
        }
    }

    private suspend fun legacyLikePost(postId: String): Result<Unit> {
        return try {
            db.collection(POSTS).document(postId)
                .update("likeCount", FieldValue.increment(1)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "legacyLikePost error", e)
            Result.failure(e)
        }
    }

    suspend fun refreshLikedCommentCache(): Set<String> {
        val uid = auth.currentUser?.uid ?: return emptySet<String>().also { likedCommentIdsCache = it }
        return try {
            val snap = likedCommentsRef(uid).limit(500).get().await()
            snap.documents.map { it.id }.toSet().also { likedCommentIdsCache = it }
        } catch (e: Exception) {
            Log.e(TAG, "refreshLikedCommentCache error", e)
            emptySet()
        }
    }

    fun getCachedLikedCommentIds(): Set<String> = likedCommentIdsCache ?: emptySet()

    suspend fun hasLikedComment(commentId: String): Boolean {
        likedCommentIdsCache?.let { return commentId in it }
        val uid = auth.currentUser?.uid ?: return false
        return try {
            likedCommentsRef(uid).document(commentId).get().await().exists()
        } catch (e: Exception) {
            Log.e(TAG, "hasLikedComment error", e)
            false
        }
    }

    suspend fun toggleLikeComment(commentId: String): Result<Boolean> {
        return if (hasLikedComment(commentId)) unlikeComment(commentId).map { false }
        else likeComment(commentId).map { true }
    }

    suspend fun likeComment(commentId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(Exception("Not logged in"))
        val user = fetchUser(uid) ?: currentUser
        if (user?.isBanned() == true || user?.isSuspended() == true) {
            return Result.failure(Exception("Restricted accounts cannot interact."))
        }
        val likeRef = likedCommentsRef(uid).document(commentId)
        return try {
            if (likeRef.get().await().exists()) {
                return Result.failure(Exception("You already liked this comment."))
            }
            likeRef.set(mapOf("likedAt" to Timestamp.now())).await()
            try {
                db.collection(COMMENTS).document(commentId)
                    .update("likeCount", FieldValue.increment(1)).await()
            } catch (countErr: Exception) {
                likeRef.delete().await()
                throw countErr
            }
            likedCommentIdsCache = (likedCommentIdsCache ?: emptySet()) + commentId
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "likeComment error", e)
            Result.failure(e)
        }
    }

    suspend fun unlikeComment(commentId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(Exception("Not logged in"))
        val likeRef = likedCommentsRef(uid).document(commentId)
        return try {
            if (!likeRef.get().await().exists()) {
                return Result.failure(Exception("You have not liked this comment."))
            }
            db.collection(COMMENTS).document(commentId)
                .update("likeCount", FieldValue.increment(-1)).await()
            likeRef.delete().await()
            likedCommentIdsCache = likedCommentIdsCache?.minus(commentId)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "unlikeComment error", e)
            Result.failure(e)
        }
    }

    // --- User-created categories ---
    suspend fun getCategories(): List<PostCategory> {
        return try {
            val snap = db.collection(CATEGORIES).limit(200).get().await()
            snap.documents.mapNotNull { doc ->
                doc.toObject(PostCategory::class.java)?.copy(id = doc.id)
            }.sortedBy { it.name.lowercase() }
        } catch (e: Exception) {
            Log.e(TAG, "getCategories error", e)
            emptyList()
        }
    }

    suspend fun getCategoryNames(): List<String> = getCategories().map { it.name }

    private suspend fun collectTopicNames(): MutableSet<String> {
        val names = getCategoryNames().toMutableSet()
        try {
            val posts = db.collection(POSTS).limit(200).get().await()
            posts.documents.forEach { doc ->
                doc.getString("category")?.takeIf { it.isNotBlank() }?.let { names.add(it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "collectTopicNames post scan failed", e)
        }
        return names
    }

    suspend fun getAllTopicNames(): List<String> =
        collectTopicNames().sorted()

    suspend fun getFeedHiddenTopics(): List<FeedHiddenTopic> {
        return try {
            val snap = db.collection(FEED_HIDDEN_TOPICS).limit(200).get().await()
            snap.documents.mapNotNull { doc ->
                doc.toObject(FeedHiddenTopic::class.java)?.copy(id = doc.id)
            }.sortedByDescending { it.hiddenAt?.seconds ?: 0L }
        } catch (e: Exception) {
            Log.e(TAG, "getFeedHiddenTopics error", e)
            emptyList()
        }
    }

    suspend fun getFeedHiddenTopicNames(): Set<String> =
        getFeedHiddenTopics().mapNotNull { it.name.takeIf { n -> n.isNotBlank() }?.lowercase() }.toSet()

    suspend fun hideTopicFromBrowse(name: String): Result<Unit> {
        if (!resolveRole(currentUser).canModerate()) {
            return Result.failure(Exception("No permission to hide topics."))
        }
        val trimmed = name.trim()
        if (trimmed.isBlank() || trimmed == ALL_CATEGORY_LABEL) {
            return Result.failure(Exception("Cannot hide this topic."))
        }
        val uid = auth.currentUser?.uid ?: return Result.failure(Exception("Not logged in"))
        if (getFeedHiddenTopics().any { it.name.equals(trimmed, ignoreCase = true) }) {
            return Result.success(Unit)
        }
        return try {
            db.collection(FEED_HIDDEN_TOPICS).document().set(
                mapOf(
                    "name" to trimmed,
                    "hiddenBy" to uid,
                    "hiddenAt" to Timestamp.now()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "hideTopicFromBrowse error", e)
            Result.failure(e)
        }
    }

    suspend fun unhideTopicFromBrowse(id: String): Result<Unit> {
        if (!resolveRole(currentUser).canModerate()) {
            return Result.failure(Exception("No permission to unhide topics."))
        }
        if (id.isBlank()) return Result.failure(Exception("Invalid topic id."))
        return try {
            db.collection(FEED_HIDDEN_TOPICS).document(id).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "unhideTopicFromBrowse error", e)
            Result.failure(e)
        }
    }

    suspend fun getCreateCategoryNames(): List<String> =
        getFeedCategoryNames().filter { it != ALL_CATEGORY_LABEL }

    suspend fun getHiddenTopicNames(): Set<String> {
        return try {
            val snap = db.collection(HIDDEN_TOPICS).limit(200).get().await()
            snap.documents.mapNotNull { doc ->
                doc.getString("name")?.takeIf { it.isNotBlank() }?.lowercase()
            }.toSet()
        } catch (e: Exception) {
            Log.e(TAG, "getHiddenTopicNames error", e)
            emptySet()
        }
    }

    suspend fun getBannedTopics(): List<BannedTopic> {
        return try {
            val snap = db.collection(HIDDEN_TOPICS).limit(200).get().await()
            snap.documents.mapNotNull { doc ->
                val topic = doc.toObject(BannedTopic::class.java)?.copy(id = doc.id) ?: return@mapNotNull null
                val bannedAt = doc.getTimestamp("bannedAt") ?: doc.getTimestamp("hiddenAt")
                topic.copy(bannedAt = bannedAt ?: topic.bannedAt, hiddenAt = bannedAt ?: topic.hiddenAt)
            }.sortedByDescending { it.bannedAt?.seconds ?: it.hiddenAt?.seconds ?: 0L }
        } catch (e: Exception) {
            Log.e(TAG, "getBannedTopics error", e)
            emptyList()
        }
    }

    suspend fun isTopicBanned(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return false
        return getHiddenTopicNames().contains(trimmed.lowercase())
    }

    /** @deprecated Use [isTopicBanned]; kept for call-site clarity. */
    suspend fun isTopicHidden(name: String): Boolean = isTopicBanned(name)

    suspend fun banTopic(name: String, @Suppress("UNUSED_PARAMETER") categoryId: String? = null): Result<Unit> {
        if (!resolveRole(currentUser).canModerate()) {
            return Result.failure(Exception("No permission to ban topics."))
        }
        val trimmed = name.trim()
        if (trimmed.isBlank() || trimmed == ALL_CATEGORY_LABEL) {
            return Result.failure(Exception("Cannot ban this topic."))
        }
        if (getBannedTopics().any { it.name.equals(trimmed, ignoreCase = true) }) {
            return Result.success(Unit)
        }
        return try {
            db.collection(HIDDEN_TOPICS).document().set(
                mapOf(
                    "name" to trimmed,
                    "bannedAt" to Timestamp.now()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "banTopic error", e)
            Result.failure(e)
        }
    }

    suspend fun unbanTopic(id: String): Result<Unit> {
        if (!resolveRole(currentUser).canModerate()) {
            return Result.failure(Exception("No permission to unban topics."))
        }
        if (id.isBlank()) return Result.failure(Exception("Invalid topic id."))
        return try {
            db.collection(HIDDEN_TOPICS).document(id).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "unbanTopic error", e)
            Result.failure(e)
        }
    }

    /** Names for feed filter chips: "All" + every category + any categories used on posts. */
    suspend fun getFeedCategoryNames(includeFeedHidden: Boolean = false): List<String> {
        val banned = getHiddenTopicNames()
        val feedHidden = if (includeFeedHidden) emptySet() else getFeedHiddenTopicNames()
        val visible = collectTopicNames()
            .filter { !banned.contains(it.lowercase()) }
            .filter { includeFeedHidden || !feedHidden.contains(it.lowercase()) }
            .sorted()
        return listOf(ALL_CATEGORY_LABEL) + visible
    }

    suspend fun deleteTopicAndCategoryFully(
        categoryName: String,
        categoryId: String? = null
    ): Result<Unit> {
        if (!resolveRole(currentUser).canModerate()) {
            return Result.failure(Exception("No permission to delete topics."))
        }
        val trimmed = categoryName.trim()
        if (trimmed.isBlank() || trimmed == ALL_CATEGORY_LABEL) {
            return Result.failure(Exception("Cannot delete this category."))
        }
        val modRole = resolveRole(currentUser)
        return try {
            val categories = getCategories()
            val resolvedId = categoryId?.takeIf { it.isNotBlank() }
                ?: categories.find { it.name.equals(trimmed, ignoreCase = true) }?.id
                ?: resolveTopicCategoryId(trimmed, categories)

            val postsSnap = db.collection(POSTS).limit(500).get().await()
            for (doc in postsSnap.documents) {
                val cat = doc.getString("category") ?: continue
                if (cat.equals(trimmed, ignoreCase = true)) {
                    deletePost(doc.id, modRole).getOrThrow()
                }
            }

            val roomId = ChatRoom.topicRoomId(resolvedId)
            if (getChatRoom(roomId) != null) {
                deleteTopicRoom(roomId).getOrThrow()
            }

            categories.find { it.name.equals(trimmed, ignoreCase = true) }?.let { match ->
                db.collection(CATEGORIES).document(match.id).delete().await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "deleteTopicAndCategoryFully error", e)
            Result.failure(e)
        }
    }

    suspend fun deleteCategory(categoryId: String, deleteTopicChat: Boolean = true): Result<Unit> {
        val categories = getCategories()
        val match = categories.find { it.id == categoryId }
            ?: return Result.failure(Exception("Category not found."))
        return deleteTopicAndCategoryFully(match.name, categoryId)
    }

    suspend fun deleteCategoryByName(name: String): Result<Unit> {
        val trimmed = name.trim()
        val categories = getCategories()
        val match = categories.find { it.name.equals(trimmed, ignoreCase = true) }
        val catId = match?.id ?: resolveTopicCategoryId(trimmed, categories)
        return deleteTopicAndCategoryFully(trimmed, catId)
    }

    suspend fun getPostCountByCategory(): Map<String, Long> {
        val counts = mutableMapOf<String, Long>()
        return try {
            val posts = db.collection(POSTS).limit(500).get().await()
            posts.documents.forEach { doc ->
                val cat = doc.getString("category")?.takeIf { it.isNotBlank() } ?: return@forEach
                counts[cat] = (counts[cat] ?: 0L) + 1L
            }
            counts
        } catch (e: Exception) {
            Log.e(TAG, "getPostCountByCategory error", e)
            counts
        }
    }

    suspend fun getLeaderboardData(limit: Int = 20): LeaderboardData {
        val banned = getHiddenTopicNames()
        val feedHidden = getFeedHiddenTopicNames()
        val postCounts = getPostCountByCategory()
        // Only query rooms the user can read (topic + own DMs). An unfiltered query fails
        // when any inaccessible DM is in the result set.
        val rooms = try {
            getChatRoomsForInbox()
        } catch (e: Exception) {
            Log.e(TAG, "getLeaderboardData rooms error", e)
            emptyList()
        }

        val topTopics = rooms
            .filter { it.isTopic() }
            .filter { room ->
                val name = room.topicName?.takeIf { it.isNotBlank() } ?: room.title
                val key = name.lowercase()
                !banned.contains(key) && !feedHidden.contains(key)
            }
            .map { room ->
                val name = room.topicName?.takeIf { it.isNotBlank() } ?: room.title
                val postCount = postCounts[name] ?: 0L
                LeaderboardEntry(
                    title = room.title,
                    roomId = room.id,
                    messageCount = room.messageCount,
                    postCount = postCount,
                    score = room.messageCount * 2 + postCount,
                    lastMessageAt = room.lastMessageAt,
                    isTopic = true
                )
            }
            .sortedWith(
                compareByDescending<LeaderboardEntry> { it.score }
                    .thenByDescending { it.lastMessageAt?.seconds ?: 0L }
            )
            .take(limit)
            .mapIndexed { index, entry -> entry.copy(rank = index + 1) }

        val topChats = rooms
            .map { room ->
                LeaderboardEntry(
                    title = room.title,
                    roomId = room.id,
                    messageCount = room.messageCount,
                    lastMessageAt = room.lastMessageAt,
                    isTopic = room.isTopic()
                )
            }
            .sortedWith(
                compareByDescending<LeaderboardEntry> { it.messageCount }
                    .thenByDescending { it.lastMessageAt?.seconds ?: 0L }
            )
            .take(limit)
            .mapIndexed { index, entry -> entry.copy(rank = index + 1) }

        return LeaderboardData(topTopics = topTopics, topChats = topChats)
    }

    suspend fun getChessLeaderboard(limit: Int = 50): List<com.codex.app.models.ChessLeaderboardEntry> {
        return try {
            val snap = db.collection(USERS)
                .orderBy("chessElo", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()
            snap.documents
                .mapNotNull { doc ->
                    val user = doc.toObject(User::class.java) ?: return@mapNotNull null
                    if (user.chessGamesPlayed <= 0) return@mapNotNull null
                    com.codex.app.models.ChessLeaderboardEntry(
                        uid = doc.id,
                        displayName = user.displayName.ifBlank { "Player" },
                        photoUrl = user.photoUrl,
                        elo = user.chessElo,
                        wins = user.chessWins,
                        losses = user.chessLosses,
                        draws = user.chessDraws
                    )
                }
                .mapIndexed { index, entry -> entry.copy(rank = index + 1) }
        } catch (e: Exception) {
            Log.e(TAG, "getChessLeaderboard error", e)
            emptyList()
        }
    }

    suspend fun createCategory(name: String): Result<PostCategory> {
        val uid = auth.currentUser?.uid ?: return Result.failure(Exception("Not logged in"))
        val trimmed = name.trim()
        if (trimmed.length < 2) return Result.failure(Exception("Category name too short (min 2 chars)"))
        if (trimmed.length > MAX_CATEGORY) return Result.failure(Exception("Category name too long (max $MAX_CATEGORY)"))
        val normalized = trimmed.lowercase()
        return try {
            getCategories().find { it.name.equals(trimmed, ignoreCase = true) }?.let {
                return Result.success(it)
            }
            try {
                val existing = db.collection(CATEGORIES)
                    .whereEqualTo("normalizedName", normalized)
                    .limit(1)
                    .get().await()
                if (!existing.isEmpty) {
                    val doc = existing.documents.first()
                    val cat = doc.toObject(PostCategory::class.java)?.copy(id = doc.id)
                        ?: return Result.failure(Exception("Category exists but could not be read"))
                    return Result.success(cat)
                }
            } catch (queryErr: Exception) {
                Log.w(TAG, "normalizedName query failed, continuing create", queryErr)
            }
            val ref = db.collection(CATEGORIES).document()
            val now = Timestamp.now()
            ref.set(
                hashMapOf(
                    "name" to trimmed,
                    "normalizedName" to normalized,
                    "createdBy" to uid,
                    "createdAt" to now
                )
            ).await()
            val category = PostCategory(id = ref.id, name = trimmed, createdBy = uid, createdAt = now)
            try {
                ensureTopicRoomForCategory(category)
            } catch (e: Exception) {
                Log.w(TAG, "Topic room seed skipped", e)
            }
            Result.success(category)
        } catch (e: Exception) {
            Log.e(TAG, "createCategory error", e)
            Result.failure(e)
        }
    }

    suspend fun resolveOrCreateCategory(name: String): Result<String> {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return Result.failure(Exception("Category is required"))
        val existing = getCategories().find { it.name.equals(trimmed, ignoreCase = true) }
        if (existing != null) {
            try {
                ensureTopicRoomForCategory(existing)
            } catch (e: Exception) {
                Log.w(TAG, "Topic room seed skipped for existing category", e)
            }
            return Result.success(existing.name)
        }
        val created = createCategory(trimmed)
        if (created.isSuccess) return Result.success(created.getOrThrow().name)
        // Categories collection may be blocked by rules; still allow the post with this label.
        Log.w(TAG, "Category registry write failed, using name on post", created.exceptionOrNull())
        try {
            getOrCreateTopicRoomByName(trimmed)
        } catch (e: Exception) {
            Log.w(TAG, "Topic room seed skipped for post-only category", e)
        }
        return Result.success(trimmed)
    }

    // Comments — no orderBy with whereEqualTo (avoids composite index failures)
    suspend fun getComments(postId: String): List<Comment> {
        return try {
            val snap = db.collection(COMMENTS)
                .whereEqualTo("postId", postId)
                .get().await()
            snap.documents.mapNotNull { doc ->
                doc.toObject(Comment::class.java)?.copy(id = doc.id)
            }.sortedBy { it.createdAt?.seconds ?: 0L }
        } catch (e: Exception) {
            Log.e(TAG, "getComments error", e)
            emptyList()
        }
    }

    suspend fun getCommentsByUser(uid: String): List<Comment> {
        return try {
            val snap = db.collection(COMMENTS)
                .whereEqualTo("authorId", uid)
                .get().await()
            snap.documents.mapNotNull { doc ->
                doc.toObject(Comment::class.java)?.copy(id = doc.id)
            }.sortedByDescending { it.createdAt?.seconds ?: 0L }
        } catch (e: Exception) {
            Log.e(TAG, "getCommentsByUser error", e)
            emptyList()
        }
    }

    suspend fun addComment(
        postId: String,
        text: String,
        imageUrl: String? = null,
        mediaType: String? = null,
        parentCommentId: String? = null,
        replyToAuthorName: String? = null
    ): Result<Comment> {
        val fbUser = auth.currentUser ?: return Result.failure(Exception("Not logged in"))
        val user = fetchUser(fbUser.uid) ?: currentUser ?: return Result.failure(Exception("Profile missing"))
        if (user != currentUser) currentUser = user

        if (!user.canComment()) {
            return Result.failure(Exception("You cannot comment at this time."))
        }

        val t = text.trim()
        if (t.isBlank() && imageUrl.isNullOrBlank()) {
            return Result.failure(Exception("Comment cannot be empty"))
        }
        if (t.length > MAX_COMMENT) return Result.failure(Exception("Comment too long (max $MAX_COMMENT chars)"))

        return try {
            val commentRef = db.collection(COMMENTS).document()
            val resolvedMediaType = MediaType.resolve(mediaType, imageUrl)
            val comment = Comment(
                id = commentRef.id,
                postId = postId,
                parentCommentId = parentCommentId?.takeIf { it.isNotBlank() },
                replyToAuthorName = replyToAuthorName?.takeIf { it.isNotBlank() },
                authorId = fbUser.uid,
                authorName = user.displayName,
                authorPhotoUrl = user.photoUrl,
                authorRole = firestoreRoleForWrites(fbUser.uid),
                text = t,
                imageUrl = imageUrl,
                mediaType = resolvedMediaType,
                likeCount = 0,
                createdAt = Timestamp.now()
            )
            commentRef.set(comment).await()

            // Best-effort count bump — comment is already saved if this fails (e.g. stale rules).
            try {
                db.collection(POSTS).document(postId)
                    .update("commentCount", FieldValue.increment(1)).await()
            } catch (countErr: Exception) {
                Log.w(TAG, "commentCount increment failed (comment saved)", countErr)
            }

            Result.success(comment)
        } catch (e: Exception) {
            Log.e(TAG, "addComment error", e)
            Result.failure(e)
        }
    }

    data class ModerationStats(
        val total: Int,
        val activeRecently: Int,
        val members: Int,
        val mods: Int,
        val admins: Int,
        val founders: Int,
        val suspended: Int,
        val banned: Int
    )

    private const val ACTIVE_USER_WINDOW_SECONDS = 24 * 60 * 60L

    // Moderation actions - mods, admins, and founder
    suspend fun updateUserRole(targetUid: String, newRole: Role, actorRole: Role): Result<Unit> {
        val effectiveActor = if (isFounderAccount()) Role.FOUNDER else actorRole
        if (!effectiveActor.canModerate()) {
            return Result.failure(Exception("Insufficient permissions."))
        }
        val target = fetchUser(targetUid)
        if (target != null && isFounderEmail(target.email) && newRole != Role.FOUNDER) {
            return Result.failure(Exception("The founder account cannot be demoted."))
        }
        if (newRole == Role.FOUNDER && !effectiveActor.isFounder()) {
            return Result.failure(Exception("Only the founder can assign the Founder role."))
        }
        if (newRole == Role.ADMIN && effectiveActor != Role.ADMIN && !effectiveActor.isFounder()) {
            return Result.failure(Exception("Only admins can promote other admins."))
        }
        return try {
            db.collection(USERS).document(targetUid)
                .update("role", newRole.name).await()
            if (targetUid == auth.currentUser?.uid) {
                // If self role changed (e.g. demoted), refresh cache immediately
                currentUser = fetchUser(targetUid)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUserStatus(targetUid: String, role: Role): Result<Unit> {
        val actor = currentUser ?: return Result.failure(Exception("No actor"))
        return updateUserRole(targetUid, role, actor.getRoleEnum())
    }

    data class UserListResult(val users: List<User>, val error: String? = null)

    /**
     * Fetch users for moderation. No orderBy — Firestore ordered queries drop docs missing
     * that field, which caused empty/broken mod user lists.
     */
    suspend fun getUsersForModeration(limit: Long = 300): UserListResult {
        return try {
            val snap = db.collection(USERS).limit(limit).get().await()
            val users = snap.documents.mapNotNull { d ->
                d.toObject(User::class.java)?.copy(uid = d.id)?.let { withResolvedRole(it) }
            }.sortedByDescending { it.lastActive?.seconds ?: it.createdAt?.seconds ?: 0L }
            UserListResult(users)
        } catch (e: Exception) {
            Log.e(TAG, "getUsersForModeration error", e)
            UserListResult(emptyList(), e.message ?: "Failed to load users. Check moderator permissions.")
        }
    }

    suspend fun getAllUsers(limit: Long = 100): List<User> = getUsersForModeration(limit).users

    suspend fun getUsersForMessaging(limit: Long = 150): List<User> {
        return getUsersForModeration(limit).users.filter { it.getRoleEnum().isActive() }
    }

    fun isRecentlyActive(user: User, windowSeconds: Long = ACTIVE_USER_WINDOW_SECONDS): Boolean {
        val last = user.lastActive?.seconds ?: return false
        val now = System.currentTimeMillis() / 1000
        return last >= now - windowSeconds
    }

    fun hasActivityData(user: User): Boolean = user.lastActive != null

    fun filterUsers(
        users: List<User>,
        query: String,
        activeOnly: Boolean = false,
        roleFilter: Role? = null
    ): List<User> {
        var result = users
        if (activeOnly) {
            result = result.filter { isRecentlyActive(it) }
        }
        if (roleFilter != null) {
            result = result.filter { it.getRoleEnum() == roleFilter }
        }
        val q = query.trim().lowercase()
        if (q.isNotEmpty()) {
            result = result.filter { u ->
                u.displayName.lowercase().contains(q) ||
                    u.email.lowercase().contains(q) ||
                    u.uid.lowercase().contains(q) ||
                    u.bio.lowercase().contains(q)
            }
        }
        return result
    }

    fun computeModerationStats(users: List<User>): ModerationStats {
        var active = 0
        var members = 0
        var mods = 0
        var admins = 0
        var founders = 0
        var suspended = 0
        var banned = 0
        for (u in users) {
            if (isRecentlyActive(u)) active++
            when (u.getRoleEnum()) {
                Role.USER -> members++
                Role.MOD -> mods++
                Role.ADMIN -> admins++
                Role.FOUNDER -> founders++
                Role.SUSPENDED -> suspended++
                Role.BANNED -> banned++
            }
        }
        return ModerationStats(
            total = users.size,
            activeRecently = active,
            members = members,
            mods = mods,
            admins = admins,
            founders = founders,
            suspended = suspended,
            banned = banned
        )
    }

    suspend fun getRecentComments(limit: Long = 40): List<Comment> {
        return try {
            val snap = db.collection(COMMENTS).limit(150).get().await()
            snap.documents.mapNotNull { doc ->
                doc.toObject(Comment::class.java)?.copy(id = doc.id)
            }.sortedByDescending { it.createdAt?.seconds ?: 0L }.take(limit.toInt())
        } catch (e: Exception) {
            Log.e(TAG, "getRecentComments error", e)
            emptyList()
        }
    }

    suspend fun deleteComment(commentId: String, postId: String): Result<Unit> {
        val actorRole = currentUser?.getRoleEnum()
            ?: fetchUser(auth.currentUser?.uid ?: "")?.getRoleEnum()
            ?: Role.USER
        if (!actorRole.canModerate() && !actorRole.isFounder()) {
            return Result.failure(Exception("No permission to delete comments."))
        }
        return try {
            db.collection(COMMENTS).document(commentId).delete().await()
            db.collection(POSTS).document(postId)
                .update("commentCount", FieldValue.increment(-1)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "deleteComment error", e)
            Result.failure(e)
        }
    }

    // For feed: get posts by a specific user
    // Populates .id (critical for clicks/deletes from profile history and mod views).
    suspend fun getPostsByUser(uid: String): List<Post> {
        return try {
            val snap = db.collection(POSTS)
                .whereEqualTo("authorId", uid)
                .get().await()
            snap.documents.mapNotNull { doc -> doc.toPost() }
                .sortedByDescending { it.createdAt?.seconds ?: 0L }
        } catch (e: Exception) {
            Log.e(TAG, "getPostsByUser error", e)
            emptyList()
        }
    }

    // Listen to auth state changes (used in activities)
    fun addAuthStateListener(listener: FirebaseAuth.AuthStateListener) {
        auth.addAuthStateListener(listener)
    }

    fun removeAuthStateListener(listener: FirebaseAuth.AuthStateListener) {
        auth.removeAuthStateListener(listener)
    }

    /** Maps Firestore docs to Post, including legacy documents that used `role` instead of `authorRole`. */
    private fun DocumentSnapshot.toPost(): Post? {
        val base = toObject(Post::class.java) ?: return null
        val role = base.authorRole.takeIf { it.isNotBlank() && it != Role.USER.name }
            ?: getString("role")
            ?: base.authorRole
        return base.copy(id = id, authorRole = role)
    }

    private fun DocumentSnapshot.toChatRoom(): ChatRoom? {
        return toObject(ChatRoom::class.java)?.copy(id = id)
    }

    private fun DocumentSnapshot.toChatMessage(): ChatMessage? {
        return toObject(ChatMessage::class.java)?.copy(id = id)
    }

    // --- Live chat ---

    private suspend fun fetchChatRoomDoc(roomId: String): ChatRoom? {
        return try {
            val snap = db.collection(CHAT_ROOMS).document(roomId).get().await()
            if (!snap.exists()) null else snap.toChatRoom()
        } catch (e: Exception) {
            Log.e(TAG, "fetchChatRoomDoc error", e)
            null
        }
    }

    suspend fun getChatRoom(roomId: String): ChatRoom? {
        val room = fetchChatRoomDoc(roomId)
        if (room != null) return room
        if (roomId.startsWith("dm_")) {
            return repairDmMembershipByRoomId(roomId).getOrNull()
        }
        return null
    }

    /** Parse dm_{uidA}_{uidB} and repair memberIds when the array is missing or stale. */
    private fun parseDmMemberIds(roomId: String, uid: String): List<String>? {
        if (!roomId.startsWith("dm_")) return null
        val parts = roomId.removePrefix("dm_").split("_").filter { it.isNotBlank() }
        if (parts.size != 2 || uid !in parts) return null
        return parts.sorted()
    }

    suspend fun repairDmMembershipByRoomId(roomId: String): Result<ChatRoom> {
        val uid = auth.currentUser?.uid ?: return Result.failure(Exception("Not logged in"))
        val members = parseDmMemberIds(roomId, uid)
            ?: return Result.failure(Exception("You are not a participant in this conversation."))
        return try {
            db.collection(CHAT_ROOMS).document(roomId)
                .update("memberIds", members)
                .await()
            val room = fetchChatRoomDoc(roomId)
                ?: return Result.failure(Exception("Could not load conversation after repair."))
            Result.success(room)
        } catch (e: Exception) {
            Log.e(TAG, "repairDmMembershipByRoomId error", e)
            Result.failure(
                if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    mapFirestoreError(e)
                } else {
                    e
                }
            )
        }
    }

    /** Stable category id for topic rooms: Firestore doc id when registered, else normalized slug. */
    fun resolveTopicCategoryId(categoryName: String, categories: List<PostCategory> = emptyList()): String {
        val match = categories.find { it.name.equals(categoryName, ignoreCase = true) }
        if (match != null) return match.id
        return categoryName.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
    }

    suspend fun getOrCreateTopicRoom(categoryId: String, categoryName: String): Result<ChatRoom> {
        val uid = auth.currentUser?.uid ?: return Result.failure(Exception("Not logged in"))
        if (isTopicBanned(categoryName)) {
            return Result.failure(Exception("This topic is banned by moderators."))
        }
        val roomId = ChatRoom.topicRoomId(categoryId)
        return try {
            val existing = getChatRoom(roomId)
            if (existing != null) return Result.success(existing)

            val now = Timestamp.now()
            val ref = db.collection(CHAT_ROOMS).document(roomId)
            ref.set(
                hashMapOf(
                    "type" to ChatRoom.TYPE_TOPIC,
                    "title" to categoryName,
                    "topicId" to categoryId,
                    "topicName" to categoryName,
                    "memberIds" to emptyList<String>(),
                    "createdBy" to uid,
                    "createdAt" to now,
                    "lastMessageAt" to now,
                    "lastMessagePreview" to "",
                    "lastMessageAuthorId" to "",
                    "messageCount" to 0L
                )
            ).await()
            Result.success(
                ChatRoom(
                    id = roomId,
                    type = ChatRoom.TYPE_TOPIC,
                    title = categoryName,
                    topicId = categoryId,
                    topicName = categoryName,
                    memberIds = emptyList(),
                    createdBy = uid,
                    createdAt = now,
                    lastMessageAt = now
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "getOrCreateTopicRoom error", e)
            Result.failure(e)
        }
    }

    suspend fun getOrCreateTopicRoomByName(categoryName: String): Result<ChatRoom> {
        val categories = getCategories()
        val catId = resolveTopicCategoryId(categoryName, categories)
        return getOrCreateTopicRoom(catId, categoryName)
    }

    suspend fun getOrCreateDmRoom(otherUserId: String): Result<ChatRoom> {
        val fbUser = auth.currentUser ?: return Result.failure(Exception("Not logged in"))
        if (otherUserId == fbUser.uid) {
            return Result.failure(Exception("Cannot message yourself."))
        }
        val roomId = ChatRoom.dmRoomId(fbUser.uid, otherUserId)
        return try {
            val existing = getChatRoom(roomId)
            if (existing != null) {
                return ensureDmMembership(existing)
            }

            val other = fetchUser(otherUserId)
                ?: return Result.failure(Exception("User not found."))
            val members = listOf(fbUser.uid, otherUserId).sorted()
            val now = Timestamp.now()
            val title = other.displayName.ifBlank { other.email.substringBefore("@") }
            db.collection(CHAT_ROOMS).document(roomId).set(
                hashMapOf(
                    "type" to ChatRoom.TYPE_DM,
                    "title" to title,
                    "memberIds" to members,
                    "createdBy" to fbUser.uid,
                    "createdAt" to now,
                    "lastMessageAt" to now,
                    "lastMessagePreview" to "Started a conversation",
                    "lastMessageAuthorId" to "",
                    "messageCount" to 0L
                )
            ).await()
            Result.success(
                ChatRoom(
                    id = roomId,
                    type = ChatRoom.TYPE_DM,
                    title = title,
                    memberIds = members,
                    createdBy = fbUser.uid,
                    createdAt = now,
                    lastMessageAt = now,
                    lastMessagePreview = "Started a conversation"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "getOrCreateDmRoom error", e)
            val repaired = repairDmMembershipByRoomId(roomId)
            if (repaired.isSuccess) repaired
            else Result.failure(
                if (e is FirebaseFirestoreException) mapFirestoreError(e) else e
            )
        }
    }

    suspend fun ensureDmMembership(room: ChatRoom): Result<ChatRoom> {
        val uid = auth.currentUser?.uid ?: return Result.failure(Exception("Not logged in"))
        if (!room.isDm()) return Result.success(room)
        if (room.memberIds.contains(uid)) return Result.success(room)
        return repairDmMembershipByRoomId(room.id)
    }

    suspend fun prepareChatRoomAccess(roomId: String): Result<ChatRoom> {
        return try {
            var room = getChatRoom(roomId)
            if (room == null && roomId.startsWith("dm_")) {
                val repaired = repairDmMembershipByRoomId(roomId)
                if (repaired.isSuccess) room = repaired.getOrThrow()
            }
            room ?: return Result.failure(Exception("This chat is no longer available."))
            if (room.isDm()) {
                val repaired = ensureDmMembership(room)
                if (repaired.isFailure) return Result.failure(
                    repaired.exceptionOrNull() ?: Exception("Cannot access this chat.")
                )
                Result.success(repaired.getOrThrow())
            } else {
                Result.success(room)
            }
        } catch (e: FirebaseFirestoreException) {
            Log.e(TAG, "prepareChatRoomAccess error", e)
            Result.failure(mapFirestoreError(e))
        } catch (e: Exception) {
            Log.e(TAG, "prepareChatRoomAccess error", e)
            Result.failure(e)
        }
    }

    fun mapFirestoreError(e: FirebaseFirestoreException): Exception {
        return when (e.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                Exception("You don't have access to this chat.")
            FirebaseFirestoreException.Code.NOT_FOUND ->
                Exception("This chat is no longer available.")
            else -> Exception(e.message ?: "Could not load chat.")
        }
    }

    suspend fun ensureTopicRoomForCategory(category: PostCategory) {
        try {
            getOrCreateTopicRoom(category.id, category.name)
        } catch (e: Exception) {
            Log.w(TAG, "ensureTopicRoomForCategory failed", e)
        }
    }

    suspend fun getChatRoomsForInbox(): List<ChatRoom> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        return try {
            val topicSnap = db.collection(CHAT_ROOMS)
                .whereEqualTo("type", ChatRoom.TYPE_TOPIC)
                .limit(100)
                .get().await()
            val dmSnap = db.collection(CHAT_ROOMS)
                .whereEqualTo("type", ChatRoom.TYPE_DM)
                .whereArrayContains("memberIds", uid)
                .limit(50)
                .get().await()

            val rooms = mutableListOf<ChatRoom>()
            topicSnap.documents.forEach { doc ->
                doc.toChatRoom()?.let { rooms.add(it) }
            }
            dmSnap.documents.forEach { doc ->
                doc.toChatRoom()?.let { rooms.add(it) }
            }
            rooms.sortedByDescending { it.lastMessageAt?.seconds ?: 0L }
        } catch (e: Exception) {
            Log.e(TAG, "getChatRoomsForInbox error", e)
            emptyList()
        }
    }

    data class ChatRoomsListenerHandle(
        private val topicReg: ListenerRegistration,
        private val dmReg: ListenerRegistration
    ) {
        fun remove() {
            topicReg.remove()
            dmReg.remove()
        }
    }

    fun listenChatRooms(
        onUpdate: (List<ChatRoom>) -> Unit,
        onError: ((Exception) -> Unit)? = null
    ): ChatRoomsListenerHandle? {
        val uid = auth.currentUser?.uid ?: return null
        var topicRooms = emptyList<ChatRoom>()
        var dmRooms = emptyList<ChatRoom>()

        fun emit() {
            val merged = (topicRooms + dmRooms)
                .distinctBy { it.id }
                .sortedByDescending { it.lastMessageAt?.seconds ?: 0L }
            onUpdate(merged)
        }

        fun reportError(source: String, err: Exception) {
            Log.e(TAG, "listenChatRooms $source error", err)
            val mapped = if (err is FirebaseFirestoreException) {
                mapFirestoreError(err)
            } else {
                Exception(err.message ?: "Could not load chats.")
            }
            onError?.invoke(mapped)
        }

        val topicReg = db.collection(CHAT_ROOMS)
            .whereEqualTo("type", ChatRoom.TYPE_TOPIC)
            .limit(100)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    reportError("topic", err)
                    return@addSnapshotListener
                }
                topicRooms = snap?.documents?.mapNotNull { it.toChatRoom() } ?: emptyList()
                emit()
            }

        val dmReg = db.collection(CHAT_ROOMS)
            .whereEqualTo("type", ChatRoom.TYPE_DM)
            .whereArrayContains("memberIds", uid)
            .limit(50)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    reportError("dm", err)
                    return@addSnapshotListener
                }
                dmRooms = snap?.documents?.mapNotNull { it.toChatRoom() } ?: emptyList()
                emit()
            }

        return ChatRoomsListenerHandle(topicReg, dmReg)
    }

    fun listenMessages(
        roomId: String,
        onUpdate: (List<ChatMessage>) -> Unit,
        onError: ((Exception) -> Unit)? = null
    ): ListenerRegistration {
        return db.collection(CHAT_ROOMS).document(roomId)
            .collection(MESSAGES)
            .limit(200)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "listenMessages error", err)
                    val mapped = if (err is FirebaseFirestoreException) {
                        mapFirestoreError(err)
                    } else {
                        Exception(err.message ?: "Could not load messages.")
                    }
                    onError?.invoke(mapped)
                    return@addSnapshotListener
                }
                val messages = snap?.documents?.mapNotNull { it.toChatMessage() }
                    ?.sortedBy { it.createdAt?.seconds ?: 0L }
                    ?: emptyList()
                onUpdate(messages)
            }
    }

    private fun chatMessagePreview(text: String, mediaType: String?): String {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) return trimmed.take(120)
        return when (mediaType?.lowercase()) {
            MediaType.GIF -> "[GIF]"
            MediaType.VIDEO -> "[Video]"
            MediaType.IMAGE -> "[Photo]"
            else -> "[Media]"
        }
    }

    suspend fun sendChatMessage(
        roomId: String,
        text: String,
        imageUrl: String? = null,
        mediaType: String? = null
    ): Result<ChatMessage> {
        val fbUser = auth.currentUser ?: return Result.failure(Exception("Not logged in"))
        val user = fetchUser(fbUser.uid) ?: currentUser
            ?: return Result.failure(Exception("Profile missing"))
        if (user != currentUser) currentUser = user

        val actorRole = resolveRole(user, fbUser.email)
        if (!actorRole.canPost()) {
            return Result.failure(Exception("You cannot send messages at this time."))
        }

        val t = text.trim()
        val url = imageUrl?.trim()?.takeIf { it.isNotEmpty() }
        val resolvedMediaType = if (url != null) MediaType.resolve(mediaType, url) else null

        if (t.isBlank() && url == null) {
            return Result.failure(Exception("Message cannot be empty"))
        }
        if (t.length > MAX_CHAT_MESSAGE) {
            return Result.failure(Exception("Message too long (max $MAX_CHAT_MESSAGE chars)"))
        }
        if (t.isNotBlank() && containsGifUrl(t)) {
            return Result.failure(Exception("GIFs and image links are not allowed in message text. Use attach instead."))
        }

        return try {
            val room = getChatRoom(roomId)
            if (room?.locked == true && !actorRole.canModerate()) {
                return Result.failure(Exception("This topic is locked by moderators."))
            }

            val roomRef = db.collection(CHAT_ROOMS).document(roomId)
            val msgRef = roomRef.collection(MESSAGES).document()
            val now = Timestamp.now()
            val message = ChatMessage(
                id = msgRef.id,
                authorId = fbUser.uid,
                authorName = user.displayName.ifBlank {
                    fbUser.email?.substringBefore("@") ?: "User"
                },
                authorPhotoUrl = user.photoUrl,
                authorRole = firestoreRoleForWrites(fbUser.uid),
                text = t,
                imageUrl = url,
                mediaType = resolvedMediaType,
                createdAt = now,
                type = MediaType.messageType(resolvedMediaType, url)
            )
            val preview = chatMessagePreview(t, resolvedMediaType)
            val batch = db.batch()
            batch.set(msgRef, message)
            batch.update(
                roomRef,
                mapOf(
                    "lastMessageAt" to now,
                    "lastMessagePreview" to preview,
                    "lastMessageAuthorId" to fbUser.uid,
                    "messageCount" to FieldValue.increment(1)
                )
            )
            batch.commit().await()
            Result.success(message)
        } catch (e: Exception) {
            Log.e(TAG, "sendChatMessage error", e)
            Result.failure(e)
        }
    }

    suspend fun deleteChatMessage(roomId: String, messageId: String, messageAuthorId: String? = null): Result<Unit> {
        val uid = auth.currentUser?.uid
        val actorRole = resolveRole(currentUser)
        val isAuthor = messageAuthorId != null && messageAuthorId == uid
        if (!actorRole.canModerate() && !isAuthor) {
            return Result.failure(Exception("No permission to delete messages."))
        }
        return try {
            db.collection(CHAT_ROOMS).document(roomId)
                .collection(MESSAGES).document(messageId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "deleteChatMessage error", e)
            Result.failure(e)
        }
    }

    suspend fun getRecentChatMessages(limit: Long = 40): List<Pair<ChatRoom, ChatMessage>> {
        return try {
            val rooms = getChatRoomsForInbox().take(20)
            val results = mutableListOf<Pair<ChatRoom, ChatMessage>>()
            for (room in rooms) {
                val snap = db.collection(CHAT_ROOMS).document(room.id)
                    .collection(MESSAGES)
                    .limit(5)
                    .get().await()
                snap.documents.mapNotNull { it.toChatMessage() }
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
                    .forEach { msg -> results.add(room to msg) }
            }
            results.sortedByDescending { it.second.createdAt?.seconds ?: 0L }.take(limit.toInt())
        } catch (e: Exception) {
            Log.e(TAG, "getRecentChatMessages error", e)
            emptyList()
        }
    }

    suspend fun searchPosts(query: String, limit: Int = 30): List<Post> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return emptyList()
        return getPosts(limit = 200).filter { post ->
            post.title.lowercase().contains(q) ||
                post.body.lowercase().contains(q) ||
                post.authorName.lowercase().contains(q) ||
                post.category.lowercase().contains(q)
        }.take(limit)
    }

    suspend fun searchTopics(query: String, limit: Int = 20): List<PostCategory> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return emptyList()
        val banned = getHiddenTopicNames()
        val feedHidden = getFeedHiddenTopicNames()
        return collectTopicNames()
            .filter { !banned.contains(it.lowercase()) }
            .filter { !feedHidden.contains(it.lowercase()) }
            .filter { it.lowercase().contains(q) }
            .sorted()
            .take(limit)
            .mapIndexed { index, name -> PostCategory(id = "search-topic-$index", name = name) }
    }

    suspend fun searchChatRooms(query: String, limit: Int = 20): List<ChatRoom> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return emptyList()
        return getChatRoomsForInbox().filter { room ->
            room.title.lowercase().contains(q) ||
                room.topicName?.lowercase()?.contains(q) == true ||
                room.lastMessagePreview.lowercase().contains(q)
        }.take(limit)
    }

    fun listenChatRoom(
        roomId: String,
        onUpdate: (ChatRoom?) -> Unit,
        onError: ((Exception) -> Unit)? = null
    ): ListenerRegistration {
        return db.collection(CHAT_ROOMS).document(roomId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "listenChatRoom error", err)
                    val mapped = if (err is FirebaseFirestoreException) {
                        mapFirestoreError(err)
                    } else {
                        Exception(err.message ?: "Could not load chat room.")
                    }
                    onError?.invoke(mapped)
                    return@addSnapshotListener
                }
                onUpdate(if (snap != null && snap.exists()) snap.toChatRoom() else null)
            }
    }

    suspend fun getTopicRooms(limit: Long = 100): List<ChatRoom> {
        return try {
            val snap = db.collection(CHAT_ROOMS)
                .whereEqualTo("type", ChatRoom.TYPE_TOPIC)
                .limit(limit)
                .get().await()
            snap.documents.mapNotNull { it.toChatRoom() }
                .sortedByDescending { it.lastMessageAt?.seconds ?: 0L }
        } catch (e: Exception) {
            Log.e(TAG, "getTopicRooms error", e)
            emptyList()
        }
    }

    suspend fun lockTopicRoom(roomId: String, lock: Boolean): Result<Unit> {
        if (!resolveRole(currentUser).canModerate()) {
            return Result.failure(Exception("No permission to lock topics."))
        }
        val uid = auth.currentUser?.uid ?: return Result.failure(Exception("Not logged in"))
        return try {
            val room = getChatRoom(roomId) ?: return Result.failure(Exception("Room not found"))
            if (!room.isTopic()) return Result.failure(Exception("Only topic rooms can be locked."))
            val updates = mutableMapOf<String, Any>("locked" to lock)
            if (lock) {
                updates["lockedBy"] = uid
                updates["lockedAt"] = Timestamp.now()
            } else {
                updates["lockedBy"] = ""
                updates["lockedAt"] = Timestamp.now()
            }
            db.collection(CHAT_ROOMS).document(roomId).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "lockTopicRoom error", e)
            Result.failure(e)
        }
    }

    suspend fun resetTopicRoom(roomId: String): Result<Unit> {
        if (!resolveRole(currentUser).canModerate()) {
            return Result.failure(Exception("No permission to reset topics."))
        }
        return try {
            val room = getChatRoom(roomId) ?: return Result.failure(Exception("Room not found"))
            if (!room.isTopic()) return Result.failure(Exception("Only topic rooms can be reset."))

            val messagesSnap = db.collection(CHAT_ROOMS).document(roomId)
                .collection(MESSAGES).get().await()
            if (messagesSnap.documents.isNotEmpty()) {
                var batch = db.batch()
                var count = 0
                for (doc in messagesSnap.documents) {
                    batch.delete(doc.reference)
                    count++
                    if (count >= 400) {
                        batch.commit().await()
                        batch = db.batch()
                        count = 0
                    }
                }
                if (count > 0) batch.commit().await()
            }

            db.collection(CHAT_ROOMS).document(roomId).update(
                mapOf(
                    "lastMessageAt" to Timestamp.now(),
                    "lastMessagePreview" to "",
                    "lastMessageAuthorId" to "",
                    "messageCount" to 0L
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "resetTopicRoom error", e)
            Result.failure(e)
        }
    }

    suspend fun deleteTopicRoom(roomId: String): Result<Unit> {
        if (!resolveRole(currentUser).canModerate()) {
            return Result.failure(Exception("No permission to delete topics."))
        }
        return try {
            val room = getChatRoom(roomId) ?: return Result.failure(Exception("Room not found"))
            if (!room.isTopic()) return Result.failure(Exception("Only topic rooms can be deleted here."))

            val messagesSnap = db.collection(CHAT_ROOMS).document(roomId)
                .collection(MESSAGES).get().await()
            if (messagesSnap.documents.isNotEmpty()) {
                var batch = db.batch()
                var count = 0
                for (doc in messagesSnap.documents) {
                    batch.delete(doc.reference)
                    count++
                    if (count >= 400) {
                        batch.commit().await()
                        batch = db.batch()
                        count = 0
                    }
                }
                if (count > 0) batch.commit().await()
            }
            db.collection(CHAT_ROOMS).document(roomId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "deleteTopicRoom error", e)
            Result.failure(e)
        }
    }

    // --- Favorite chats ---

    private fun favoriteChatsRef(uid: String) =
        db.collection(USERS).document(uid).collection(FAVORITE_CHATS)

    fun listenFavoriteRoomIds(onUpdate: (Set<String>) -> Unit): ListenerRegistration? {
        val uid = auth.currentUser?.uid ?: return null
        return favoriteChatsRef(uid).addSnapshotListener { snap, err ->
            if (err != null) {
                Log.e(TAG, "listenFavoriteRoomIds error", err)
                onUpdate(emptySet())
                return@addSnapshotListener
            }
            val ids = snap?.documents?.map { it.id }?.toSet() ?: emptySet()
            onUpdate(ids)
        }
    }

    suspend fun getFavoriteRoomIds(): Set<String> {
        val uid = auth.currentUser?.uid ?: return emptySet()
        return try {
            val snap = favoriteChatsRef(uid).get().await()
            snap.documents.map { it.id }.toSet()
        } catch (e: Exception) {
            Log.e(TAG, "getFavoriteRoomIds error", e)
            emptySet()
        }
    }

    suspend fun toggleFavorite(roomId: String): Result<Boolean> {
        val uid = auth.currentUser?.uid ?: return Result.failure(Exception("Not logged in"))
        return try {
            val ref = favoriteChatsRef(uid).document(roomId)
            val existing = ref.get().await()
            if (existing.exists()) {
                ref.delete().await()
                Result.success(false)
            } else {
                ref.set(mapOf("roomId" to roomId, "pinnedAt" to Timestamp.now())).await()
                Result.success(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "toggleFavorite error", e)
            Result.failure(e)
        }
    }

    fun sortRoomsWithFavorites(rooms: List<ChatRoom>, favoriteIds: Set<String>): List<ChatRoom> {
        return rooms.sortedWith(
            compareByDescending<ChatRoom> { favoriteIds.contains(it.id) }
                .thenByDescending { it.lastMessageAt?.seconds ?: 0L }
        )
    }

    fun filterRooms(
        rooms: List<ChatRoom>,
        query: String,
        tab: String,
        favoriteIds: Set<String>
    ): List<ChatRoom> {
        var result = when (tab) {
            TAB_FAVORITES -> rooms.filter { favoriteIds.contains(it.id) }
            TAB_TOPICS -> rooms.filter { it.isTopic() }
            TAB_DMS -> rooms.filter { it.isDm() }
            else -> sortRoomsWithFavorites(rooms, favoriteIds)
        }
        val q = query.trim().lowercase()
        if (q.isNotEmpty()) {
            result = result.filter { room ->
                room.title.lowercase().contains(q) ||
                    room.topicName?.lowercase()?.contains(q) == true ||
                    room.lastMessagePreview.lowercase().contains(q)
            }
        }
        return result
    }

    enum class FriendshipStatus {
        NONE, PENDING_OUT, PENDING_IN, FRIENDS, SELF
    }

    private fun containsGifUrl(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains(".gif") || lower.contains("giphy.com") || lower.contains("tenor.com")
    }

    private fun friendsRef(uid: String) = db.collection(USERS).document(uid).collection(FRIENDS)

    private fun favoriteCategoriesRef(uid: String) =
        db.collection(USERS).document(uid).collection(FAVORITE_CATEGORIES)

    suspend fun getFavoriteCategories(uid: String): List<FavoriteCategory> {
        return try {
            val snap = favoriteCategoriesRef(uid).get().await()
            snap.documents.mapNotNull { doc ->
                doc.toObject(FavoriteCategory::class.java)?.copy(categoryId = doc.id)
            }.sortedBy { it.pinnedAt?.seconds ?: 0L }
        } catch (e: Exception) {
            Log.e(TAG, "getFavoriteCategories error", e)
            emptyList()
        }
    }

    suspend fun toggleFavoriteCategory(categoryId: String, name: String): Result<Boolean> {
        val uid = auth.currentUser?.uid ?: return Result.failure(Exception("Not logged in"))
        return try {
            val ref = favoriteCategoriesRef(uid).document(categoryId)
            val existing = ref.get().await()
            if (existing.exists()) {
                ref.delete().await()
                Result.success(false)
            } else {
                val current = getFavoriteCategories(uid)
                if (current.size >= MAX_FAVORITE_CATEGORIES) {
                    return Result.failure(Exception("You can pin up to $MAX_FAVORITE_CATEGORIES communities."))
                }
                ref.set(
                    FavoriteCategory(
                        categoryId = categoryId,
                        name = name,
                        pinnedAt = Timestamp.now()
                    )
                ).await()
                Result.success(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "toggleFavoriteCategory error", e)
            Result.failure(e)
        }
    }

    suspend fun getFriendshipStatus(otherUid: String): FriendshipStatus {
        val myUid = auth.currentUser?.uid ?: return FriendshipStatus.NONE
        if (myUid == otherUid) return FriendshipStatus.SELF
        return try {
            val friendDoc = friendsRef(myUid).document(otherUid).get().await()
            if (friendDoc.exists()) return FriendshipStatus.FRIENDS

            val outSnap = db.collection(FRIEND_REQUESTS)
                .whereEqualTo("fromUid", myUid)
                .whereEqualTo("toUid", otherUid)
                .whereEqualTo("status", FriendRequest.STATUS_PENDING)
                .limit(1)
                .get().await()
            if (!outSnap.isEmpty) return FriendshipStatus.PENDING_OUT

            val inSnap = db.collection(FRIEND_REQUESTS)
                .whereEqualTo("fromUid", otherUid)
                .whereEqualTo("toUid", myUid)
                .whereEqualTo("status", FriendRequest.STATUS_PENDING)
                .limit(1)
                .get().await()
            if (!inSnap.isEmpty) return FriendshipStatus.PENDING_IN

            FriendshipStatus.NONE
        } catch (e: Exception) {
            Log.e(TAG, "getFriendshipStatus error", e)
            FriendshipStatus.NONE
        }
    }

    suspend fun sendFriendRequest(toUid: String): Result<Unit> {
        val myUid = auth.currentUser?.uid ?: return Result.failure(Exception("Not logged in"))
        if (myUid == toUid) return Result.failure(Exception("Cannot friend yourself."))

        return try {
            when (getFriendshipStatus(toUid)) {
                FriendshipStatus.FRIENDS -> return Result.failure(Exception("Already friends."))
                FriendshipStatus.PENDING_OUT -> return Result.failure(Exception("Friend request already sent."))
                FriendshipStatus.PENDING_IN -> {
                    val inSnap = db.collection(FRIEND_REQUESTS)
                        .whereEqualTo("fromUid", toUid)
                        .whereEqualTo("toUid", myUid)
                        .whereEqualTo("status", FriendRequest.STATUS_PENDING)
                        .limit(1)
                        .get().await()
                    val reqId = inSnap.documents.firstOrNull()?.id
                        ?: return Result.failure(Exception("Request not found."))
                    return respondToFriendRequest(reqId, accept = true)
                }
                else -> {}
            }

            val ref = db.collection(FRIEND_REQUESTS).document()
            ref.set(
                FriendRequest(
                    id = ref.id,
                    fromUid = myUid,
                    toUid = toUid,
                    status = FriendRequest.STATUS_PENDING,
                    createdAt = Timestamp.now()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "sendFriendRequest error", e)
            Result.failure(e)
        }
    }

    suspend fun respondToFriendRequest(requestId: String, accept: Boolean): Result<Unit> {
        val myUid = auth.currentUser?.uid ?: return Result.failure(Exception("Not logged in"))
        return try {
            val reqRef = db.collection(FRIEND_REQUESTS).document(requestId)
            val snap = reqRef.get().await()
            val request = snap.toObject(FriendRequest::class.java)?.copy(id = snap.id)
                ?: return Result.failure(Exception("Request not found."))
            if (request.toUid != myUid) {
                return Result.failure(Exception("Not your request to respond to."))
            }
            if (request.status != FriendRequest.STATUS_PENDING) {
                return Result.failure(Exception("Request already handled."))
            }

            if (!accept) {
                reqRef.update("status", FriendRequest.STATUS_DECLINED).await()
                return Result.success(Unit)
            }

            val fromUser = fetchUser(request.fromUid) ?: return Result.failure(Exception("Sender not found."))
            val toUser = fetchUser(request.toUid) ?: return Result.failure(Exception("Recipient not found."))
            val now = Timestamp.now()
            val batch = db.batch()
            batch.set(
                friendsRef(request.fromUid).document(request.toUid),
                Friend(
                    uid = request.toUid,
                    displayName = toUser.displayName,
                    photoUrl = toUser.photoUrl,
                    since = now
                )
            )
            batch.set(
                friendsRef(request.toUid).document(request.fromUid),
                Friend(
                    uid = request.fromUid,
                    displayName = fromUser.displayName,
                    photoUrl = fromUser.photoUrl,
                    since = now
                )
            )
            batch.update(reqRef, "status", FriendRequest.STATUS_ACCEPTED)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "respondToFriendRequest error", e)
            Result.failure(e)
        }
    }

    suspend fun getIncomingFriendRequests(): List<FriendRequest> {
        val myUid = auth.currentUser?.uid ?: return emptyList()
        return try {
            val snap = db.collection(FRIEND_REQUESTS)
                .whereEqualTo("toUid", myUid)
                .whereEqualTo("status", FriendRequest.STATUS_PENDING)
                .get().await()
            snap.documents.mapNotNull { doc ->
                doc.toObject(FriendRequest::class.java)?.copy(id = doc.id)
            }.sortedByDescending { it.createdAt?.seconds ?: 0L }
        } catch (e: Exception) {
            Log.e(TAG, "getIncomingFriendRequests error", e)
            emptyList()
        }
    }

    suspend fun getFriends(uid: String): List<Friend> {
        return try {
            val snap = friendsRef(uid).get().await()
            snap.documents.mapNotNull { doc ->
                doc.toObject(Friend::class.java)?.copy(uid = doc.id)
            }.sortedBy { it.displayName.lowercase() }
        } catch (e: Exception) {
            Log.e(TAG, "getFriends error", e)
            emptyList()
        }
    }

    suspend fun removeFriend(friendUid: String): Result<Unit> {
        val myUid = auth.currentUser?.uid ?: return Result.failure(Exception("Not logged in"))
        return try {
            val batch = db.batch()
            batch.delete(friendsRef(myUid).document(friendUid))
            batch.delete(friendsRef(friendUid).document(myUid))
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "removeFriend error", e)
            Result.failure(e)
        }
    }

    const val TAB_RECENT = "recent"
    const val TAB_FAVORITES = "favorites"
    const val TAB_TOPICS = "topics"
    const val TAB_DMS = "dms"
    const val TAB_POPULAR = "popular"
}