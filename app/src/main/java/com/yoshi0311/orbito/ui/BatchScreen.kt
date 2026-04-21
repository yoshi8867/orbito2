package com.yoshi0311.orbito.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yoshi0311.orbito.model.AVAILABLE_BOTS
import com.yoshi0311.orbito.model.BatchState
import com.yoshi0311.orbito.model.BotConfig
import com.yoshi0311.orbito.model.GameConfig
import com.yoshi0311.orbito.model.Player
import com.yoshi0311.orbito.ui.theme.AppBackground
import com.yoshi0311.orbito.ui.theme.BlackBall
import com.yoshi0311.orbito.ui.theme.WhiteBall
import com.yoshi0311.orbito.viewmodel.BatchViewModel

@Composable
fun BatchScreen(
    config: GameConfig,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: BatchViewModel = viewModel(factory = BatchViewModel.factory(config))
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var whiteBot by remember { mutableStateOf(config.whiteBot ?: AVAILABLE_BOTS.first()) }
    var blackBot by remember { mutableStateOf(config.blackBot ?: AVAILABLE_BOTS.first()) }
    var batchCount by remember { mutableStateOf(config.batchCount) }

    fun buildConfig() = state.config.copy(whiteBot = whiteBot, blackBot = blackBot, batchCount = batchCount)

    Box(modifier = modifier.background(AppBackground)) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isTablet = maxWidth >= 600.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                BatchProgressChip(state = state)

                Spacer(Modifier.height(40.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(if (isTablet) 80.dp else 56.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    WinCountPanel(
                        wins = state.whiteWins,
                        total = state.config.batchCount,
                        color = WhiteBall,
                        isTablet = isTablet
                    )
                    WinCountPanel(
                        wins = state.blackWins,
                        total = state.config.batchCount,
                        color = BlackBall,
                        isTablet = isTablet
                    )
                }

                Spacer(Modifier.height(36.dp))

                BatchConfigSection(
                    whiteBot = whiteBot,
                    blackBot = blackBot,
                    batchCount = batchCount,
                    onWhiteBotChange = { whiteBot = it },
                    onBlackBotChange = { blackBot = it },
                    onBatchCountChange = { batchCount = it }
                )
            }
        }

        // Top-left: ← BACK + RESTART — identical position to GameScreen
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("←", color = Color.White.copy(alpha = 0.45f), fontSize = 18.sp)
            }
            TextButton(onClick = { viewModel.restart(buildConfig()) }) {
                Text(
                    text = "RESTART",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

@Composable
private fun BatchProgressChip(state: BatchState) {
    val label = when {
        !state.isDone && state.currentGame == 0 -> "STARTING..."
        !state.isDone -> "GAME  ${state.currentGame}  /  ${state.config.batchCount}"
        state.whiteWins > state.blackWins -> "WHITE WINS  ·  ${state.whiteWins} : ${state.blackWins}"
        state.blackWins > state.whiteWins -> "BLACK WINS  ·  ${state.whiteWins} : ${state.blackWins}"
        else -> "DRAW  ·  ${state.whiteWins} : ${state.blackWins}"
    }
    val winnerBallColor = when {
        state.isDone && state.whiteWins > state.blackWins -> WhiteBall
        state.isDone && state.blackWins > state.whiteWins -> BlackBall
        else -> null
    }

    Row(
        modifier = Modifier
            .background(Color(0x26000000), RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (winnerBallColor != null) Ball(color = winnerBallColor, size = 10.dp)
        Text(text = label, color = Color.White, fontSize = 11.sp, letterSpacing = 1.5.sp)
    }
}

@Composable
private fun WinCountPanel(wins: Int, total: Int, color: Color, isTablet: Boolean) {
    val ballSize: Dp = when {
        total <= 10 -> if (isTablet) 24.dp else 19.dp
        total <= 30 -> if (isTablet) 19.dp else 15.dp
        total <= 50 -> if (isTablet) 16.dp else 13.dp
        else        -> if (isTablet) 13.dp else 10.dp
    }
    val gap = 4.dp
    val rows = (total + 4) / 5  // ceil(total / 5)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = wins.toString(),
            color = Color.White,
            fontSize = if (isTablet) 42.sp else 36.sp,
            fontWeight = FontWeight.Bold
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(gap),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            repeat(rows) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(5) { col ->
                        val idx = row * 5 + col
                        if (idx < total) {
                            Box(
                                modifier = Modifier
                                    .size(ballSize)
                                    .clip(CircleShape)
                                    .then(
                                        if (idx < wins) Modifier.background(color)
                                        else Modifier.border(1.dp, color.copy(alpha = 0.28f), CircleShape)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchConfigSection(
    whiteBot: BotConfig,
    blackBot: BotConfig,
    batchCount: Int,
    onWhiteBotChange: (BotConfig) -> Unit,
    onBlackBotChange: (BotConfig) -> Unit,
    onBatchCountChange: (Int) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Ball(color = WhiteBall, size = 10.dp)
                BotSelector(selectedBot = whiteBot, onBotChange = onWhiteBotChange)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Ball(color = BlackBall, size = 10.dp)
                BotSelector(selectedBot = blackBot, onBotChange = onBlackBotChange)
            }
        }
        BatchCountSelector(count = batchCount, onCountChange = onBatchCountChange)
    }
}
