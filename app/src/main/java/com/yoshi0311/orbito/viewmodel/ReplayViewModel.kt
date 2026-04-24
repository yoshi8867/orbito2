package com.yoshi0311.orbito.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yoshi0311.orbito.model.CellState
import com.yoshi0311.orbito.model.GamePhase
import com.yoshi0311.orbito.model.GameState
import com.yoshi0311.orbito.model.Player
import com.yoshi0311.orbito.model.ROTATION_MAPPING
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ReplayUiState(
    val record: String = "",
    val moveLines: List<String> = emptyList(),
    // displays[k] = board after k moves with all rotations applied (starting board for move k+1)
    val displays: List<List<List<CellState>>> = listOf(List(4) { List(4) { CellState.EMPTY } }),
    // sideCountStates[k] = (whiteSide, blackSide) after k moves
    val sideCountStates: List<Pair<Int, Int>> = listOf(Pair(8, 8)),
    val currentIndex: Int = 0,
    val displayBoardState: GameState? = null,
    val isAnimating: Boolean = false,
    val toast: String? = null
)

class ReplayViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("orbito_prefs", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(ReplayUiState())
    val state: StateFlow<ReplayUiState> = _state.asStateFlow()
    private var rotationDeferred: CompletableDeferred<Unit>? = null

    init { loadRecord() }

    fun loadRecord() {
        val record = prefs.getString("last_game_record", "") ?: ""
        val (moveLines, displays, sides) = parseRecord(record)
        _state.value = ReplayUiState(
            record = record,
            moveLines = moveLines,
            displays = displays,
            sideCountStates = sides,
            currentIndex = 0,
            displayBoardState = makeDisplay(displays.first(), sides.first())
        )
    }

    fun clearRecord() {
        prefs.edit().remove("last_game_record").apply()
        val empty = listOf(emptyBoard())
        _state.value = ReplayUiState(
            record = "",
            moveLines = emptyList(),
            displays = empty,
            sideCountStates = listOf(Pair(8, 8)),
            currentIndex = 0,
            displayBoardState = makeDisplay(empty.first(), Pair(8, 8))
        )
    }

    fun dismissToast() { _state.value = _state.value.copy(toast = null) }

    fun goFirst() {
        val s = _state.value
        if (s.isAnimating) return
        _state.value = s.copy(
            currentIndex = 0,
            displayBoardState = makeDisplay(s.displays.first(), s.sideCountStates.first())
        )
    }

    fun goPrev() {
        val s = _state.value
        if (s.isAnimating || s.currentIndex <= 0) return
        val idx = s.currentIndex - 1
        _state.value = s.copy(
            currentIndex = idx,
            displayBoardState = makeDisplay(s.displays[idx], s.sideCountStates[idx])
        )
    }

    fun goNext() {
        val s = _state.value
        if (s.isAnimating || s.currentIndex >= s.moveLines.size) return
        val moveIdx = s.currentIndex
        val startBoard = s.displays[moveIdx]
        val startSides = s.sideCountStates[moveIdx]
        val endSides = s.sideCountStates.getOrElse(moveIdx + 1) { Pair(0, 0) }

        val info = parseMoveInfo(s.moveLines[moveIdx]) ?: run {
            val ni = moveIdx + 1
            _state.value = s.copy(
                currentIndex = ni,
                displayBoardState = makeDisplay(s.displays.getOrElse(ni) { emptyBoard() }, endSides)
            )
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isAnimating = true)
            var board = startBoard
            fun cur() = _state.value.displayBoardState ?: makeDisplay(board, startSides)
            fun upd(d: GameState) { _state.value = _state.value.copy(displayBoardState = d) }

            upd(makeDisplay(board, startSides))

            // Optional move animation
            if (info.optMove != null) {
                val (src, dst) = info.optMove
                val sr = src / 4; val sc = src % 4; val dr = dst / 4; val dc = dst % 4
                if (board[sr][sc] != CellState.EMPTY && board[dr][dc] == CellState.EMPTY &&
                    kotlin.math.abs(sr - dr) + kotlin.math.abs(sc - dc) == 1
                ) {
                    upd(cur().copy(botHighlightCell = src)); delay(500)
                    upd(cur().copy(botHighlightCell = null)); delay(100)
                    upd(cur().copy(botPieceMoveAnim = Pair(src, dst))); delay(450)
                    val nb = mutableBoard(board)
                    nb[dr][dc] = nb[sr][sc]; nb[sr][sc] = CellState.EMPTY
                    board = nb.toImmutable()
                    upd(cur().copy(board = board, botPieceMoveAnim = null)); delay(150)
                }
            }

            // Placement animation
            val color = if (info.isWhite) CellState.WHITE else CellState.BLACK
            val p = info.placePos.takeIf { it in 0..15 && board[it / 4][it % 4] == CellState.EMPTY }
                ?: (0..15).firstOrNull { board[it / 4][it % 4] == CellState.EMPTY }
            if (p != null) {
                val nb = mutableBoard(board)
                nb[p / 4][p % 4] = color; board = nb.toImmutable()
                upd(cur().copy(board = board, botHighlightCell = p)); delay(500)
                upd(cur().copy(botHighlightCell = null)); delay(500)
            }

            // Rotation animation
            val boardBeforeRot = board
            val rotated = rotate(board)
            upd(
                cur().copy(
                    board = rotated,
                    boardBeforeRotation = boardBeforeRot,
                    isRotating = true,
                    rotationVersion = cur().rotationVersion + 1,
                    whiteSideCount = endSides.first,
                    blackSideCount = endSides.second,
                    botHighlightCell = null
                )
            )
            rotationDeferred = CompletableDeferred()
            withTimeoutOrNull(1000L) { rotationDeferred?.await() }
            rotationDeferred = null

            val ni = moveIdx + 1
            _state.value = _state.value.copy(
                currentIndex = ni,
                isAnimating = false,
                displayBoardState = makeDisplay(s.displays.getOrElse(ni) { rotated }, endSides)
            )
        }
    }

    fun goLast() {
        val s = _state.value
        if (s.isAnimating) return
        val ni = s.moveLines.size
        _state.value = s.copy(
            currentIndex = ni,
            displayBoardState = makeDisplay(s.displays.last(), s.sideCountStates.last())
        )
    }

    fun onRotationComplete() {
        val d = _state.value.displayBoardState ?: return
        if (!d.isRotating) return
        _state.value = _state.value.copy(displayBoardState = d.copy(isRotating = false, boardBeforeRotation = null))
        rotationDeferred?.complete(Unit)
    }

    fun loadFromString(content: String) {
        val (moveLines, displays, sides) = parseRecord(content)
        if (moveLines.isEmpty()) {
            _state.value = _state.value.copy(
                record = content,
                moveLines = emptyList(),
                displays = listOf(emptyBoard()),
                sideCountStates = listOf(Pair(8, 8)),
                currentIndex = 0,
                displayBoardState = makeDisplay(emptyBoard(), Pair(8, 8)),
                toast = "invalid game record"
            )
            return
        }
        _state.value = ReplayUiState(
            record = content,
            moveLines = moveLines,
            displays = displays,
            sideCountStates = sides,
            currentIndex = 0,
            displayBoardState = makeDisplay(displays.first(), sides.first())
        )
    }

    fun getDefaultFileName(): String {
        val lines = _state.value.record.lines()
        val white = lines.firstOrNull { it.startsWith("w: ") }?.removePrefix("w: ")?.trim() ?: "white"
        val black = lines.firstOrNull { it.startsWith("b: ") }?.removePrefix("b: ")?.trim() ?: "black"
        val sdf = SimpleDateFormat("yyyy-MM-dd(HHmm)", Locale.getDefault())
        return "orbit${sdf.format(Date())}${white.replace(" ", "_")}_vs_${black.replace(" ", "_")}.txt"
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    private data class MoveInfo(val isWhite: Boolean, val optMove: Pair<Int, Int>?, val placePos: Int)

    private fun parseRecord(
        record: String
    ): Triple<List<String>, List<List<List<CellState>>>, List<Pair<Int, Int>>> {
        val lines = record.lines()
        val di = lines.indexOfFirst { it.trim() == "---" }
        val moveLines = if (di >= 0) lines.drop(di + 1).filter { it.isNotBlank() } else emptyList()

        val displays = mutableListOf<List<List<CellState>>>()
        val sides = mutableListOf<Pair<Int, Int>>()
        var current = emptyBoard(); var wm = 0; var bm = 0
        displays.add(current); sides.add(Pair(8, 8))

        for (line in moveLines) {
            val info = parseMoveInfo(line)
            if (info == null) {
                // Invalid line: keep board unchanged, still advance index
                displays.add(current); sides.add(Pair(8 - wm, 8 - bm))
                continue
            }
            val color = if (info.isWhite) CellState.WHITE else CellState.BLACK
            var board = current

            if (info.optMove != null) {
                val (src, dst) = info.optMove
                if (src in 0..15 && dst in 0..15 &&
                    board[src / 4][src % 4] != CellState.EMPTY &&
                    board[dst / 4][dst % 4] == CellState.EMPTY
                ) {
                    board = applyOptMove(board, src, dst)
                }
            }
            if (info.placePos in 0..15 && board[info.placePos / 4][info.placePos % 4] == CellState.EMPTY) {
                val nb = mutableBoard(board)
                nb[info.placePos / 4][info.placePos % 4] = color; board = nb.toImmutable()
            }

            if (info.isWhite) wm++ else bm++
            current = rotate(board)
            displays.add(current); sides.add(Pair(8 - wm, 8 - bm))
        }

        return Triple(moveLines, displays, sides)
    }

    private fun parseMoveInfo(line: String): MoveInfo? {
        val ci = line.indexOf(':'); if (ci < 0) return null
        val isWhite = line.substring(0, ci).trim() == "w"
        val raw = line.substring(ci + 1).trim().split(" #")[0].trim()
        return when {
            raw.contains(">") -> {
                val si = raw.indexOf('/'); if (si < 0) return null
                val place = raw.substring(si + 1).trim().toIntOrNull()?.takeIf { it in 0..15 } ?: return null
                val op = raw.substring(0, si).trim().split(">")
                if (op.size != 2) return null
                val src = op[0].trim().toIntOrNull()?.takeIf { it in 0..15 } ?: return null
                val dst = op[1].trim().toIntOrNull()?.takeIf { it in 0..15 } ?: return null
                MoveInfo(isWhite, Pair(src, dst), place)
            }
            raw.contains("/") -> {
                // backward compat: "skip/place"
                val place = raw.split("/").getOrNull(1)?.trim()?.toIntOrNull()?.takeIf { it in 0..15 } ?: return null
                MoveInfo(isWhite, null, place)
            }
            else -> {
                val place = raw.toIntOrNull()?.takeIf { it in 0..15 } ?: return null
                MoveInfo(isWhite, null, place)
            }
        }
    }

    // ── Board helpers ─────────────────────────────────────────────────────────

    private fun makeDisplay(board: List<List<CellState>>, sides: Pair<Int, Int>): GameState =
        GameState(
            board = board,
            whiteSideCount = sides.first,
            blackSideCount = sides.second,
            currentPlayer = Player.WHITE,
            phase = GamePhase.OPTIONAL_MOVE
        )

    private fun applyOptMove(board: List<List<CellState>>, src: Int, dst: Int): List<List<CellState>> {
        val nb = mutableBoard(board)
        nb[dst / 4][dst % 4] = nb[src / 4][src % 4]; nb[src / 4][src % 4] = CellState.EMPTY
        return nb.toImmutable()
    }

    private fun rotate(board: List<List<CellState>>): List<List<CellState>> {
        val n = mutableBoard(board)
        for ((src, dst) in ROTATION_MAPPING) n[dst.first][dst.second] = board[src.first][src.second]
        return n.toImmutable()
    }

    private fun emptyBoard(): List<List<CellState>> = List(4) { List(4) { CellState.EMPTY } }
    private fun mutableBoard(b: List<List<CellState>>) = b.map { it.toMutableList() }.toMutableList()
    private fun List<MutableList<CellState>>.toImmutable() = map { it.toList() }
}
