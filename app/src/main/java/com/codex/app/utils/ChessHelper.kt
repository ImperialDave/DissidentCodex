package com.codex.app.utils

import android.util.Log
import com.codex.app.chess.ChessEngine
import com.codex.app.models.ChessGame
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

object ChessHelper {

    private const val TAG = "ChessHelper"
    private const val GAMES = "chessGames"
    private val db = FirebaseFirestore.getInstance()

    fun gameIdFor(uidA: String, uidB: String): String {
        val (a, b) = if (uidA < uidB) uidA to uidB else uidB to uidA
        return "chess_${a}_${b}"
    }

    suspend fun getGameWithUser(opponentUid: String): ChessGame? {
        val me = FirebaseHelper.getCurrentFirebaseUser()?.uid ?: return null
        return getGameById(gameIdFor(me, opponentUid))
    }

    suspend fun getGameById(gameId: String): ChessGame? {
        return try {
            val snap = db.collection(GAMES).document(gameId).get().await()
            if (!snap.exists()) null
            else snap.toObject(ChessGame::class.java)?.copy(id = gameId)
        } catch (e: Exception) {
            if (isPermissionDenied(e)) {
                Log.w(TAG, "getGameById denied (doc may not exist yet): $gameId")
            } else {
                Log.e(TAG, "getGameById error", e)
            }
            null
        }
    }

