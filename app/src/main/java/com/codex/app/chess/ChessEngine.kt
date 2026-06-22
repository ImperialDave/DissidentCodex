package com.codex.app.chess

/**
 * Lightweight chess rules engine. Stores board as 64 chars; serializes to FEN only.
 * No bitmaps, no move-history buffers — minimal heap use.
 */
object ChessEngine {

    const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    data class Move(val from: Int, val to: Int, val promotion: Char? = null)

    enum class GameResult { WHITE_WIN, BLACK_WIN, STALEMATE, DRAW }

    data class State(
        val board: CharArray,
        val whiteToMove: Boolean,
        val castling: String,
        val enPassant: Int?,
        val halfmove: Int,
        val fullmove: Int
    ) {
        fun copyBoard(): State = copy(board = board.copyOf())
    }

    fun parseFen(fen: String): State {
        val parts = fen.trim().split("\\s+".toRegex())
        val rows = parts[0].split("/")
        val board = CharArray(64) { '.' }
        for (r in 0 until 8) {
            var f = 0
            for (ch in rows[r]) {
                if (ch.isDigit()) f += ch.digitToInt()
                else {
                    val rank = 7 - r
                    board[rank * 8 + f] = ch
                    f++
                }
            }
        }
        val white = parts.getOrNull(1) != "b"
        val castling = parts.getOrNull(2)?.takeIf { it != "-" } ?: ""
        val ep = parts.getOrNull(3)?.let { sqToIndex(it) }?.takeIf { it >= 0 }
        val half = parts.getOrNull(4)?.toIntOrNull() ?: 0
        val full = parts.getOrNull(5)?.toIntOrNull() ?: 1
        return State(board, white, castling, ep, half, full)
    }

    fun toFen(state: State): String {
        val sb = StringBuilder()
        for (r in 7 downTo 0) {
            var empty = 0
            for (f in 0 until 8) {
                val p = state.board[r * 8 + f]
                if (p == '.') empty++
                else {
                    if (empty > 0) { sb.append(empty); empty = 0 }
                    sb.append(p)
                }
            }
            if (empty > 0) sb.append(empty)
            if (r > 0) sb.append('/')
        }
        sb.append(if (state.whiteToMove) " w " else " b ")
        sb.append(state.castling.ifEmpty { "-" })
        sb.append(' ')
        sb.append(state.enPassant?.let { indexToSq(it) } ?: "-")
        sb.append(' ')
        sb.append(state.halfmove)
        sb.append(' ')
        sb.append(state.fullmove)
        return sb.toString()
    }

    fun legalMoves(state: State): List<Move> {
        val pseudo = mutableListOf<Move>()
        val side = state.whiteToMove
        for (sq in 0 until 64) {
            val p = state.board[sq]
            if (p == '.' || isWhite(p) != side) continue
            when (p.lowercaseChar()) {
                'p' -> addPawnMoves(state, sq, pseudo)
                'n' -> addKnightMoves(state, sq, pseudo)
                'b' -> addSliderMoves(state, sq, pseudo, intArrayOf(-9, -7, 7, 9))
                'r' -> addSliderMoves(state, sq, pseudo, intArrayOf(-8, -1, 1, 8))
                'q' -> addSliderMoves(state, sq, pseudo, intArrayOf(-9, -8, -7, -1, 1, 7, 8, 9))
                'k' -> addKingMoves(state, sq, pseudo)
            }
        }
        return pseudo.filter { !leavesKingInCheck(state, it) }
    }

    fun apply(state: State, move: Move): State {
        val b = state.board.copyOf()
        val piece = b[move.from]
        val captured = b[move.to]
        b[move.to] = move.promotion ?: piece
        b[move.from] = '.'

        var castling = state.castling
        var ep: Int? = null
        var half = if (piece.lowercaseChar() == 'p' || captured != '.') 0 else state.halfmove + 1
        var full = state.fullmove + if (state.whiteToMove) 0 else 1

        // En passant capture
        if (piece.lowercaseChar() == 'p' && move.to == state.enPassant) {
            val capSq = if (isWhite(piece)) move.to - 8 else move.to + 8
            b[capSq] = '.'
        }

        // Castling — move rook
        if (piece.lowercaseChar() == 'k' && kotlin.math.abs((move.from % 8) - (move.to % 8)) == 2) {
            if (move.to > move.from) { // kingside
                b[move.from + 1] = b[move.from + 3]
                b[move.from + 3] = '.'
            } else {
                b[move.from - 1] = b[move.from - 4]
                b[move.from - 4] = '.'
            }
        }

        // Update castling rights
        castling = updateCastling(castling, move.from, piece)
        castling = updateCastling(castling, move.to, captured)

        // En passant target
        if (piece.lowercaseChar() == 'p' && kotlin.math.abs((move.from / 8) - (move.to / 8)) == 2) {
            ep = (move.from + move.to) / 2
        }

        return State(b, !state.whiteToMove, castling, ep, half, full)
    }

