package com.yoshi0311.orbito.viewmodel

import android.app.Application
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chaquo.python.Python
import com.yoshi0311.orbito.model.BotRepository
import com.yoshi0311.orbito.model.CellState
import com.yoshi0311.orbito.model.GamePhase
import com.yoshi0311.orbito.model.GameState
import com.yoshi0311.orbito.model.Player
import com.yoshi0311.orbito.model.ROTATION_MAPPING
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class BotEditUiState(
    val editBoard: List<List<CellState>> = List(4) { List(4) { CellState.EMPTY } },
    val botName: String = "",
    val codeText: TextFieldValue = TextFieldValue(""),
    val savedFileName: String? = null,
    val displayBoardState: GameState? = null,
    val isRunning: Boolean = false,
    val runStackSize: Int = 0,
    val toast: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val showInfoModal: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val shouldNavigateBack: Boolean = false
)

class BotEditViewModel(
    application: Application,
    private val initialBotName: String?
) : AndroidViewModel(application) {

    private val repository = BotRepository(application.filesDir)

    private val _state = MutableStateFlow(BotEditUiState())
    val state: StateFlow<BotEditUiState> = _state.asStateFlow()

    private val runStack = ArrayDeque<List<List<CellState>>>()
    private val undoStack = ArrayDeque<TextFieldValue>()
    private val redoStack = ArrayDeque<TextFieldValue>()

    private var runJob: Job? = null
    private var rotationDeferred: CompletableDeferred<Unit>? = null

    init {
        if (initialBotName != null) {
            val code = repository.loadCode(initialBotName) ?: ""
            _state.value = _state.value.copy(
                botName = initialBotName,
                codeText = TextFieldValue(code),
                savedFileName = initialBotName
            )
        }
    }

    // ── Board editing ─────────────────────────────────────────────────────────

    fun cycleCellState(row: Int, col: Int) {
        val s = _state.value
        if (s.isRunning) return
        val next = when (s.editBoard[row][col]) {
            CellState.EMPTY -> CellState.BLACK
            CellState.BLACK -> CellState.WHITE
            CellState.WHITE -> CellState.EMPTY
        }
        val newBoard = s.editBoard.mapIndexed { r, rowList ->
            rowList.mapIndexed { c, cell -> if (r == row && c == col) next else cell }
        }
        runStack.clear()
        _state.value = s.copy(editBoard = newBoard, runStackSize = 0)
    }

    fun clearBoard() {
        if (_state.value.isRunning) return
        runStack.clear()
        _state.value = _state.value.copy(
            editBoard = List(4) { List(4) { CellState.EMPTY } },
            runStackSize = 0
        )
    }

    fun backRun() {
        if (_state.value.isRunning || runStack.isEmpty()) return
        val prev = runStack.removeLast()
        _state.value = _state.value.copy(editBoard = prev, runStackSize = runStack.size)
    }

    // ── Code editing ──────────────────────────────────────────────────────────

    fun updateCode(value: TextFieldValue) {
        val prev = _state.value.codeText
        if (undoStack.size >= 20) undoStack.removeFirst()
        undoStack.addLast(prev)
        redoStack.clear()
        _state.value = _state.value.copy(
            codeText = value,
            canUndo = undoStack.isNotEmpty(),
            canRedo = false
        )
    }

    fun undoCode() {
        if (undoStack.isEmpty()) return
        val current = _state.value.codeText
        redoStack.addLast(current)
        val restored = undoStack.removeLast()
        _state.value = _state.value.copy(
            codeText = restored,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty()
        )
    }

    fun redoCode() {
        if (redoStack.isEmpty()) return
        val current = _state.value.codeText
        if (undoStack.size >= 20) undoStack.removeFirst()
        undoStack.addLast(current)
        val restored = redoStack.removeLast()
        _state.value = _state.value.copy(
            codeText = restored,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty()
        )
    }

    fun updateBotName(name: String) {
        _state.value = _state.value.copy(botName = name)
    }

    // ── Save / Delete ─────────────────────────────────────────────────────────

    fun saveBot() {
        val s = _state.value
        val name = s.botName.trim()
        val code = s.codeText.text
        val oldName = s.savedFileName

        if (name.isBlank()) {
            showToast("봇 이름을 입력하세요")
            return
        }
        if (!BotRepository.isValidName(name)) {
            showToast("이름에 사용할 수 없는 문자가 포함되어 있습니다")
            return
        }
        if (name != oldName && repository.exists(name)) {
            showToast("이미 같은 이름의 봇이 있습니다")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (oldName != null && name != oldName) {
                    repository.renameFile(oldName, name)
                }
                repository.writeCode(name, code)
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(savedFileName = name, botName = name)
                    showToast("저장 완료")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showToast("저장 실패") }
            }
        }
    }

    fun deleteBot() {
        val name = _state.value.savedFileName ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteBot(name)
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(shouldNavigateBack = true)
            }
        }
    }

    // ── Info modal ────────────────────────────────────────────────────────────

    fun showInfoModal() { _state.value = _state.value.copy(showInfoModal = true) }
    fun dismissInfoModal() { _state.value = _state.value.copy(showInfoModal = false) }

    fun showDeleteConfirm() { _state.value = _state.value.copy(showDeleteConfirm = true) }
    fun dismissDeleteConfirm() { _state.value = _state.value.copy(showDeleteConfirm = false) }

    // ── Toast ─────────────────────────────────────────────────────────────────

    fun dismissToast() { _state.value = _state.value.copy(toast = null) }
    private fun showToast(msg: String) { _state.value = _state.value.copy(toast = msg) }

    // ── RUN ───────────────────────────────────────────────────────────────────

    fun runBot() {
        val s = _state.value
        if (s.isRunning) return
        val board = s.editBoard
        val code = s.codeText.text

        val whiteCount = board.flatten().count { it == CellState.WHITE }
        val blackCount = board.flatten().count { it == CellState.BLACK }
        if (kotlin.math.abs(whiteCount - blackCount) > 1) return

        val whiteSide = (8 - whiteCount).coerceIn(0, 8)
        val blackSide = (8 - blackCount).coerceIn(0, 8)
        val stateStr = encodeBoard(board, whiteSide, blackSide)

        runStack.addLast(board)
        runJob?.cancel()
        runJob = viewModelScope.launch {
            _state.value = _state.value.copy(isRunning = true, runStackSize = runStack.size)
            val initDisplay = GameState(
                board = board,
                whiteSideCount = whiteSide,
                blackSideCount = blackSide,
                currentPlayer = Player.WHITE,
                phase = GamePhase.OPTIONAL_MOVE,
                isBotThinking = true
            )
            _state.value = _state.value.copy(displayBoardState = initDisplay)

            var botMove: BotMove? = null
            var errorMsg: String? = null
            val timedOut = withTimeoutOrNull(3000L) {
                withContext(Dispatchers.IO) {
                    try {
                        val result = Python.getInstance()
                            .getModule("executor")
                            .callAttr("run_user_code", code, stateStr)
                            .toString()
                        botMove = parseBotResponse(result)
                        if (botMove == null) errorMsg = "잘못된 반환값: $result"
                    } catch (e: Exception) {
                        errorMsg = e.message ?: "오류"
                    }
                }
            } == null

            if (timedOut) {
                showToast("3초 초과")
                finishRun(board)
                return@launch
            }
            if (errorMsg != null) {
                showToast("ERROR")
                finishRun(board)
                return@launch
            }

            animateBotMove(board, botMove!!, whiteSide)
        }
    }

    fun onRotationComplete() {
        val s = _state.value
        val display = s.displayBoardState ?: return
        if (!display.isRotating) return
        _state.value = s.copy(displayBoardState = display.copy(isRotating = false, boardBeforeRotation = null))
        rotationDeferred?.complete(Unit)
    }

    private suspend fun animateBotMove(initBoard: List<List<CellState>>, move: BotMove, initWhiteSide: Int) {
        val opponentColor = CellState.BLACK
        val ownColor = CellState.WHITE
        var board = initBoard

        fun cur() = _state.value.displayBoardState ?: GameState(board = board, currentPlayer = Player.WHITE, phase = GamePhase.OPTIONAL_MOVE)
        fun update(d: GameState) { _state.value = _state.value.copy(displayBoardState = d) }

        update(cur().copy(isBotThinking = false))

        if (move.optMove != null) {
            val (srcPos, dstPos) = move.optMove
            val srcR = srcPos / 4; val srcC = srcPos % 4
            val dstR = dstPos / 4; val dstC = dstPos % 4
            if (board[srcR][srcC] == opponentColor &&
                board[dstR][dstC] == CellState.EMPTY &&
                kotlin.math.abs(srcR - dstR) + kotlin.math.abs(srcC - dstC) == 1) {

                update(cur().copy(botHighlightCell = srcPos))
                delay(500)
                update(cur().copy(botHighlightCell = null))
                delay(100)
                update(cur().copy(botPieceMoveAnim = Pair(srcPos, dstPos)))
                delay(450)
                val nb = mutableBoard(board)
                nb[dstR][dstC] = nb[srcR][srcC]; nb[srcR][srcC] = CellState.EMPTY
                board = nb.toImmutable()
                update(cur().copy(board = board, botPieceMoveAnim = null))
                delay(150)
            }
        }

        var placePos = move.placePos
        if (placePos !in 0..15 || board[placePos / 4][placePos % 4] != CellState.EMPTY) {
            placePos = (0..15).firstOrNull { board[it / 4][it % 4] == CellState.EMPTY } ?: run {
                finishRun(board); return
            }
        }
        val nb = mutableBoard(board)
        nb[placePos / 4][placePos % 4] = ownColor
        val boardWithBall = nb.toImmutable()
        board = boardWithBall

        update(cur().copy(board = boardWithBall, botHighlightCell = placePos))
        delay(500)
        update(cur().copy(botHighlightCell = null))
        delay(500)

        val rotated = rotate(boardWithBall)
        val ver = cur().rotationVersion + 1
        update(cur().copy(
            board = rotated,
            boardBeforeRotation = boardWithBall,
            whiteSideCount = (initWhiteSide - 1).coerceAtLeast(0),
            isRotating = true,
            rotationVersion = ver,
            botHighlightCell = null
        ))

        rotationDeferred = CompletableDeferred()
        withTimeoutOrNull(1000L) { rotationDeferred?.await() }
        rotationDeferred = null

        finishRun(rotated)
    }

    private fun finishRun(finalBoard: List<List<CellState>>) {
        _state.value = _state.value.copy(
            editBoard = finalBoard,
            displayBoardState = null,
            isRunning = false
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun encodeBoard(board: List<List<CellState>>, whiteSide: Int, blackSide: Int): String {
        val cells = board.flatten().joinToString(",") {
            when (it) { CellState.WHITE -> "w"; CellState.BLACK -> "b"; else -> "" }
        }
        return "[$cells]/$whiteSide/$blackSide/w"
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

    private fun rotate(board: List<List<CellState>>): List<List<CellState>> {
        val new = mutableBoard(board)
        for ((src, dst) in ROTATION_MAPPING) new[dst.first][dst.second] = board[src.first][src.second]
        return new.toImmutable()
    }

    private fun mutableBoard(b: List<List<CellState>>) = b.map { it.toMutableList() }.toMutableList()
    private fun List<MutableList<CellState>>.toImmutable() = map { it.toList() }

    private data class BotMove(val optMove: Pair<Int, Int>?, val placePos: Int)

    companion object {
        fun factory(initialBotName: String?) = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                BotEditViewModel(app, initialBotName)
            }
        }
    }
}
