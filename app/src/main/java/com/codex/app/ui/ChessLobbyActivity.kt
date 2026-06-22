package com.codex.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.codex.app.R
import com.codex.app.adapters.ChessGameAdapter
import com.codex.app.databinding.ActivityChessLobbyBinding
import com.codex.app.models.ChessGame
import com.codex.app.utils.ChessHelper
import com.codex.app.utils.FirebaseHelper
import com.codex.app.utils.WindowInsetsHelper
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class ChessLobbyActivity : BaseThemedActivity() {

    private lateinit var binding: ActivityChessLobbyBinding
    private lateinit var adapter: ChessGameAdapter
    private var whiteListener: ListenerRegistration? = null
    private var blackListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChessLobbyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        WindowInsetsHelper.applyTopSafeArea(binding.toolbar)

        val myUid = FirebaseHelper.getCurrentFirebaseUser()?.uid
        if (myUid == null) {
            Toast.makeText(this, "Sign in required", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        adapter = ChessGameAdapter(
            myUid = myUid,
            onAction = { game, action ->
                when (action) {
                    ChessGameAdapter.Action.ACCEPT -> respond(game, accept = true)
                    ChessGameAdapter.Action.DECLINE -> respond(game, accept = false)
                    ChessGameAdapter.Action.OPEN, ChessGameAdapter.Action.CANCEL -> openGame(game)
                }
            },
            onOpen = { openGame(it) }
        )
        binding.gamesRecycler.layoutManager = LinearLayoutManager(this)
        binding.gamesRecycler.adapter = adapter
    }

    override fun onStart() {
        super.onStart()
        binding.loadingBar.visibility = View.VISIBLE
        val (w, b) = ChessHelper.listenMyGames(
            onUpdate = { games ->
                binding.loadingBar.visibility = View.GONE
                showGames(games)
            },
            onError = { err ->
                binding.loadingBar.visibility = View.GONE
                Toast.makeText(this, err.message ?: "Could not load games", Toast.LENGTH_SHORT).show()
                refreshGames()
            }
        )
        whiteListener = w
        blackListener = b
    }

    override fun onResume() {
        super.onResume()
        refreshGames()
    }

    private fun refreshGames() {
        lifecycleScope.launch {
            binding.loadingBar.visibility = View.VISIBLE
            val games = ChessHelper.getMyGames()
            binding.loadingBar.visibility = View.GONE
            showGames(games)
        }
    }

    private fun showGames(games: List<ChessGame>) {
        val sorted = games.sortedWith(
            compareBy<ChessGame> {
                when (it.status) {
                    ChessGame.STATUS_ACTIVE -> 0
                    ChessGame.STATUS_PENDING -> 1
                    ChessGame.STATUS_FINISHED -> 2
                    else -> 3
                }
            }.thenByDescending { it.updatedAt?.seconds ?: 0L }
        )
        adapter.submitList(sorted)
        val empty = sorted.isEmpty()
        binding.emptyText.visibility = if (empty) View.VISIBLE else View.GONE
        binding.gamesRecycler.visibility = if (empty) View.GONE else View.VISIBLE
    }

    override fun onStop() {
        whiteListener?.remove()
        blackListener?.remove()
        whiteListener = null
        blackListener = null
        super.onStop()
    }

    private fun respond(game: ChessGame, accept: Boolean) {
        lifecycleScope.launch {
            val res = ChessHelper.respondToChallenge(game.id, accept)
            if (res.isSuccess && accept) {
                openGame(res.getOrThrow())
            } else if (res.isFailure) {
                Toast.makeText(this@ChessLobbyActivity, res.exceptionOrNull()?.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openGame(game: ChessGame) {
        startActivity(ChessGameActivity.intent(this, game.id))
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, ChessLobbyActivity::class.java)
    }
}