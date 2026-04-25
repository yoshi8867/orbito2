package com.yoshi0311.orbito.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yoshi0311.orbito.model.CellState
import com.yoshi0311.orbito.ui.theme.AppBackground
import com.yoshi0311.orbito.ui.theme.BlackBall
import com.yoshi0311.orbito.ui.theme.CellNormal
import com.yoshi0311.orbito.ui.theme.WhiteBall
import com.yoshi0311.orbito.viewmodel.BotEditViewModel

@Composable
fun BotEditScreen(
    initialBotName: String?,
    sessionKey: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BotEditViewModel = viewModel(
        key = "bot_edit_$sessionKey",
        factory = BotEditViewModel.factory(initialBotName)
    )
) {
    TrackScreenTime("stat_min_bot_edit")
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Navigate back after delete
    LaunchedEffect(state.shouldNavigateBack) {
        if (state.shouldNavigateBack) onBack()
    }

    // Toast display
    LaunchedEffect(state.toast) {
        val msg = state.toast ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        viewModel.dismissToast()
    }

    val whiteBoardCount = state.editBoard.flatten().count { it == CellState.WHITE }
    val blackBoardCount = state.editBoard.flatten().count { it == CellState.BLACK }
    val whiteSideCount = (8 - whiteBoardCount).coerceIn(0, 8)
    val blackSideCount = (8 - blackBoardCount).coerceIn(0, 8)
    val isRunValid = !state.isRunning && run {
        if (state.currentTurn == "w") blackBoardCount - whiteBoardCount in 0..1
        else whiteBoardCount - blackBoardCount in 0..1
    }

    fun buildStateString(): String {
        val cells = state.editBoard.flatten().joinToString(",") {
            when (it) { CellState.WHITE -> "w"; CellState.BLACK -> "b"; else -> "" }
        }
        return "\"[$cells]/$whiteSideCount/$blackSideCount/${state.currentTurn}\""
    }

    Box(modifier = modifier.fillMaxSize().background(AppBackground)) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isTablet = maxWidth >= 600.dp
            val cellSize: Dp = if (isTablet) minOf(maxWidth * 0.38f, maxHeight * 0.5f) / 4
                               else (maxWidth - 100.dp) / 4
            val ballSize: Dp = cellSize * 0.68f
            val sideBallSize: Dp = if (isTablet) 22.dp else 16.dp

            if (isTablet) {
                Row(modifier = Modifier.fillMaxSize().padding(top = 48.dp)) {
                    // Left: board
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.42f)
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        BoardSection(
                            state = state,
                            whiteSideCount = whiteSideCount,
                            blackSideCount = blackSideCount,
                            cellSize = cellSize,
                            ballSize = ballSize,
                            sideBallSize = sideBallSize,
                            isTablet = true,
                            isRunValid = isRunValid,
                            stateString = buildStateString(),
                            currentTurn = state.currentTurn,
                            onCellTap = viewModel::cycleCellState,
                            onRotationComplete = viewModel::onRotationComplete,
                            onRun = viewModel::runBot,
                            onBack = viewModel::backRun,
                            onClear = viewModel::clearBoard,
                            onToggleTurn = viewModel::toggleTurn
                        )
                    }
                    // Right: editor
                    EditorPanel(
                        state = state,
                        onBotNameChange = viewModel::updateBotName,
                        onCodeChange = viewModel::updateCode,
                        onInfo = viewModel::showInfoModal,
                        onSave = viewModel::saveBot,
                        onUndo = viewModel::undoCode,
                        onRedo = viewModel::redoCode,
                        onDelete = viewModel::showDeleteConfirm,
                        modifier = Modifier.fillMaxHeight().weight(0.58f)
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(top = 48.dp)) {
                    // Top: editor
                    EditorPanel(
                        state = state,
                        onBotNameChange = viewModel::updateBotName,
                        onCodeChange = viewModel::updateCode,
                        onInfo = viewModel::showInfoModal,
                        onSave = viewModel::saveBot,
                        onUndo = viewModel::undoCode,
                        onRedo = viewModel::redoCode,
                        onDelete = viewModel::showDeleteConfirm,
                        modifier = Modifier.fillMaxWidth().weight(0.5f)
                    )
                    // Bottom: board
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.5f)
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        BoardSection(
                            state = state,
                            whiteSideCount = whiteSideCount,
                            blackSideCount = blackSideCount,
                            cellSize = cellSize,
                            ballSize = ballSize,
                            sideBallSize = sideBallSize,
                            isTablet = false,
                            isRunValid = isRunValid,
                            stateString = buildStateString(),
                            currentTurn = state.currentTurn,
                            onCellTap = viewModel::cycleCellState,
                            onRotationComplete = viewModel::onRotationComplete,
                            onRun = viewModel::runBot,
                            onBack = viewModel::backRun,
                            onClear = viewModel::clearBoard,
                            onToggleTurn = viewModel::toggleTurn
                        )
                    }
                }
            }
        }

        // Back button (top-left)
        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 4.dp)
        ) {
            Text("←", color = Color.White.copy(alpha = 0.45f), fontSize = 18.sp)
        }

        // Info modal
        if (state.showInfoModal) {
            InfoModal(onDismiss = viewModel::dismissInfoModal)
        }

        // Delete confirm dialog
        if (state.showDeleteConfirm) {
            DeleteConfirmDialog(
                botName = state.savedFileName ?: "",
                onConfirm = { viewModel.dismissDeleteConfirm(); viewModel.deleteBot() },
                onDismiss = viewModel::dismissDeleteConfirm
            )
        }
    }
}

