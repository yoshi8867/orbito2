package com.yoshi0311.orbito.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chaquo.python.Python
import com.yoshi0311.orbito.model.CellState
import com.yoshi0311.orbito.model.GameConfig
import com.yoshi0311.orbito.model.GamePhase
import com.yoshi0311.orbito.model.GameState
import com.yoshi0311.orbito.model.Player
import com.yoshi0311.orbito.model.PlayerType
import com.yoshi0311.orbito.model.ROTATION_MAPPING
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class GameViewModel(
    private val config: GameConfig = GameConfig()
) : ViewModel() {

    companion object {
        fun factory(config: GameConfig) = viewModelFactory {
            initializer { GameViewModel(config) }
        }
    }

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    private var timerJob: Job? = null
    private var botJob: Job? = null
    private var _pendingBotMove: BotMove? = null
    private var _pendingBotName: String = ""

    init {
        if (config.typeFor(Player.BLACK) == PlayerType.HUMAN) startTimer() else triggerBot()
    }

    fun onCellTap(row: Int, col: Int) {
        val s = _state.value
        if (s.isRotating || s.isBotThinking || s.botMoveReady) return
        if (config.typeFor(s.currentPlayer) == PlayerType.BOT) return
        when (s.phase) {
            GamePhase.OPTIONAL_MOVE -> handleOptionalMove(s, row, col)
            GamePhase.PLACE -> handlePlace(s, row, col)
            GamePhase.DONE -> {}
        }
    }

    fun restart() {
        timerJob?.cancel()
        botJob?.cancel()
        _pendingBotMove = null
        _pendingBotName = ""
        _state.value = GameState()
        if (config.typeFor(Player.BLACK) == PlayerType.HUMAN) startTimer() else triggerBot()
    }

    fun onNextPressed() {
        val s = _state.value
        if (!s.botMoveReady || s.isRotating || s.isBotThinking) return
        val move = _pendingBotMove ?: return
        val name = _pendingBotName
        _pendingBotMove = null
        _pendingBotName = ""
        _state.value = s.copy(botMoveReady = false, isBotThinking = true)
        botJob?.cancel()
        botJob = viewModelScope.launch { animateBotMove(move, name) }
    }

    fun onRotationComplete() {
        val s = _state.value
        if (!s.isRotating) return
        if (s.phase == GamePhase.DONE) {
            _state.value = s.copy(isRotating = false, boardBeforeRotation = null)
            return
        }
        val winner = checkWinner(s.board)
        val nextPlayer = if (s.currentPlayer == Player.WHITE) Player.BLACK else Player.WHITE
        _state.value = s.copy(
            isRotating = false,
            boardBeforeRotation = null,
            currentPlayer = nextPlayer,
            phase = if (winner != null) GamePhase.DONE else GamePhase.OPTIONAL_MOVE,
            winner = winner
        )
        logState("ROTATION_COMPLETE→${nextPlayer}")
        if (winner == null) {
            if (config.typeFor(nextPlayer) == PlayerType.HUMAN) startTimer() else triggerBot()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        _state.value = _state.value.copy(timeLeft = 20)
        timerJob = viewModelScope.launch {
            for (remaining in 19 downTo 0) {
                delay(1000L)
                val s = _state.value
                if (s.phase == GamePhase.DONE) return@launch
                _state.value = s.copy(timeLeft = remaining)
                if (remaining == 0) {
                    val winner = if (s.currentPlayer == Player.WHITE) Player.BLACK else Player.WHITE
                    _state.value = _state.value.copy(phase = GamePhase.DONE, winner = winner)
                    return@launch
                }
            }
        }
    }

    private fun triggerBot() {
        val s = _state.value
        val botConfig = config.botFor(s.currentPlayer) ?: return
        _state.value = s.copy(isBotThinking = true, botMoveReady = false)
        logState("BOT_TRIGGER(${s.currentPlayer})")

        botJob?.cancel()
        botJob = viewModelScope.launch {
            val stateStr = encodeState(_state.value, _state.value.currentPlayer)
            var pythonException: String? = null
            var rawResponse: String? = null

            val didTimeout = withTimeoutOrNull(2000L) {
                withContext(Dispatchers.IO) {
                    try {
                        rawResponse = Python.getInstance()
                            .getModule("script")
                            .callAttr("move", stateStr)
                            .toString()
                    } catch (e: Exception) {
                        pythonException = e.message ?: "unknown"
                    }
                }
            } == null

            val botMove: BotMove? = when {
                didTimeout -> { addLog("[${botConfig.name}] timeout → random"); null }
                pythonException != null -> { addLog("[${botConfig.name}] error: $pythonException → random"); null }
                rawResponse != null -> parseBotResponse(rawResponse!!) ?: run {
                    addLog("[${botConfig.name}] parse error: $rawResponse → random"); null
                }
                else -> null
            }

            _pendingBotMove = botMove ?: randomMove(_state.value)
            _pendingBotName = botConfig.name
            _state.value = _state.value.copy(isBotThinking = false, botMoveReady = true)
            logState("BOT_READY(${_state.value.currentPlayer})")
        }
    }

    private suspend fun animateBotMove(move: BotMove, botName: String) {
        Log.d("OrbitoState", "[BOT_RESPONSE] opt=${move.optMove} place=${move.placePos}")
        val player = _state.value.currentPlayer
        val opponentColor = if (player == Player.WHITE) CellState.BLACK else CellState.WHITE
        val ownColor = if (player == Player.WHITE) CellState.WHITE else CellState.BLACK

        // --- optional move: flash → animate → commit ---
        if (move.optMove != null) {
            val (srcPos, dstPos) = move.optMove
            val srcRow = srcPos / 4; val srcCol = srcPos % 4
            val dstRow = dstPos / 4; val dstCol = dstPos % 4
            val board = _state.value.board
            if (board[srcRow][srcCol] == opponentColor &&
                board[dstRow][dstCol] == CellState.EMPTY &&
                isAdjacent(Pair(srcRow, srcCol), dstRow, dstCol)
            ) {
                _state.value = _state.value.copy(botHighlightCell = srcPos)
                delay(500)
                _state.value = _state.value.copy(botHighlightCell = null)
                delay(100)

                _state.value = _state.value.copy(botPieceMoveAnim = Pair(srcPos, dstPos))
                delay(450)

                val newBoard = mutableBoard(_state.value.board)
                newBoard[dstRow][dstCol] = newBoard[srcRow][srcCol]
                newBoard[srcRow][srcCol] = CellState.EMPTY
                _state.value = _state.value.copy(board = newBoard.toImmutable(), botPieceMoveAnim = null)
                delay(150)
            } else {
                addLog("[$botName] invalid optional move → skipped")
            }
        }

        // --- placement: show ball → wait 1s → flash → rotation ---
        var placePos = move.placePos
        if (_state.value.board[placePos / 4][placePos % 4] != CellState.EMPTY) {
            val fallback = (0..15).firstOrNull { _state.value.board[it / 4][it % 4] == CellState.EMPTY }
            if (fallback == null) return
            addLog("[$botName] invalid placement → fallback")
            placePos = fallback
        }

        val newBoard = mutableBoard(_state.value.board)
        newBoard[placePos / 4][placePos % 4] = ownColor
        val boardWithBall = newBoard.toImmutable()
        _state.value = _state.value.copy(board = boardWithBall)

        _state.value = _state.value.copy(botHighlightCell = placePos)
        delay(500)
        _state.value = _state.value.copy(botHighlightCell = null)
        delay(500)

        val s = _state.value
        timerJob?.cancel()
        _state.value = s.copy(
            board = rotate(boardWithBall),
            boardBeforeRotation = boardWithBall,
            whiteSideCount = if (player == Player.WHITE) s.whiteSideCount - 1 else s.whiteSideCount,
            blackSideCount = if (player == Player.BLACK) s.blackSideCount - 1 else s.blackSideCount,
            isRotating = true,
            isBotThinking = false,
            rotationVersion = s.rotationVersion + 1,
            botHighlightCell = null
        )
        logState("PLACED(${player}@${placePos})")
    }

    private fun handleOptionalMove(s: GameState, row: Int, col: Int) {
        val opponentColor = if (s.currentPlayer == Player.WHITE) CellState.BLACK else CellState.WHITE
        val sel = s.selectedCell
        when {
            sel != null && sel.first == row && sel.second == col ->
                _state.value = s.copy(selectedCell = null)
            sel != null && s.board[row][col] == CellState.EMPTY && isAdjacent(sel, row, col) -> {
                val newBoard = mutableBoard(s.board)
                newBoard[row][col] = newBoard[sel.first][sel.second]
                newBoard[sel.first][sel.second] = CellState.EMPTY
                _state.value = s.copy(board = newBoard.toImmutable(), selectedCell = null, phase = GamePhase.PLACE)
            }
            s.board[row][col] == opponentColor ->
                _state.value = s.copy(selectedCell = Pair(row, col))
            s.board[row][col] == CellState.EMPTY ->
                handlePlace(s.copy(selectedCell = null), row, col)
        }
    }

    private fun handlePlace(s: GameState, row: Int, col: Int) {
        if (s.board[row][col] != CellState.EMPTY) return
        val sideCount = if (s.currentPlayer == Player.WHITE) s.whiteSideCount else s.blackSideCount
        if (sideCount <= 0) return
        placeOnBoard(s, row, col)
    }

    private fun placeOnBoard(s: GameState, row: Int, col: Int) {
        val ownColor = if (s.currentPlayer == Player.WHITE) CellState.WHITE else CellState.BLACK
        val newBoard = mutableBoard(s.board)
        newBoard[row][col] = ownColor
        val boardAfterPlace = newBoard.toImmutable()
        timerJob?.cancel()
        _state.value = s.copy(
            board = rotate(boardAfterPlace),
            boardBeforeRotation = boardAfterPlace,
            whiteSideCount = if (s.currentPlayer == Player.WHITE) s.whiteSideCount - 1 else s.whiteSideCount,
            blackSideCount = if (s.currentPlayer == Player.BLACK) s.blackSideCount - 1 else s.blackSideCount,
            isRotating = true,
            isBotThinking = false,
            rotationVersion = s.rotationVersion + 1
        )
        logState("PLACED(${s.currentPlayer}@${row*4+col})")
    }

    private fun encodeState(s: GameState, myColor: Player): String {
        val cells = s.board.flatten().joinToString(",") {
            when (it) { CellState.WHITE -> "w"; CellState.BLACK -> "b"; else -> "" }
        }
        val colorStr = if (myColor == Player.WHITE) "w" else "b"
        return "[$cells]/${s.whiteSideCount}/${s.blackSideCount}/$colorStr"
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

    private fun randomMove(s: GameState): BotMove {
        val emptyCells = (0..15).filter { s.board[it / 4][it % 4] == CellState.EMPTY }
        return BotMove(null, emptyCells.randomOrNull() ?: 0)
    }

    private fun addLog(msg: String) {
        Log.d("OrbitoBot", msg)
        _state.value = _state.value.copy(logs = _state.value.logs + msg)
    }

    private fun logState(tag: String) {
        val s = _state.value
        val boardStr = s.board.mapIndexed { r, row ->
            row.mapIndexed { c, cell ->
                val sym = when (cell) { CellState.WHITE -> "W"; CellState.BLACK -> "B"; else -> "." }
                "${r * 4 + c}:$sym"
            }.joinToString(" ")
        }.joinToString(" | ")
        Log.d("OrbitoState", "[$tag] player=${s.currentPlayer} phase=${s.phase} rotating=${s.isRotating} botThinking=${s.isBotThinking} W=${s.whiteSideCount} B=${s.blackSideCount}")
        Log.d("OrbitoState", "[$tag] board: $boardStr")
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
}
