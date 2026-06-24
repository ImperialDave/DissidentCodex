package com.codex.app.utils

import android.util.Log
import com.codex.app.models.AppNotification
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

object NotificationHelper {

    private const val TAG = "NotificationHelper"
    private val db = FirebaseFirestore.getInstance()

    private fun notifsRef(uid: String) =
        db.collection("users").document(uid).collection("notifications")

    suspend fun getNotifications(limit: Long = 50): List<AppNotification> {
        val uid = FirebaseHelper.getCurrentFirebaseUser()?.uid ?: return emptyList()
        return try {
            val snap = notifsRef(uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()
            snap.documents.mapNotNull { doc ->
                doc.toObject(AppNotification::class.java)?.copy(id = doc.id)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getNotifications error", e)
            emptyList()
        }
    }

    fun listenNotifications(
        onUpdate: (List<AppNotification>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration? {
        val uid = FirebaseHelper.getCurrentFirebaseUser()?.uid ?: return null
        return notifsRef(uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    onError(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(AppNotification::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                onUpdate(list)
            }
    }

    fun listenUnreadCount(
        onCount: (Int) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration? {
        val uid = FirebaseHelper.getCurrentFirebaseUser()?.uid ?: return null
        return notifsRef(uid)
            .whereEqualTo("read", false)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    onError(err)
                    return@addSnapshotListener
                }
                onCount(snap?.size() ?: 0)
            }
    }

    suspend fun markRead(notificationId: String): Result<Unit> {
        val uid = FirebaseHelper.getCurrentFirebaseUser()?.uid
            ?: return Result.failure(Exception("Sign in required"))
        return try {
            notifsRef(uid).document(notificationId)
                .update("read", true)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "markRead error", e)
            Result.failure(e)
        }
    }

    suspend fun markAllRead(): Result<Unit> {
        val uid = FirebaseHelper.getCurrentFirebaseUser()?.uid
            ?: return Result.failure(Exception("Sign in required"))
        return try {
            val snap = notifsRef(uid).whereEqualTo("read", false).get().await()
            if (snap.isEmpty) return Result.success(Unit)
            val batch = db.batch()
            snap.documents.forEach { doc ->
                batch.update(doc.reference, "read", true)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "markAllRead error", e)
            Result.failure(e)
        }
    }

    suspend fun deleteNotification(notificationId: String): Result<Unit> {
        val uid = FirebaseHelper.getCurrentFirebaseUser()?.uid
            ?: return Result.failure(Exception("Sign in required"))
        return try {
            notifsRef(uid).document(notificationId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "deleteNotification error", e)
            Result.failure(e)
        }
    }

    suspend fun clearAllNotifications(): Result<Unit> {
        val uid = FirebaseHelper.getCurrentFirebaseUser()?.uid
            ?: return Result.failure(Exception("Sign in required"))
        return try {
            val snap = notifsRef(uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .await()
            if (snap.isEmpty) return Result.success(Unit)
            val batch = db.batch()
            snap.documents.forEach { doc -> batch.delete(doc.reference) }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "clearAllNotifications error", e)
            Result.failure(e)
        }
    }
}