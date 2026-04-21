package com.yoshi0311.orbito.ui

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yoshi0311.orbito.model.GameConfig
import com.yoshi0311.orbito.model.GamePhase
import com.yoshi0311.orbito.model.GameState
import com.yoshi0311.orbito.model.Player
import com.yoshi0311.orbito.model.PlayerType
import com.yoshi0311.orbito.ui.theme.AppBackground
import com.yoshi0311.orbito.ui.theme.BlackBall
import com.yoshi0311.orbito.ui.theme.WhiteBall
import com.yoshi0311.orbito.viewmodel.GameViewModel

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun GameScreen(
    config: GameConfig = GameConfig(),
    sessionKey: Int = 0,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: GameViewModel = viewModel(key = "game_$sessionKey", factory = GameViewModel.factory(config))
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var logVisible by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    val isCurrentPlayerBot = config.typeFor(state.currentPlayer) == PlayerType.BOT

    Box(modifier = modifier.background(AppBackground)) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isTablet = maxWidth >= 600.dp
            val cellSize = if (isTablet) minOf(maxWidth * 0.45f, maxHeight * 0.58f) / 4
                           else (maxWidth - 120.dp) / 4
            val ballSize = cellSize * 0.68f
            val sideBallSize = if (isTablet) 24.dp else 18.dp
            val boardWidth = cellSize * 4 + 12.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                TurnIndicator(state = state, config = config)

                Spacer(Modifier.height(20.dp))

                val showTimer = !isCurrentPlayerBot && state.timeLimitSeconds != null
                if (showTimer) {
                    TimerBar(
                        timeLeft = state.timeLeft,
                        maxTime = state.timeLimitSeconds!!,
                        width = boardWidth
                    )
                } else {
                    Spacer(Modifier.height(3.dp))
                }

                Spacer(Modifier.height(20.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    SideBallsPanel(
                        count = state.whiteSideCount,
                        ballColor = WhiteBall,
                        ballSize = sideBallSize,
                        isTablet = isTablet
                    )
                    Spacer(Modifier.width(10.dp))
                    BoardGrid(
                        state = state,
                        cellSize = cellSize,
                        ballSize = ballSize,
                        onCellTap = viewModel::onCellTap,
                        onRotationComplete = viewModel::onRotationComplete
                    )
                    Spacer(Modifier.width(10.dp))
                    SideBallsPanel(
                        count = state.blackSideCount,
                        ballColor = BlackBall,
                        ballSize = sideBallSize,
                        isTablet = isTablet
                    )
                }

                Spacer(Modifier.height(20.dp))

                NextButtonArea(
                    visible = state.botMoveReady,
                    onClick = viewModel::onNextPressed,
                    width = boardWidth
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text(
                    text = "←",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 18.sp
                )
            }
            TextButton(onClick = viewModel::restart) {
                Text(
                    text = "RESTART",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                    letterSpacing = 2.sp
                )
            }
        }

        TextButton(
            onClick = { settingsOpen = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 4.dp)
        ) {
            Text("⚙", color = Color.White.copy(alpha = 0.45f), fontSize = 16.sp)
        }

        if (settingsOpen) {
            SettingsModal(
                timeLimitSeconds = state.timeLimitSeconds,
                onTimeLimitChange = { viewModel.updateTimeLimit(it) },
                onDismiss = { settingsOpen = false }
            )
        }

        if (state.winner != null) {
            WinnerOverlay(
                winner = state.winner!!,
                isTimeout = state.timeLeft == 0,
                onRestart = viewModel::restart
            )
        }

        // Log toggle button
        val logCount = state.logs.size
        TextButton(
            onClick = { logVisible = !logVisible },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 4.dp, end = 4.dp)
        ) {
            Text(
                text = if (logCount > 0) "LOG ($logCount)" else "LOG",
                color = Color.White.copy(alpha = if (logCount > 0) 0.5f else 0.25f),
                fontSize = 10.sp,
                letterSpacing = 1.sp
            )
        }

        // Log slide-up panel
        AnimatedVisibility(
            visible = logVisible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            LogPanel(logs = state.logs, onDismiss = { logVisible = false })
        }
    }
}