    /**
     * Only attach a realtime listener when the game document already exists.
     * Listening to a missing doc fails Firestore rules that require player membership.
     */
    suspend fun listenGameWithUserIfExists(
        opponentUid: String,
        onUpdate: (ChessGame?) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration? {
        val game = getGameWithUser(opponentUid) ?: return null
        return listenGame(game.id, onUpdate, onError)
    }

    /**
     * Starts or resumes a game with another user. New games begin immediately as active (no accept step).
     */
    suspend fun sendChallenge(opponentUid: String): Result<ChessGame> {
        val me = FirebaseHelper.getCurrentFirebaseUser()
            ?: return Result.failure(Exception("Sign in required"))
        if (me.uid == opponentUid) return Result.failure(Exception("Cannot play yourself"))

        val myUser = FirebaseHelper.fetchUser(me.uid)
            ?: return Result.failure(Exception("Profile missing"))
        val oppUser = FirebaseHelper.fetchUser(opponentUid)
            ?: return Result.failure(Exception("Opponent not found"))

        startChessGameViaFunction(opponentUid)?.let { return it }

        val gameId = gameIdFor(me.uid, opponentUid)
        val ref = db.collection(GAMES).document(gameId)

        return try {
            val existing = try {
                ref.get().await()
            } catch (e: Exception) {
                if (isPermissionDenied(e)) {
                    Log.w(TAG, "get before create denied, creating new game")
                } else {
                    Log.w(TAG, "get before create failed, creating new game", e)
                }
                null
            }
            if (existing != null && existing.exists()) {
                val current = existing.toObject(ChessGame::class.java)?.copy(id = gameId)
                if (current != null) {
                    when (current.status) {
                        ChessGame.STATUS_ACTIVE -> return Result.success(current)
                        ChessGame.STATUS_PENDING -> return activateGame(ref, gameId, current)
                        ChessGame.STATUS_FINISHED, ChessGame.STATUS_DECLINED ->
                            return resetForNewGame(ref, gameId, me.uid, current, myUser, oppUser)
                    }
                }
            }
            createNewGame(ref, gameId, me.uid, opponentUid, myUser, oppUser)
        } catch (e: Exception) {
            Log.e(TAG, "sendChallenge error", e)
            Result.failure(friendlyError(e))
        }
    }

    private suspend fun createNewGame(
        ref: com.google.firebase.firestore.DocumentReference,
        gameId: String,
        challengerUid: String,
        opponentUid: String,
        myUser: com.codex.app.models.User,
        oppUser: com.codex.app.models.User
    ): Result<ChessGame> {
        val now = Timestamp.now()
        val activeGame = ChessGame(
            id = gameId,
            playerUids = listOf(challengerUid, opponentUid),
            whiteUid = challengerUid,
            blackUid = opponentUid,
            whiteName = myUser.displayName.ifBlank { "Player" },
            blackName = oppUser.displayName.ifBlank { "Player" },
            challengerUid = challengerUid,
            status = ChessGame.STATUS_ACTIVE,
            fen = ChessEngine.START_FEN,
            turn = "w",
            createdAt = now,
            updatedAt = now
        )
        return try {
            ref.set(activeGame).await()
            Result.success(activeGame)
        } catch (e: Exception) {
            if (!isPermissionDenied(e)) throw e
            Log.w(TAG, "active create denied, trying pending fallback", e)
            val pendingGame = activeGame.copy(status = ChessGame.STATUS_PENDING)
            ref.set(pendingGame).await()
            activateGame(ref, gameId, pendingGame)
        }
    }

    private suspend fun activateGame(
        ref: com.google.firebase.firestore.DocumentReference,
        gameId: String,
        current: ChessGame
    ): Result<ChessGame> {
        return try {
            ref.update(
                mapOf(
                    "status" to ChessGame.STATUS_ACTIVE,
                    "updatedAt" to Timestamp.now()
                )
            ).await()
            Result.success(getGameById(gameId) ?: current.copy(status = ChessGame.STATUS_ACTIVE))
        } catch (e: Exception) {
            Log.e(TAG, "activateGame error", e)
            Result.failure(friendlyError(e))
        }
    }

    private suspend fun resetForNewGame(
        ref: com.google.firebase.firestore.DocumentReference,
        gameId: String,
        challengerUid: String,
        prev: ChessGame,
        myUser: com.codex.app.models.User,
        oppUser: com.codex.app.models.User
    ): Result<ChessGame> {
        val now = Timestamp.now()
        val whiteName = if (prev.whiteUid == myUser.uid) {
            myUser.displayName.ifBlank { "Player" }
        } else {
            oppUser.displayName.ifBlank { "Player" }
        }
        val blackName = if (prev.blackUid == myUser.uid) {
            myUser.displayName.ifBlank { "Player" }
        } else {
            oppUser.displayName.ifBlank { "Player" }
        }
        val updates = mapOf(
            "playerUids" to listOf(prev.whiteUid, prev.blackUid),
            "whiteUid" to prev.whiteUid,
            "blackUid" to prev.blackUid,
            "whiteName" to whiteName,
            "blackName" to blackName,
            "challengerUid" to challengerUid,
            "status" to ChessGame.STATUS_ACTIVE,
            "fen" to ChessEngine.START_FEN,
            "turn" to "w",
            "winnerUid" to FieldValue.delete(),
            "result" to FieldValue.delete(),
            "eloApplied" to false,
            "updatedAt" to now
        )
        return try {
            ref.update(updates).await()
            val game = getGameById(gameId)
                ?: return Result.failure(Exception("Game reset failed"))
            Result.success(game)
        } catch (e: Exception) {
            Log.e(TAG, "resetForNewGame error", e)
            Result.failure(friendlyError(e))
        }
    }

    suspend fun cancelChallenge(gameId: String): Result<Unit> {
        val me = FirebaseHelper.getCurrentFirebaseUser()
            ?: return Result.failure(Exception("Sign in required"))
        val ref = db.collection(GAMES).document(gameId)
        val game = getGameById(gameId)
            ?: return Result.failure(Exception("Game not found"))
        if (game.status != ChessGame.STATUS_PENDING) {
            return Result.failure(Exception("Only pending challenges can be cancelled"))
        }
        if (game.challengerUid != me.uid) {
            return Result.failure(Exception("Only the challenger can cancel"))
        }
        return try {
            ref.update(
                mapOf(
                    "status" to ChessGame.STATUS_DECLINED,
                    "updatedAt" to Timestamp.now()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "cancelChallenge error", e)
            Result.failure(e)
        }
    }

    suspend fun respondToChallenge(gameId: String, accept: Boolean): Result<ChessGame> {
        val me = FirebaseHelper.getCurrentFirebaseUser()
            ?: return Result.failure(Exception("Sign in required"))
        val ref = db.collection(GAMES).document(gameId)
        val game = getGameById(gameId)
            ?: return Result.failure(Exception("Game not found"))
        if (game.status != ChessGame.STATUS_PENDING) {
            return Result.failure(Exception("Challenge already handled"))
        }
        if (game.challengerUid == me.uid) {
            return Result.failure(Exception("Wait for your opponent to respond"))
        }
        if (me.uid != game.whiteUid && me.uid != game.blackUid) {
            return Result.failure(Exception("Not a participant"))
        }
        return try {
            if (accept) {
                ref.update(
                    mapOf(
                        "status" to ChessGame.STATUS_ACTIVE,
                        "updatedAt" to Timestamp.now()
                    )
                ).await()
            } else {
                ref.update(
                    mapOf(
                        "status" to ChessGame.STATUS_DECLINED,
                        "updatedAt" to Timestamp.now()
                    )
                ).await()
            }
            Result.success(getGameById(gameId) ?: game)
        } catch (e: Exception) {
            Log.e(TAG, "respondToChallenge error", e)
            Result.failure(friendlyError(e))
        }
    }

    suspend fun applyMove(
        gameId: String,
        newFen: String,
        turn: String,
        result: String? = null,
        winnerUid: String? = null
    ): Result<Unit> {
        val me = FirebaseHelper.getCurrentFirebaseUser()
            ?: return Result.failure(Exception("Sign in required"))
        val ref = db.collection(GAMES).document(gameId)
        val game = getGameById(gameId)
            ?: return Result.failure(Exception("Game not found"))
        if (game.status != ChessGame.STATUS_ACTIVE) {
            return Result.failure(Exception("Game is not active"))
        }
        if (!game.isMyTurn(me.uid)) {
            return Result.failure(Exception("Not your turn"))
        }
        val updates = mutableMapOf<String, Any>(
            "fen" to newFen,
            "turn" to turn,
            "updatedAt" to Timestamp.now()
        )
        if (result != null) {
            updates["status"] = ChessGame.STATUS_FINISHED
            updates["result"] = result
            if (winnerUid != null) updates["winnerUid"] = winnerUid
        }
        return try {
            ref.update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "applyMove error", e)
            Result.failure(e)
        }
    }

    suspend fun resign(gameId: String): Result<Unit> {
        val me = FirebaseHelper.getCurrentFirebaseUser()
            ?: return Result.failure(Exception("Sign in required"))
        val ref = db.collection(GAMES).document(gameId)
        val game = getGameById(gameId)
            ?: return Result.failure(Exception("Game not found"))
        if (game.status != ChessGame.STATUS_ACTIVE) {
            return Result.failure(Exception("Game is not active"))
        }
        if (me.uid != game.whiteUid && me.uid != game.blackUid) {
            return Result.failure(Exception("Not a participant"))
        }
        val winner = game.opponentUid(me.uid)
        return try {
            ref.update(
                mapOf(
                    "status" to ChessGame.STATUS_FINISHED,
                    "result" to ChessGame.RESULT_RESIGN,
                    "winnerUid" to winner,
                    "updatedAt" to Timestamp.now()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "resign error", e)
            Result.failure(e)
        }
    }

    suspend fun getMyGames(): List<ChessGame> {
        val me = FirebaseHelper.getCurrentFirebaseUser()?.uid ?: return emptyList()
        val fromArray = queryMyGamesArray(me)
        if (fromArray.isNotEmpty()) return fromArray
        val fromFields = queryMyGamesByColor(me)
        if (fromFields.isNotEmpty()) return fromFields
        return discoverGamesViaFriends(me)
    }

    private suspend fun queryMyGamesArray(me: String): List<ChessGame> {
        return try {
            val snap = db.collection(GAMES).whereArrayContains("playerUids", me).get().await()
            snap.documents
                .mapNotNull { doc -> doc.toObject(ChessGame::class.java)?.copy(id = doc.id) }
                .filter { it.status != ChessGame.STATUS_DECLINED }
                .sortedByDescending { it.updatedAt?.seconds ?: 0L }
        } catch (e: Exception) {
            Log.w(TAG, "queryMyGamesArray error", e)
            emptyList()
        }
    }

    private suspend fun queryMyGamesByColor(me: String): List<ChessGame> {
        return try {
            val w = db.collection(GAMES).whereEqualTo("whiteUid", me).get().await()
            val b = db.collection(GAMES).whereEqualTo("blackUid", me).get().await()
            (w.documents + b.documents)
                .mapNotNull { doc -> doc.toObject(ChessGame::class.java)?.copy(id = doc.id) }
                .filter { it.status != ChessGame.STATUS_DECLINED }
                .distinctBy { it.id }
                .sortedByDescending { it.updatedAt?.seconds ?: 0L }
        } catch (e: Exception) {
            Log.w(TAG, "queryMyGamesByColor error", e)
            emptyList()
        }
    }

    private suspend fun discoverGamesViaFriends(me: String): List<ChessGame> {
        return try {
            FirebaseHelper.getFriends(me)
                .mapNotNull { friend -> getGameById(gameIdFor(me, friend.uid)) }
                .filter { it.status != ChessGame.STATUS_DECLINED }
                .sortedByDescending { it.updatedAt?.seconds ?: 0L }
        } catch (e: Exception) {
            Log.e(TAG, "discoverGamesViaFriends error", e)
            emptyList()
        }
    }

    fun listenGame(
        gameId: String,
        onUpdate: (ChessGame?) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return db.collection(GAMES).document(gameId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    onError(err)
                    return@addSnapshotListener
                }
                if (snap == null || !snap.exists()) {
                    onUpdate(null)
                    return@addSnapshotListener
                }
                onUpdate(snap.toObject(ChessGame::class.java)?.copy(id = snap.id))
            }
    }

    fun listenMyGames(
        onUpdate: (List<ChessGame>) -> Unit,
        onError: (Exception) -> Unit
    ): Pair<ListenerRegistration, ListenerRegistration> {
        val me = FirebaseHelper.getCurrentFirebaseUser()?.uid
        if (me == null) {
            onUpdate(emptyList())
            val noop = db.collection(GAMES).limit(0).addSnapshotListener { _, _ -> }
            return noop to noop
        }
        val cache = mutableMapOf<String, ChessGame>()
        fun emit() {
            onUpdate(
                cache.values
                    .filter { it.status != ChessGame.STATUS_DECLINED }
                    .sortedByDescending { it.updatedAt?.seconds ?: 0L }
            )
        }
        fun merge(snap: com.google.firebase.firestore.QuerySnapshot?, forWhite: Boolean) {
            if (snap == null) return
            val ids = snap.documents.map { it.id }.toSet()
            cache.entries.removeIf { (id, game) ->
                val mine = if (forWhite) game.whiteUid == me else game.blackUid == me
                mine && id !in ids
            }
            snap.documents.forEach { doc ->
                val game = doc.toObject(ChessGame::class.java)?.copy(id = doc.id) ?: return@forEach
                if (game.status == ChessGame.STATUS_DECLINED) cache.remove(game.id)
                else cache[game.id] = game
            }
            emit()
        }
        val whiteListener = db.collection(GAMES)
            .whereEqualTo("whiteUid", me)
            .addSnapshotListener { snap, err ->
                if (err != null) { onError(err); return@addSnapshotListener }
                merge(snap, forWhite = true)
            }
        val blackListener = db.collection(GAMES)
            .whereEqualTo("blackUid", me)
            .addSnapshotListener { snap, err ->
                if (err != null) { onError(err); return@addSnapshotListener }
                merge(snap, forWhite = false)
            }
        return whiteListener to blackListener
    }

    private suspend fun startChessGameViaFunction(opponentUid: String): Result<ChessGame>? {
        return try {
            val result = Firebase.functions
                .getHttpsCallable("startChessGame")
                .call(mapOf("opponentUid" to opponentUid))
                .await()
            val data = result.getData() as? Map<*, *> ?: return null
            val gameId = data["gameId"] as? String ?: return null
            val game = ChessGame(
                id = gameId,
                whiteUid = data["whiteUid"] as? String ?: "",
                blackUid = data["blackUid"] as? String ?: "",
                whiteName = data["whiteName"] as? String ?: "Player",
                blackName = data["blackName"] as? String ?: "Player",
                challengerUid = data["challengerUid"] as? String ?: "",
                status = data["status"] as? String ?: ChessGame.STATUS_ACTIVE,
                fen = data["fen"] as? String ?: ChessEngine.START_FEN,
                turn = data["turn"] as? String ?: "w",
                winnerUid = data["winnerUid"] as? String,
                result = data["result"] as? String,
                createdAt = data["createdAt"] as? Timestamp,
                updatedAt = data["updatedAt"] as? Timestamp
            )
            Result.success(game)
        } catch (e: Exception) {
            if (e is FirebaseFunctionsException &&
                e.code == FirebaseFunctionsException.Code.NOT_FOUND
            ) {
                Log.w(TAG, "startChessGame function not deployed yet")
            } else {
                Log.w(TAG, "startChessGame function failed, using client fallback", e)
            }
            null
        }
    }

    private fun isPermissionDenied(e: Exception): Boolean {
        return e is FirebaseFirestoreException &&
            e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    }

    private fun friendlyError(e: Exception): Exception {
        return if (isPermissionDenied(e)) {
            Exception("Chess is blocked by Firestore rules. Publish the latest firestore.rules in Firebase Console.")
        } else {
            Exception(e.message ?: "Could not start game")
        }
    }
}