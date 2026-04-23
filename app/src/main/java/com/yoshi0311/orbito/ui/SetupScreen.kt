package com.yoshi0311.orbito.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yoshi0311.orbito.model.AVAILABLE_BOTS
import com.yoshi0311.orbito.model.BotConfig
import com.yoshi0311.orbito.model.BotVsBotMode
import com.yoshi0311.orbito.model.GameConfig
import com.yoshi0311.orbito.model.PlayerType
import com.yoshi0311.orbito.ui.theme.AppBackground
import com.yoshi0311.orbito.ui.theme.BlackBall
import com.yoshi0311.orbito.ui.theme.WhiteBall

@Composable
fun SetupScreen(
    onStartGame: (GameConfig) -> Unit,
    onBack: () -> Unit,
    onNewBot: () -> Unit = {},
    onEditBot: (String) -> Unit = {},
    userBots: List<BotConfig> = emptyList(),
    modifier: Modifier = Modifier
) {
    var whiteType by remember { mutableStateOf(PlayerType.HUMAN) }
    var blackType by remember { mutableStateOf(PlayerType.HUMAN) }
    var whiteBot by remember { mutableStateOf(AVAILABLE_BOTS.first()) }
    var blackBot by remember { mutableStateOf(AVAILABLE_BOTS.first()) }
    var botMode by remember { mutableStateOf(BotVsBotMode.STEP) }
    var batchCount by remember { mutableStateOf(10) }

    val isBotVsBot = whiteType == PlayerType.BOT && blackType == PlayerType.BOT

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(36.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "ORBITO",
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 8.sp
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.Top
            ) {
                PlayerPanel(
                    label = "WHITE",
                    ballColor = WhiteBall,
                    type = whiteType,
                    selectedBot = whiteBot,
                    userBots = userBots,
                    onTypeChange = { whiteType = it },
                    onBotChange = { whiteBot = it },
                    onNewBot = onNewBot,
                    onEditBot = onEditBot
                )
                PlayerPanel(
                    label = "BLACK",
                    ballColor = BlackBall,
                    type = blackType,
                    selectedBot = blackBot,
                    userBots = userBots,
                    onTypeChange = { blackType = it },
                    onBotChange = { blackBot = it },
                    onNewBot = onNewBot,
                    onEditBot = onEditBot
                )
            }

            AnimatedVisibility(visible = isBotVsBot) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeChip(
                            label = "STEP",
                            selected = botMode == BotVsBotMode.STEP,
                            onClick = { botMode = BotVsBotMode.STEP }
                        )
                        ModeChip(
                            label = "BATCH",
                            selected = botMode == BotVsBotMode.BATCH,
                            onClick = { botMode = BotVsBotMode.BATCH }
                        )
                    }
                    AnimatedVisibility(visible = botMode == BotVsBotMode.BATCH) {
                        BatchCountSelector(count = batchCount, onCountChange = { batchCount = it })
                    }
                }
            }

            TextButton(
                onClick = {
                    onStartGame(
                        GameConfig(
                            whiteType = whiteType,
                            blackType = blackType,
                            whiteBot = if (whiteType == PlayerType.BOT) whiteBot else null,
                            blackBot = if (blackType == PlayerType.BOT) blackBot else null,
                            botVsBotMode = botMode,
                            batchCount = batchCount
                        )
                    )
                },
                modifier = Modifier
                    .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = "START GAME",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 4.sp
                )
            }
        }
    }
}

@Composable
private fun PlayerPanel(
    label: String,
    ballColor: Color,
    type: PlayerType,
    selectedBot: BotConfig,
    userBots: List<BotConfig> = emptyList(),
    onTypeChange: (PlayerType) -> Unit,
    onBotChange: (BotConfig) -> Unit,
    onNewBot: () -> Unit = {},
    onEditBot: (String) -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.width(120.dp)
    ) {
        Ball(color = ballColor, size = 14.dp)
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 10.sp,
            letterSpacing = 2.sp
        )
        Row(
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(20.dp))
                .padding(2.dp)
        ) {
            TypeChip(
                label = "PLAYER",
                selected = type == PlayerType.HUMAN,
                onClick = { onTypeChange(PlayerType.HUMAN) }
            )
            TypeChip(
                label = "BOT",
                selected = type == PlayerType.BOT,
                onClick = { onTypeChange(PlayerType.BOT) }
            )
        }
        AnimatedVisibility(visible = type == PlayerType.BOT) {
            BotSelector(
                selectedBot = selectedBot,
                userBots = userBots,
                onBotChange = onBotChange,
                onNewBot = onNewBot,
                onEditBot = onEditBot
            )
        }
    }
}

@Composable
private fun TypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                if (selected) Color.White.copy(alpha = 0.18f) else Color.Transparent,
                RoundedCornerShape(18.dp)
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.4f),
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
internal fun BotSelector(
    selectedBot: BotConfig,
    userBots: List<BotConfig> = emptyList(),
    onBotChange: (BotConfig) -> Unit,
    onNewBot: () -> Unit = {},
    onEditBot: (String) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                text = "${selectedBot.name} ▾",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 9.sp,
                letterSpacing = 1.sp
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .background(Color(0xFF2A2A2A), RoundedCornerShape(8.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            ) {
                // Built-in bots
                AVAILABLE_BOTS.forEach { bot ->
                    Text(
                        text = bot.name,
                        color = if (bot == selectedBot) Color.White else Color.White.copy(alpha = 0.6f),
                        fontSize = 9.sp,
                        modifier = Modifier
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onBotChange(bot); expanded = false }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                // User bots (with pencil icon)
                if (userBots.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
                    userBots.forEach { bot ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = bot.name,
                                color = if (bot == selectedBot) Color.White else Color.White.copy(alpha = 0.6f),
                                fontSize = 9.sp,
                                modifier = Modifier
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) { onBotChange(bot); expanded = false }
                                    .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 2.dp)
                            )
                            Text(
                                text = "✏",
                                color = Color.White.copy(alpha = 0.3f),
                                fontSize = 8.sp,
                                modifier = Modifier
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) { onEditBot(bot.name); expanded = false }
                                    .padding(horizontal = 6.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                // + NEW
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
                Text(
                    text = "+ NEW",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 9.sp,
                    modifier = Modifier
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onNewBot(); expanded = false }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                if (selected) Color.White.copy(alpha = 0.18f) else Color.Transparent,
                RoundedCornerShape(16.dp)
            )
            .border(
                1.dp,
                Color.White.copy(alpha = if (selected) 0.5f else 0.2f),
                RoundedCornerShape(16.dp)
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.45f),
            fontSize = 11.sp,
            letterSpacing = 2.sp
        )
    }
}

@Composable
internal fun BatchCountSelector(count: Int, onCountChange: (Int) -> Unit) {
    fun dec(c: Int) = if (c > 30) maxOf(c - 10, 30) else maxOf(c - 1, 10)
    fun inc(c: Int) = if (c >= 30) minOf(c + 10, 100) else c + 1

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "GAMES",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 10.sp,
            letterSpacing = 2.sp
        )
        TextButton(
            onClick = { onCountChange(dec(count)) },
            modifier = Modifier.size(32.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(text = "−", color = Color.White.copy(alpha = 0.7f), fontSize = 18.sp)
        }
        Text(
            text = count.toString(),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.width(52.dp)
        )
        TextButton(
            onClick = { onCountChange(inc(count)) },
            modifier = Modifier.size(32.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(text = "+", color = Color.White.copy(alpha = 0.7f), fontSize = 18.sp)
        }
    }
}
