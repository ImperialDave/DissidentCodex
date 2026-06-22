package com.codex.app.chess

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import com.codex.app.R

/**
 * Single-view chess board — 64 squares drawn on canvas, no per-square child views.
 * Piece drawables are cached once to minimize allocations.
 */
class ChessBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onSquareTapped(square: Int)
    }

    var listener: Listener? = null
    var flipped: Boolean = false
        set(value) { field = value; invalidate() }

    private var state: ChessEngine.State = ChessEngine.parseFen(ChessEngine.START_FEN)
    private var selected: Int = -1
    private var legalTargets: Set<Int> = emptySet()

    private val lightPaint = Paint().apply { color = 0xFFE8DCC8.toInt() }
    private val darkPaint = Paint().apply { color = 0xFF7A9458.toInt() }
    private val selectPaint = Paint().apply { color = 0x88FFEB3B.toInt() }
    private val targetPaint = Paint().apply {
        color = 0x8800BCD4.toInt()
        style = Paint.Style.FILL
    }
    private val checkPaint = Paint().apply { color = 0x88F44336.toInt() }

    private val pieceBounds = Rect()
    private val drawableCache = mutableMapOf<Char, Drawable?>()

    fun setPosition(fen: String) {
        state = ChessEngine.parseFen(fen)
        invalidate()
    }

    fun setSelection(square: Int, legal: Set<Int>) {
        selected = square
        legalTargets = legal
        invalidate()
    }

    fun clearSelection() {
        selected = -1
        legalTargets = emptySet()
        invalidate()
    }

    fun squareAt(x: Float, y: Float): Int {
        val sq = squareSize()
        if (sq <= 0) return -1
        val col = (x / sq).toInt().coerceIn(0, 7)
        val row = (y / sq).toInt().coerceIn(0, 7)
        val file = if (flipped) 7 - col else col
        val rank = if (flipped) row else 7 - row
        return rank * 8 + file
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val size = minOf(w, h)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        val sq = squareSize()
        if (sq <= 0) return

        val inCheck = ChessEngine.isInCheck(state, state.whiteToMove)
        val kingSq = if (inCheck) findKingSquare(state.whiteToMove) else -1

        for (rank in 0 until 8) {
            for (file in 0 until 8) {
                val boardSq = rank * 8 + file
                val visCol = if (flipped) 7 - file else file
                val visRow = if (flipped) rank else 7 - rank
                val left = visCol * sq
                val top = visRow * sq
                val light = (file + rank) % 2 == 0
                canvas.drawRect(
                    left.toFloat(), top.toFloat(),
                    (left + sq).toFloat(), (top + sq).toFloat(),
                    if (light) lightPaint else darkPaint
                )
                if (boardSq == selected) {
                    canvas.drawRect(
                        left.toFloat(), top.toFloat(),
                        (left + sq).toFloat(), (top + sq).toFloat(),
                        selectPaint
                    )
                }
                if (boardSq in legalTargets) {
                    val pad = sq * 0.3f
                    canvas.drawCircle(
                        left + sq / 2f, top + sq / 2f, sq / 2f - pad, targetPaint
                    )
                }
                if (boardSq == kingSq) {
                    canvas.drawRect(
                        left.toFloat(), top.toFloat(),
                        (left + sq).toFloat(), (top + sq).toFloat(),
                        checkPaint
                    )
                }
                val piece = state.board[boardSq]
                if (piece != '.') {
                    drawPiece(canvas, piece, left, top, sq)
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        if (event.action == MotionEvent.ACTION_UP) {
            val sq = squareAt(event.x, event.y)
            if (sq >= 0) listener?.onSquareTapped(sq)
        }
        return true
    }

    private fun drawPiece(canvas: Canvas, piece: Char, left: Int, top: Int, sq: Int) {
        val drawable = pieceDrawable(piece) ?: return
        val pad = (sq * 0.1f).toInt()
        pieceBounds.set(left + pad, top + pad, left + sq - pad, top + sq - pad)
        drawable.bounds = pieceBounds
        drawable.draw(canvas)
    }

    private fun pieceDrawable(piece: Char): Drawable? {
        return drawableCache.getOrPut(piece) {
            val id = ChessPieces.drawableFor(piece) ?: return@getOrPut null
            AppCompatResources.getDrawable(context, id)?.mutate()?.also {
                DrawableCompat.setTintList(it, null)
            }
        }
    }

    private fun squareSize(): Int = width / 8

    private fun findKingSquare(white: Boolean): Int {
        val k = if (white) 'K' else 'k'
        for (i in 0 until 64) if (state.board[i] == k) return i
        return -1
    }
}