// ── Board section ─────────────────────────────────────────────────────────────

@Composable
private fun BoardSection(
    state: com.yoshi0311.orbito.viewmodel.BotEditUiState,
    whiteSideCount: Int,
    blackSideCount: Int,
    cellSize: Dp,
    ballSize: Dp,
    sideBallSize: Dp,
    isTablet: Boolean,
    isRunValid: Boolean,
    stateString: String,
    currentTurn: String,
    onCellTap: (Int, Int) -> Unit,
    onRotationComplete: () -> Unit,
    onRun: () -> Unit,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onToggleTurn: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SideBallsPanel(count = whiteSideCount, ballColor = WhiteBall, ballSize = sideBallSize, isTablet = isTablet)
            Spacer(Modifier.width(10.dp))

            if (state.displayBoardState != null) {
                BoardGrid(
                    state = state.displayBoardState,
                    cellSize = cellSize,
                    ballSize = ballSize,
                    onCellTap = { _, _ -> },
                    onRotationComplete = onRotationComplete
                )
            } else {
                EditableBoard(board = state.editBoard, cellSize = cellSize, ballSize = ballSize, onCellTap = onCellTap)
            }

            Spacer(Modifier.width(10.dp))
            SideBallsPanel(count = blackSideCount, ballColor = BlackBall, ballSize = sideBallSize, isTablet = isTablet)
        }

        Text(
            text = stateString,
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BoardActionButton(
                text = "RUN",
                enabled = isRunValid,
                onClick = onRun
            )
            BoardActionButton(
                text = "BACK",
                enabled = !state.isRunning && state.runStackSize > 0,
                onClick = onBack
            )
            BoardActionButton(
                text = "CLEAR",
                enabled = !state.isRunning,
                onClick = onClear
            )
            BoardActionButton(
                text = if (currentTurn == "w") "WHITE" else "BLACK",
                enabled = !state.isRunning,
                onClick = onToggleTurn
            )
        }
    }
}

