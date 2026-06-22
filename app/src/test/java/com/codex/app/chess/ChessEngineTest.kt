package com.codex.app.chess

import org.junit.Assert.*
import org.junit.Test

class ChessEngineTest {

    @Test
    fun queen_moves_all_directions_from_center() {
        val fen = "8/8/8/3Q4/8/8/8/4K2k w - - 0 1" // queen d5, white king e1, black king h1
        val state = ChessEngine.parseFen(fen)
        val qSq = 35 // d5 = rank 4 file 3 = 4*8+3=35
        val moves = ChessEngine.legalMoves(state).filter { it.from == qSq }
        val targets = moves.map { it.to }.toSet()
        // All squares on rays from d5 should be reachable (board mostly empty)
        assertTrue("Queen should move north", targets.contains(43)) // d6
        assertTrue("Queen should move south", targets.contains(27)) // d4
        assertTrue("Queen should move east", targets.contains(36)) // e5
        assertTrue("Queen should move west", targets.contains(34)) // c5
        assertTrue("Queen should move NE", targets.contains(44)) // e6
        assertTrue("Queen should move NW", targets.contains(42)) // c6
        assertTrue("Queen should move SE", targets.contains(28)) // e4
        assertTrue("Queen should move SW", targets.contains(26)) // c4
    }

    @Test
    fun queen_long_diagonal() {
        val fen = "8/8/8/8/8/8/8/Q7 w - - 0 1"
        val state = ChessEngine.parseFen(fen)
        val moves = ChessEngine.legalMoves(state).filter { it.from == 0 }
        assertTrue(moves.any { it.to == 63 }) // a1 to h8
    }

    @Test
    fun king_moves_all_adjacent() {
        val fen = "8/8/8/8/3K4/8/8/7k w - - 0 1"
        val state = ChessEngine.parseFen(fen)
        val kSq = 27 // d4
        val moves = ChessEngine.legalMoves(state).filter { it.from == kSq }
        assertEquals(8, moves.size)
    }

    @Test
    fun no_wraparound_rook() {
        val fen = "8/8/8/7R/8/8/8/4k3 w - - 0 1" // rook h4
        val state = ChessEngine.parseFen(fen)
        val rSq = 31
        val moves = ChessEngine.legalMoves(state).filter { it.from == rSq }
        assertFalse(moves.any { it.to == 32 }) // h4 to a5 wrap should not happen
    }
}
