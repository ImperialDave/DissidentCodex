package com.codex.app.utils

import android.net.Uri
import android.util.Log
import com.codex.app.CodexApplication
import com.codex.app.models.BannedTopic
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
    private const val PLACEHOLDER_LOAD_FROM_FILE