    fun isInCheck(state: State, white: Boolean): Boolean {
        val kingSq = findKing(state.board, white) ?: return false
        return isSquareAttacked(state.board, kingSq, !white)
    }

    fun evaluateEnd(state: State): GameResult? {
        val moves = legalMoves(state)
        if (moves.isNotEmpty()) return null
        return if (isInCheck(state, state.whiteToMove)) {
            if (state.whiteToMove) GameResult.BLACK_WIN else GameResult.WHITE_WIN
        } else {
            GameResult.STALEMATE
        }
    }

    fun isWhite(piece: Char) = piece.isUpperCase()

    private fun addPawnMoves(state: State, sq: Int, out: MutableList<Move>) {
        val board = state.board
        val p = board[sq]
        val white = isWhite(p)
        val dir = if (white) 8 else -8
        val startRank = if (white) 1 else 6
        val promoRank = if (white) 7 else 0
        val one = sq + dir
        if (one in 0 until 64 && board[one] == '.') {
            if (one / 8 == promoRank) promotions(sq, one, p, out)
            else out.add(Move(sq, one))
            val two = sq + 2 * dir
            if (sq / 8 == startRank && board[two] == '.') out.add(Move(sq, two))
        }
        for (cap in intArrayOf(dir - 1, dir + 1)) {
            val t = sq + cap
            if (t !in 0 until 64 || kotlin.math.abs((sq % 8) - (t % 8)) != 1) continue
            if (board[t] != '.' && isWhite(board[t]) != white) {
                if (t / 8 == promoRank) promotions(sq, t, p, out)
                else out.add(Move(sq, t))
            } else if (t == state.enPassant) {
                out.add(Move(sq, t))
            }
        }
    }

    private fun promotions(from: Int, to: Int, piece: Char, out: MutableList<Move>) {
        val promo = if (isWhite(piece)) "QRBN" else "qrbn"
        for (ch in promo) out.add(Move(from, to, ch))
    }

    private fun addKnightMoves(state: State, sq: Int, out: MutableList<Move>) {
        val white = isWhite(state.board[sq])
        for (d in intArrayOf(-17, -15, -10, -6, 6, 10, 15, 17)) {
            val t = sq + d
            if (t in 0 until 64 && knightDist(sq, t)) {
                val target = state.board[t]
                if (target == '.' || isWhite(target) != white) out.add(Move(sq, t))
            }
        }
    }

    private fun addKingMoves(state: State, sq: Int, out: MutableList<Move>) {
        val board = state.board
        val white = isWhite(board[sq])
        val file = sq % 8
        val rank = sq / 8
        for (df in -1..1) {
            for (dr in -1..1) {
                if (df == 0 && dr == 0) continue
                val f = file + df
                val r = rank + dr
                if (f !in 0..7 || r !in 0..7) continue
                val t = r * 8 + f
                val target = board[t]
                if (target == '.' || isWhite(target) != white) out.add(Move(sq, t))
            }
        }
        // Castling
        if (isInCheck(state, white)) return
        val homeRank = if (white) 0 else 7
        val kSq = homeRank * 8 + 4
        if (sq != kSq) return
        if (white) {
            if (state.castling.contains('K') && board[kSq + 1] == '.' && board[kSq + 2] == '.' &&
                board[kSq + 3] == 'R' && !attackedAfter(state, kSq) && !attackedAfter(state, kSq + 1) &&
                !attackedAfter(state, kSq + 2)
            ) out.add(Move(kSq, kSq + 2))
            if (state.castling.contains('Q') && board[kSq - 1] == '.' && board[kSq - 2] == '.' &&
                board[kSq - 3] == '.' && board[kSq - 4] == 'R' && !attackedAfter(state, kSq) &&
                !attackedAfter(state, kSq - 1) && !attackedAfter(state, kSq - 2)
            ) out.add(Move(kSq, kSq - 2))
        } else {
            if (state.castling.contains('k') && board[kSq + 1] == '.' && board[kSq + 2] == '.' &&
                board[kSq + 3] == 'r' && !attackedAfter(state, kSq) && !attackedAfter(state, kSq + 1) &&
                !attackedAfter(state, kSq + 2)
            ) out.add(Move(kSq, kSq + 2))
            if (state.castling.contains('q') && board[kSq - 1] == '.' && board[kSq - 2] == '.' &&
                board[kSq - 3] == '.' && board[kSq - 4] == 'r' && !attackedAfter(state, kSq) &&
                !attackedAfter(state, kSq - 1) && !attackedAfter(state, kSq - 2)
            ) out.add(Move(kSq, kSq - 2))
        }
    }

    private fun attackedAfter(state: State, sq: Int): Boolean =
        isSquareAttacked(state.board, sq, !state.whiteToMove)

