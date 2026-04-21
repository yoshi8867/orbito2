package com.yoshi0311.orbito.model

data class BatchState(
    val config: GameConfig,
    val whiteWins: Int = 0,
    val blackWins: Int = 0,
    val currentGame: Int = 0,
    val isRunning: Boolean = false,
    val isDone: Boolean = false,
    val board: List<List<CellState>> = List(4) { List(4) { CellState.EMPTY } }
)
