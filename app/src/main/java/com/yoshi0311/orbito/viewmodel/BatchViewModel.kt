package com.yoshi0311.orbito.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chaquo.python.Python
import com.yoshi0311.orbito.model.BatchState
import com.yoshi0311.orbito.model.CellState
import com.yoshi0311.orbito.model.GameConfig
import com.yoshi0311.orbito.model.Player
import com.yoshi0311.orbito.model.ROTATION_MAPPING
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BatchViewModel(initialConfig: GameConfig) : ViewModel() {

    companion object {
        fun factory(config: GameConfig) = viewModelFactory {
            initializer { BatchViewModel(config) }
        }
    }

    private val _state = MutableStateFlow(BatchState(config = initialConfig))
    val state: StateFlow<BatchState> = _state.asStateFlow()

    private var batchJob: Job? = null

    init { runBatch() }

    fun restart(newConfig: GameConfig = _state.value.config) {
        batchJob?.cancel()
        _state.value = BatchState(config = newConfig)
        runBatch()
    }

    private fun runBatch() {
        val config = _state.value.config
        val total = config.batchCount
        batchJob = viewModelScope.launch {
            _state.value = _state.value.copy(isRunning = true)
            repeat(total) { i ->
                val firstPlayer = if (i % 2 == 0) Player.BLACK else Player.WHITE
                val result = simulateGame(config, firstPlayer)
                _state.value = _state.value.let { s ->
                    s.copy(
                        currentGame = i + 1,
                        whiteWins = if (result.winner == Player.WHITE) s.whiteWins + 1 else s.whiteWins,
                        blackWins = if (result.winner == Player.BLACK) s.blackWins + 1 else s.blackWins,
                        board = result.board
                    )
                }
                if (i < total - 1) delay(350L)
            }
            _state.value = _state.value.copy(isRunning = false, isDone = true)
        }
    }

    private suspend fun simulateGame(config: GameConfig, firstPlayer: Player = Player.BLACK): SimResult {
        var board: List<List<CellState>> = List(4) { List(4) { CellState.EMPTY } }
        var whiteSide = 8
        var blackSide = 8
        var currentPlayer = firstPlayer

        repeat(200) {
            val stateStr = encodeState(board, whiteSide, blackSide, currentPlayer)
            val botId = config.botFor(currentPlayer)?.id ?: ""
            val funcName = if (botId == "smart_bot") "smart_move" else "move"
            val rawResponse = withContext(Dispatchers.IO) {
                try { Python.getInstance().getModule("script").callAttr(funcName, stateStr).toString() }
                catch (e: Exception) { null }
            }
            val move = rawResponse?.let { parseBotResponse(it) } ?: randomMove(board)

            val opponentColor = if (currentPlayer == Player.WHITE) CellState.BLACK else CellState.WHITE
            val ownColor = if (currentPlayer == Player.WHITE) CellState.WHITE else CellState.BLACK

            if (move.optMove != null) {
                val (src, dst) = move.optMove
                val sr = src / 4; val sc = src % 4; val dr = dst / 4; val dc = dst % 4
                if (board[sr][sc] == opponentColor && board[dr][dc] == CellState.EMPTY &&
                    isAdjacent(Pair(sr, sc), dr, dc)
                ) {
                    val nb = mutableBoard(board)
                    nb[dr][dc] = nb[sr][sc]; nb[sr][sc] = CellState.EMPTY
                    board = nb.toImmutable()
                }
            }

            var placePos = move.placePos
            if (board[placePos / 4][placePos % 4] != CellState.EMPTY) {
                placePos = (0..15).firstOrNull { board[it / 4][it % 4] == CellState.EMPTY }
                    ?: return SimResult(currentPlayer, board)
            }
            val nb = mutableBoard(board)
            nb[placePos / 4][placePos % 4] = ownColor
            board = nb.toImmutable()

            if (currentPlayer == Player.WHITE) whiteSide-- else blackSide--
            board = rotate(board)

            checkWinner(board)?.let { return SimResult(it, board) }
            currentPlayer = if (currentPlayer == Player.WHITE) Player.BLACK else Player.WHITE
        }
        return SimResult(currentPlayer, board)
    }

    private fun encodeState(board: List<List<CellState>>, whiteSide: Int, blackSide: Int, player: Player): String {
        val cells = board.flatten().joinToString(",") {
            when (it) { CellState.WHITE -> "w"; CellState.BLACK -> "b"; else -> "" }
        }
        return "[$cells]/$whiteSide/$blackSide/${if (player == Player.WHITE) "w" else "b"}"
    }

    private fun parseBotResponse(response: String): BotMove? {
        val parts = response.trim().split("/")
        if (parts.size != 2) return null
        val placePos = parts[1].trim().toIntOrNull()?.takeIf { it in 0..15 } ?: return null
        val optMove = if (parts[0].trim() == "skip") null else {
            val mp = parts[0].trim().split(">")
            if (mp.size != 2) return null
            val src = mp[0].trim().toIntOrNull()?.takeIf { it in 0..15 } ?: return null
            val dst = mp[1].trim().toIntOrNull()?.takeIf { it in 0..15 } ?: return null
            Pair(src, dst)
        }
        return BotMove(optMove, placePos)
    }

    private fun randomMove(board: List<List<CellState>>): BotMove {
        val empty = (0..15).filter { board[it / 4][it % 4] == CellState.EMPTY }
        return BotMove(null, empty.randomOrNull() ?: 0)
    }

    private fun rotate(board: List<List<CellState>>): List<List<CellState>> {
        val new = mutableBoard(board)
        for ((src, dst) in ROTATION_MAPPING) new[dst.first][dst.second] = board[src.first][src.second]
        return new.toImmutable()
    }

    private fun checkWinner(board: List<List<CellState>>): Player? {
        fun CellState.toPlayer() = if (this == CellState.WHITE) Player.WHITE else Player.BLACK
        for (r in 0..3) {
            val c = board[r][0]
            if (c != CellState.EMPTY && board[r].all { it == c }) return c.toPlayer()
        }
        for (col in 0..3) {
            val c = board[0][col]
            if (c != CellState.EMPTY && (0..3).all { board[it][col] == c }) return c.toPlayer()
        }
        val d1 = board[0][0]
        if (d1 != CellState.EMPTY && (0..3).all { board[it][it] == d1 }) return d1.toPlayer()
        val d2 = board[0][3]
        if (d2 != CellState.EMPTY && (0..3).all { board[it][3 - it] == d2 }) return d2.toPlayer()
        return null
    }

    private fun isAdjacent(from: Pair<Int, Int>, toRow: Int, toCol: Int) =
        kotlin.math.abs(from.first - toRow) + kotlin.math.abs(from.second - toCol) == 1

    private fun mutableBoard(board: List<List<CellState>>) = board.map { it.toMutableList() }.toMutableList()
    private fun List<MutableList<CellState>>.toImmutable() = map { it.toList() }

    private data class BotMove(val optMove: Pair<Int, Int>?, val placePos: Int)
    private data class SimResult(val winner: Player, val board: List<List<CellState>>)
}
