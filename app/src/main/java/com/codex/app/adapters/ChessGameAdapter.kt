package com.codex.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.codex.app.databinding.ItemChessGameBinding
import com.codex.app.models.ChessGame

class ChessGameAdapter(
    private val myUid: String,
    private val onAction: (ChessGame, Action) -> Unit,
    private val onOpen: (ChessGame) -> Unit
) : RecyclerView.Adapter<ChessGameAdapter.VH>() {

    enum class Action { ACCEPT, DECLINE, OPEN, CANCEL }

    private var games: List<ChessGame> = emptyList()

    fun submitList(list: List<ChessGame>) {
        games = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemChessGameBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(games[position])

    override fun getItemCount() = games.size

    inner class VH(private val b: ItemChessGameBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(game: ChessGame) {
            b.actionBtn.isEnabled = true
            val oppName = if (myUid == game.whiteUid) game.blackName else game.whiteName
            b.opponentName.text = oppName
            b.gameStatus.text = when (game.status) {
                ChessGame.STATUS_PENDING -> {
                    if (game.challengerUid == myUid) "Waiting for $oppName to accept"
                    else "$oppName challenged you"
                }
                ChessGame.STATUS_ACTIVE -> {
                    if (game.isMyTurn(myUid)) "Your turn" else "$oppName's turn"
                }
                ChessGame.STATUS_FINISHED -> {
                    when (game.result) {
                        ChessGame.RESULT_RESIGN ->
                            if (game.winnerUid == myUid) "You won (resignation)" else "You lost (resignation)"
                        ChessGame.RESULT_STALEMATE -> "Draw (stalemate)"
                        ChessGame.RESULT_CHECKMATE ->
                            if (game.winnerUid == myUid) "You won (checkmate)" else "You lost (checkmate)"
                        else -> "Game over"
                    }
                }
                else -> game.status
            }

            when {
                game.status == ChessGame.STATUS_PENDING && game.challengerUid != myUid -> {
                    b.actionBtn.text = "Accept & Play"
                    b.actionBtn.setOnClickListener { onAction(game, Action.ACCEPT) }
                }
                game.status == ChessGame.STATUS_PENDING && game.challengerUid == myUid -> {
                    b.actionBtn.text = "Play"
                    b.actionBtn.setOnClickListener { onOpen(game) }
                }
                game.status == ChessGame.STATUS_ACTIVE -> {
                    b.actionBtn.text = "Play"
                    b.actionBtn.setOnClickListener { onOpen(game) }
                }
                game.status == ChessGame.STATUS_FINISHED -> {
                    b.actionBtn.text = "View"
                    b.actionBtn.setOnClickListener { onOpen(game) }
                }
                else -> {
                    b.actionBtn.text = "Open"
                    b.actionBtn.setOnClickListener { onOpen(game) }
                }
            }

            b.root.setOnClickListener { onOpen(game) }
        }
    }
}