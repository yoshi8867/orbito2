package com.yoshi0311.orbito.model

enum class Player { WHITE, BLACK }
enum class CellState { EMPTY, WHITE, BLACK }
enum class GamePhase { OPTIONAL_MOVE, PLACE, DONE }

data class GameState(
    val board: List<List<CellState>> = List(4) { List(4) { CellState.EMPTY } },
    val whiteSideCount: Int = 8,
    val blackSideCount: Int = 8,
    val currentPlayer: Player = Player.BLACK,
    val phase: GamePhase = GamePhase.OPTIONAL_MOVE,
    val selectedCell: Pair<Int, Int>? = null,
    val winner: Player? = null,
    val isRotating: Boolean = false,
    val boardBeforeRotation: List<List<CellState>>? = null,
    val timeLeft: Int = 20,
    val isBotThinking: Boolean = false,
    val logs: List<String> = emptyList(),
    val rotationVersion: Int = 0,
    val botHighlightCell: Int? = null,
    val botPieceMoveAnim: Pair<Int, Int>? = null,
    val botMoveReady: Boolean = false
)