    private fun addSliderMoves(state: State, sq: Int, out: MutableList<Move>, dirs: IntArray) {
        val board = state.board
        val white = isWhite(board[sq])
        var file = sq % 8
        var rank = sq / 8
        for (d in dirs) {
            val (stepFile, stepRank) = rayStep(d)
            var f = file
            var r = rank
            while (true) {
                f += stepFile
                r += stepRank
                if (f !in 0..7 || r !in 0..7) break
                val t = r * 8 + f
                val target = board[t]
                if (target == '.') out.add(Move(sq, t))
                else {
                    if (isWhite(target) != white) out.add(Move(sq, t))
                    break
                }
            }
        }
    }

    private fun leavesKingInCheck(state: State, move: Move): Boolean {
        val next = apply(state, move)
        return isInCheck(next, state.whiteToMove)
    }

    private fun isSquareAttacked(board: CharArray, sq: Int, byWhite: Boolean): Boolean {
        // Pawns
        val pawnDir = if (byWhite) -8 else 8
        for (d in intArrayOf(pawnDir - 1, pawnDir + 1)) {
            val t = sq + d
            if (t in 0 until 64 && kotlin.math.abs((sq % 8) - (t % 8)) == 1) {
                val p = board[t]
                if (p != '.' && p.lowercaseChar() == 'p' && isWhite(p) == byWhite) return true
            }
        }
        // Knights
        for (d in intArrayOf(-17, -15, -10, -6, 6, 10, 15, 17)) {
            val t = sq + d
            if (t in 0 until 64 && knightDist(sq, t)) {
                val p = board[t]
                if (p != '.' && p.lowercaseChar() == 'n' && isWhite(p) == byWhite) return true
            }
        }
        // King
        for (d in intArrayOf(-9, -8, -7, -1, 1, 7, 8, 9)) {
            val t = sq + d
            if (t in 0 until 64 && kingDist(sq, t)) {
                val p = board[t]
                if (p != '.' && p.lowercaseChar() == 'k' && isWhite(p) == byWhite) return true
            }
        }
        // Sliders
        val file = sq % 8
        val rank = sq / 8
        for ((dirs, types) in arrayOf(
            intArrayOf(-9, -7, 7, 9) to "bq",
            intArrayOf(-8, -1, 1, 8) to "rq",
            intArrayOf(-9, -8, -7, -1, 1, 7, 8, 9) to "q"
        )) {
            for (d in dirs) {
                val (stepFile, stepRank) = rayStep(d)
                var f = file
                var r = rank
                while (true) {
                    f += stepFile
                    r += stepRank
                    if (f !in 0..7 || r !in 0..7) break
                    val t = r * 8 + f
                    val p = board[t]
                    if (p != '.') {
                        if (isWhite(p) == byWhite && types.contains(p.lowercaseChar())) return true
                        break
                    }
                }
            }
        }
        return false
    }

    private fun findKing(board: CharArray, white: Boolean): Int? {
        val k = if (white) 'K' else 'k'
        for (i in 0 until 64) if (board[i] == k) return i
        return null
    }

    private fun updateCastling(castling: String, sq: Int, piece: Char): String {
        var c = castling
        val file = sq % 8
        val rank = sq / 8
        when {
            piece == 'K' -> c = c.replace("K", "").replace("Q", "")
            piece == 'k' -> c = c.replace("k", "").replace("q", "")
            piece == 'R' && rank == 0 && file == 0 -> c = c.replace("Q", "")
            piece == 'R' && rank == 0 && file == 7 -> c = c.replace("K", "")
            piece == 'R' && rank == 7 && file == 0 -> c = c.replace("q", "")
            piece == 'R' && rank == 7 && file == 7 -> c = c.replace("k", "")
        }
        return c
    }

    private fun sqToIndex(sq: String): Int {
        if (sq.length != 2) return -1
        val f = sq[0] - 'a'
        val r = sq[1] - '1'
        if (f !in 0..7 || r !in 0..7) return -1
        return r * 8 + f
    }

    private fun indexToSq(i: Int): String =
        "${'a' + (i % 8)}${'1' + (i / 8)}"

    private fun knightDist(a: Int, b: Int): Boolean {
        val df = kotlin.math.abs((a % 8) - (b % 8))
        val dr = kotlin.math.abs((a / 8) - (b / 8))
        return (df == 1 && dr == 2) || (df == 2 && dr == 1)
    }

    private fun kingDist(a: Int, b: Int): Boolean {
        val df = kotlin.math.abs((a % 8) - (b % 8))
        val dr = kotlin.math.abs((a / 8) - (b / 8))
        return df <= 1 && dr <= 1
    }

    private fun rayStep(dir: Int): Pair<Int, Int> = when (dir) {
        1 -> 1 to 0
        -1 -> -1 to 0
        8 -> 0 to 1
        -8 -> 0 to -1
        9 -> 1 to 1
        -9 -> -1 to -1
        7 -> -1 to 1
        -7 -> 1 to -1
        else -> 0 to 0
    }
}