@Composable
private fun EditableBoard(
    board: List<List<CellState>>,
    cellSize: Dp,
    ballSize: Dp,
    onCellTap: (Int, Int) -> Unit
) {
    val gap = 4.dp
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        for (r in 0..3) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (c in 0..3) {
                    EditCell(
                        cellState = board[r][c],
                        cellSize = cellSize,
                        ballSize = ballSize,
                        onClick = { onCellTap(r, c) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EditCell(cellState: CellState, cellSize: Dp, ballSize: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(cellSize)
            .clip(RoundedCornerShape(8.dp))
            .background(CellNormal)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when (cellState) {
            CellState.WHITE -> Ball(color = WhiteBall, size = ballSize)
            CellState.BLACK -> Ball(color = BlackBall, size = ballSize)
            CellState.EMPTY -> Unit
        }
    }
}

@Composable
private fun BoardActionButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val alpha = if (enabled) 0.7f else 0.25f
    Box(
        modifier = Modifier
            .border(1.dp, Color.White.copy(alpha = if (enabled) 0.35f else 0.15f), RoundedCornerShape(12.dp))
            .then(
                if (enabled) Modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClick
                ) else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.White.copy(alpha = alpha), fontSize = 10.sp, letterSpacing = 1.sp)
    }
}

// ── Editor panel ──────────────────────────────────────────────────────────────

@Composable
private fun EditorPanel(
    state: com.yoshi0311.orbito.viewmodel.BotEditUiState,
    onBotNameChange: (String) -> Unit,
    onCodeChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    onInfo: () -> Unit,
    onSave: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.background(Color(0xFF1E1E1E))) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = state.botName,
                onValueChange = onBotNameChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.85f)
                ),
                cursorBrush = SolidColor(Color.White.copy(alpha = 0.7f)),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .defaultMinSize(minWidth = 80.dp)
                    ) {
                        if (state.botName.isEmpty()) {
                            Text("BOT NAME", color = Color.White.copy(alpha = 0.25f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                        innerTextField()
                    }
                }
            )
            Spacer(Modifier.weight(1f))
            EditorActionButton("INFO", onClick = onInfo)
            EditorActionButton("SAVE", onClick = onSave)
            // EditorActionButton("UNDO", enabled = state.canUndo, onClick = onUndo)
            // EditorActionButton("REDO", enabled = state.canRedo, onClick = onRedo)
            if (state.savedFileName != null) {
                EditorActionButton("DEL", onClick = onDelete)
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))

        // Code editor (scrollable)
        Box(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            BasicTextField(
                value = state.codeText,
                onValueChange = onCodeChange,
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    lineHeight = 20.sp
                ),
                cursorBrush = SolidColor(Color.White.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            )
        }
    }
}

@Composable
private fun EditorActionButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = if (enabled) 0.55f else 0.2f),
            fontSize = 9.sp,
            letterSpacing = 1.sp
        )
    }
}

// ── Info modal ────────────────────────────────────────────────────────────────

@Composable
private fun InfoModal(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C28),
        titleContentColor = Color.White,
        textContentColor = Color.White.copy(alpha = 0.8f),
        title = {
            Text(
                text = "BOT FUNCTION",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoLine("함수명", "move(state: str) -> str")
                InfoLine("입력 형식", "[c0,...,c15]/w_side/b_side/color")
                InfoLine("cell 값", "\"\" 빈칸  \"w\" 흰색  \"b\" 검은색")
                InfoLine("color", "\"w\" 또는 \"b\" (내 돌 색상)")
                InfoLine("출력 형식", "\"skip/pos\"  또는  \"src>dst/pos\"")
                InfoLine("optional", "상대 돌을 인접 빈칸으로 이동 (src→dst)")
                InfoLine("pos", "0–15 (내 돌을 놓을 위치, 행우선)")
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
                Text(
                    text = "⏱  3초 이내에 반환해야 합니다.",
                    fontSize = 11.sp,
                    color = Color(0xFFFFCC44),
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, letterSpacing = 2.sp)
            }
        }
    )
}

@Composable
private fun DeleteConfirmDialog(botName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C28),
        titleContentColor = Color.White,
        textContentColor = Color.White.copy(alpha = 0.8f),
        title = {
            Text(
                text = "봇 삭제",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp
            )
        },
        text = {
            Text(
                text = "\"$botName\" 봇을 삭제하시겠습니까?",
                fontSize = 12.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("YES", color = Color(0xFFFF6B6B), fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("NO", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, letterSpacing = 2.sp)
            }
        }
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.45f),
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(70.dp)
        )
        Text(
            text = value,
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.85f),
            fontFamily = FontFamily.Monospace
        )
    }
}
