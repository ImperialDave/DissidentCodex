package com.codex.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.codex.app.R
import com.codex.app.chess.ChessBoardView
import com.codex.app.chess.ChessEngine
import com.codex.app.databinding.ActivityChessGameBinding
import com.codex.app.models.ChessGame
import com.codex.app.utils.ChessHelper
import com.codex.app.utils.FirebaseHelper
import com.codex.app.utils.WindowInsetsHelper
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class ChessGameActivity : BaseThemedActivity(), ChessBoardView.Listener {

    private lateinit var binding: ActivityChessGameBinding
    private var gameId: String? = null
    private var gameListener: ListenerRegistration? = null
    private var currentGame: ChessGame? = null
    private var localState: ChessEngine.State = ChessEngine.parseFen(ChessEngine.START_FEN)
    private var selectedSquare: Int = -1
    private var myUid: String = ""
    private var isSubmitting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChessGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        gameId = intent.getStringExtra(EXTRA_GAME_ID)
        myUid = FirebaseHelper.getCurrentFirebaseUser()?.uid.orEmpty()
        if (gameId.isNullOrBlank() || myUid.isBlank()) {
            finish()
            return
        }

        binding.toolbar.setNavigationOnClickListener { finish() }
        WindowInsetsHelper.applyTopSafeArea(binding.toolbar)
        binding.chessBoard.listener = this
        binding.resignBtn.setOnClickListener { confirmResign() }
        binding.acceptBtn.setOnClickListener { acceptChallenge() }
        binding.declineBtn.setOnClickListener { declineChallenge() }
    }

    override fun onStart() {
        super.onStart()
        val gid = gameId ?: return
        gameListener = ChessHelper.listenGame(
            gameId = gid,
            onUpdate = { game -> runOnUiThread { bindGame(game) } },
            onError = { err ->
                runOnUiThread {
                    Toast.makeText(this, err.message ?: "Sync error", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    override fun onStop() {
        gameListener?.remove()
        gameListener = null
        super.onStop()
    }

    private fun bindGame(game: ChessGame?) {
        if (game == null) {
            Toast.makeText(this, R.string.chess_game_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        currentGame = game
        localState = ChessEngine.parseFen(game.fen.ifBlank { ChessEngine.START_FEN })
        val amWhite = game.myColor(myUid) == true
        binding.chessBoard.flipped = !amWhite
        binding.chessBoard.setPosition(game.fen)
        binding.chessBoard.clearSelection()
        selectedSquare = -1

        val opp = if (myUid == game.whiteUid) game.blackName else game.whiteName
        binding.toolbar.title = getString(R.string.chess_vs, opp)
        val isChallenged = game.status == ChessGame.STATUS_PENDING && game.challengerUid != myUid
        val isWaiting = game.status == ChessGame.STATUS_PENDING && game.challengerUid == myUid

        binding.statusText.text = when (game.status) {
            ChessGame.STATUS_PENDING -> {
                if (isChallenged) getString(R.string.chess_incoming_challenge, opp)
                else getString(R.string.chess_waiting_for, opp)
            }
            ChessGame.STATUS_FINISHED -> resultMessage(game)
            ChessGame.STATUS_ACTIVE -> {
                if (game.isMyTurn(myUid)) getString(R.string.chess_your_turn)
                else getString(R.string.chess_opponent_turn, opp)
            }
            else -> ""
        }

        binding.challengeActions.visibility = if (isChallenged) View.VISIBLE else View.GONE
        binding.activeActions.visibility = if (game.status == ChessGame.STATUS_ACTIVE) View.VISIBLE else View.GONE
        binding.resignBtn.isEnabled = game.status == ChessGame.STATUS_ACTIVE && !isSubmitting
        binding.chessBoard.isEnabled = game.status == ChessGame.STATUS_ACTIVE && game.isMyTurn(myUid)

        if (isWaiting || game.status == ChessGame.STATUS_FINISHED) {
            binding.chessBoard.isEnabled = false
        }
    }

    private fun acceptChallenge() {
        val gid = gameId ?: return
        lifecycleScope.launch {
            val res = ChessHelper.respondToChallenge(gid, accept = true)
            if (res.isFailure) {
                Toast.makeText(this@ChessGameActivity, res.exceptionOrNull()?.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun declineChallenge() {
        val gid = gameId ?: return
        lifecycleScope.launch {
            val res = ChessHelper.respondToChallenge(gid, accept = false)
            if (res.isSuccess) finish()
            else Toast.makeText(this@ChessGameActivity, res.exceptionOrNull()?.message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSquareTapped(square: Int) {
        val game = currentGame ?: return
        if (game.status != ChessGame.STATUS_ACTIVE || !game.isMyTurn(myUid) || isSubmitting) return

        val piece = localState.board[square]
        val myWhite = game.myColor(myUid) == true

        if (selectedSquare >= 0) {
            val moves = ChessEngine.legalMoves(localState).filter { it.from == selectedSquare }
            val chosen = moves.find { it.to == square }
            if (chosen != null) {
                if (chosen.promotion != null && needsPromotionUi(chosen)) {
                    showPromotionDialog(chosen)
                } else {
                    submitMove(chosen)
                }
                return
            }
        }

        if (piece != '.' && ChessEngine.isWhite(piece) == myWhite) {
            selectedSquare = square
            val targets = ChessEngine.legalMoves(localState)
                .filter { it.from == square }
                .map { it.to }
                .toSet()
            binding.chessBoard.setSelection(square, targets)
        } else {
            selectedSquare = -1
            binding.chessBoard.clearSelection()
        }
    }

    private fun needsPromotionUi(move: ChessEngine.Move): Boolean {
        val promoRank = if (ChessEngine.isWhite(localState.board[move.from])) 7 else 0
        return move.to / 8 == promoRank && move.promotion != null
    }

    private fun showPromotionDialog(baseMove: ChessEngine.Move) {
        val white = ChessEngine.isWhite(localState.board[baseMove.from])
        val options = if (white) arrayOf("Queen", "Rook", "Bishop", "Knight")
        else arrayOf("Queen", "Rook", "Bishop", "Knight")
        val chars = if (white) charArrayOf('Q', 'R', 'B', 'N') else charArrayOf('q', 'r', 'b', 'n')
        AlertDialog.Builder(this)
            .setTitle(R.string.chess_promote)
            .setItems(options) { _, which ->
                submitMove(baseMove.copy(promotion = chars[which]))
            }
            .setOnCancelListener {
                selectedSquare = -1
                binding.chessBoard.clearSelection()
            }
            .show()
    }

    private fun submitMove(move: ChessEngine.Move) {
        val game = currentGame ?: return
        val gid = gameId ?: return
        val next = ChessEngine.apply(localState, move)
        val fen = ChessEngine.toFen(next)
        val turn = if (next.whiteToMove) "w" else "b"
        val end = ChessEngine.evaluateEnd(next)

        var result: String? = null
        var winner: String? = null
        if (end != null) {
            result = when (end) {
                ChessEngine.GameResult.STALEMATE -> ChessGame.RESULT_STALEMATE
                ChessEngine.GameResult.DRAW -> ChessGame.RESULT_DRAW
                ChessEngine.GameResult.WHITE_WIN -> ChessGame.RESULT_CHECKMATE
                ChessEngine.GameResult.BLACK_WIN -> ChessGame.RESULT_CHECKMATE
            }
            winner = when (end) {
                ChessEngine.GameResult.WHITE_WIN -> game.whiteUid
                ChessEngine.GameResult.BLACK_WIN -> game.blackUid
                else -> null
            }
        }

        isSubmitting = true
        binding.resignBtn.isEnabled = false
        lifecycleScope.launch {
            val res = ChessHelper.applyMove(gid, fen, turn, result, winner)
            isSubmitting = false
            if (res.isFailure) {
                Toast.makeText(
                    this@ChessGameActivity,
                    res.exceptionOrNull()?.message ?: getString(R.string.error_generic),
                    Toast.LENGTH_SHORT
                ).show()
                bindGame(currentGame)
            } else {
                localState = next
                binding.chessBoard.setPosition(fen)
                selectedSquare = -1
                binding.chessBoard.clearSelection()
            }
        }
    }

    private fun confirmResign() {
        AlertDialog.Builder(this)
            .setMessage(R.string.chess_resign_confirm)
            .setPositiveButton(R.string.chess_resign) { _, _ ->
                val gid = gameId ?: return@setPositiveButton
                lifecycleScope.launch {
                    val res = ChessHelper.resign(gid)
                    if (res.isFailure) {
                        Toast.makeText(
                            this@ChessGameActivity,
                            res.exceptionOrNull()?.message,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun resultMessage(game: ChessGame): String {
        return when (game.result) {
            ChessGame.RESULT_RESIGN -> {
                if (game.winnerUid == myUid) getString(R.string.chess_you_won_resign)
                else getString(R.string.chess_you_lost_resign)
            }
            ChessGame.RESULT_STALEMATE -> getString(R.string.chess_draw_stalemate)
            ChessGame.RESULT_CHECKMATE -> {
                if (game.winnerUid == myUid) getString(R.string.chess_you_won_checkmate)
                else getString(R.string.chess_you_lost_checkmate)
            }
            else -> getString(R.string.chess_game_over)
        }
    }

    companion object {
        const val EXTRA_GAME_ID = "gameId"

        fun intent(context: Context, gameId: String): Intent =
            Intent(context, ChessGameActivity::class.java).putExtra(EXTRA_GAME_ID, gameId)
    }
}