package com.codex.app.chess

import com.codex.app.R

/** Maps board chars to lightweight vector piece icons (~1 KB each). */
object ChessPieces {
    fun drawableFor(piece: Char): Int? = when (piece) {
        'K' -> R.drawable.chess_wk
        'Q' -> R.drawable.chess_wq
        'R' -> R.drawable.chess_wr
        'B' -> R.drawable.chess_wb
        'N' -> R.drawable.chess_wn
        'P' -> R.drawable.chess_wp
        'k' -> R.drawable.chess_bk
        'q' -> R.drawable.chess_bq
        'r' -> R.drawable.chess_br
        'b' -> R.drawable.chess_bb
        'n' -> R.drawable.chess_bn
        'p' -> R.drawable.chess_bp
        else -> null
    }
}