@Composable
private fun TurnIndicator(state: GameState, config: GameConfig) {
    if (state.phase == GamePhase.DONE) return

    val playerColor = if (state.currentPlayer == Player.WHITE) WhiteBall else BlackBall
    val isBot = config.typeFor(state.currentPlayer) == PlayerType.BOT
    val isFirstTurn = state.whiteSideCount == 8 && state.blackSideCount == 8

    val label = when {
        isBot && state.isBotThinking -> "BOT IS THINKING..."
        isBot && state.botMoveReady ->
            if (state.currentPlayer == Player.WHITE) "WHITE  ·  READY" else "BLACK  ·  READY"
        isBot ->
            if (state.currentPlayer == Player.WHITE) "WHITE  ·  BOT" else "BLACK  ·  BOT"
        state.phase == GamePhase.OPTIONAL_MOVE && state.selectedCell != null ->
            "TAP ADJACENT CELL"
        state.phase == GamePhase.OPTIONAL_MOVE && !isFirstTurn ->
            if (state.currentPlayer == Player.WHITE) "WHITE  ·  MOVE OR PLACE"
            else "BLACK  ·  MOVE OR PLACE"
        else ->
            if (state.currentPlayer == Player.WHITE) "WHITE  ·  PLACE YOUR BALL"
            else "BLACK  ·  PLACE YOUR BALL"
    }
    val urgentThreshold = ((state.timeLimitSeconds ?: 20) * 0.25f).toInt().coerceAtLeast(5)
    val timerColor = if (state.timeLeft <= urgentThreshold) Color(0xFFFF6B6B) else Color.White

    Row(
        modifier = Modifier
            .background(Color(0x26000000), RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Ball(color = playerColor, size = 10.dp)
        Text(text = label, color = Color.White, fontSize = 11.sp, letterSpacing = 1.5.sp)
        if (!isBot && state.timeLimitSeconds != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = state.timeLeft.toString(),
                color = timerColor,
                fontSize = if (state.timeLeft <= 5) 14.sp else 12.sp,
                fontWeight = if (state.timeLeft <= 5) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun NextButtonArea(visible: Boolean, onClick: () -> Unit, width: Dp) {
    Box(
        modifier = Modifier.width(width).height(44.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            NextButton(onClick = onClick, width = width)
        }
    }
}

@Composable
private fun NextButton(onClick: () -> Unit, width: Dp) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.width(width)
    ) {
        Text(
            text = "NEXT  ▶",
            color = Color(0xFFFFCC44),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp
        )
    }
}

@Composable
private fun TimerBar(timeLeft: Int, maxTime: Int, width: Dp) {
    val fraction = if (maxTime > 0) (timeLeft / maxTime.toFloat()).coerceIn(0f, 1f) else 1f
    val urgentThreshold = (maxTime * 0.25f).toInt().coerceAtLeast(5)
    val barColor = if (timeLeft <= urgentThreshold) Color(0xFFFF6B6B) else Color.White.copy(alpha = 0.7f)

    Box(
        modifier = Modifier
            .width(width)
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = 0.15f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxSize(fraction)
                .background(barColor)
        )
    }
}

@Composable
private fun LogPanel(logs: List<String>, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 280.dp)
            .background(Color(0xFF1A1A1A), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BOT LOG",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                letterSpacing = 2.sp
            )
            TextButton(onClick = onDismiss) {
                Text(
                    text = "CLOSE",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
            }
        }
        if (logs.isEmpty()) {
            Text(
                text = "no logs",
                color = Color.White.copy(alpha = 0.25f),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                reverseLayout = true
            ) {
                items(logs) { log ->
                    Text(
                        text = log,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WinnerOverlay(winner: Player, isTimeout: Boolean, onRestart: () -> Unit) {
    val winnerColor = if (winner == Player.WHITE) WhiteBall else BlackBall
    val winnerName = if (winner == Player.WHITE) "WHITE" else "BLACK"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x55000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .background(Color(0x26FFFFFF), RoundedCornerShape(20.dp))
                .padding(horizontal = 48.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (isTimeout) "TIME OUT" else "WINNER",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                letterSpacing = 3.sp
            )
            Ball(color = winnerColor, size = 36.dp)
            Text(
                text = winnerName,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp
            )
            TextButton(onClick = onRestart) {
                Text(text = "PLAY AGAIN", color = Color.White, fontSize = 12.sp, letterSpacing = 2.sp)
            }
        }
    }
}

private val TIME_OPTIONS: List<Int?> = listOf(15, 20, 30, 45, 60, null)

@Composable
private fun SettingsModal(
    timeLimitSeconds: Int?,
    onTimeLimitChange: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF2111118)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .background(Color(0xFF1C1C28), RoundedCornerShape(20.dp))
                .padding(horizontal = 32.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "SETTINGS",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "MOVE TIME",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    letterSpacing = 2.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TIME_OPTIONS.forEach { option ->
                        val selected = option == timeLimitSeconds
                        Box(
                            modifier = Modifier
                                .background(
                                    if (selected) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                                    RoundedCornerShape(12.dp)
                                )
                                .border(
                                    1.dp,
                                    Color.White.copy(alpha = if (selected) 0.6f else 0.2f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { onTimeLimitChange(option) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (option == null) "∞" else "${option}s",
                                color = if (selected) Color.White else Color.White.copy(alpha = 0.5f),
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            TextButton(onClick = onDismiss) {
                Text(
                    text = "CLOSE",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}
