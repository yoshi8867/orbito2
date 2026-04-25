package com.yoshi0311.orbito.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yoshi0311.orbito.model.ChatMessage
import com.yoshi0311.orbito.model.OnlinePlayer
import com.yoshi0311.orbito.model.OnlineRole
import com.yoshi0311.orbito.model.OnlineState
import com.yoshi0311.orbito.model.OnlineStatus
import com.yoshi0311.orbito.model.Player
import com.yoshi0311.orbito.ui.theme.AppBackground
import com.yoshi0311.orbito.ui.theme.BlackBall
import com.yoshi0311.orbito.ui.theme.WhiteBall
import kotlinx.coroutines.delay

private val QUICK_CHAT = listOf(
    "반갑다.", "잘 부탁해.", "야호!", "내가 이길 듯?",
    "좋은 수였어!", "그건 예상 못 했는데.", "아니, 이런!", "좋은 승부였어."
)

@Composable
fun OnlineGameScreen(
    state: OnlineState,
    onCellTap: (Int, Int) -> Unit,
    onSendChat: (String) -> Unit,
    onLeave: () -> Unit,
    onRotationComplete: () -> Unit = {},
    onSaveRecord: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    TrackScreenTime("stat_min_online")
    Box(modifier = modifier.background(AppBackground)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth >= 600.dp) {
                LandscapeLayout(state, onCellTap, onSendChat, onRotationComplete)
            } else {
                PortraitLayout(state, onCellTap, onSendChat, onRotationComplete)
            }
        }

        TextButton(
            onClick = onLeave,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 4.dp)
        ) {
            Text("← LEAVE", color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp, letterSpacing = 2.sp)
        }

        when (state.status) {
            OnlineStatus.RECONNECTING -> ReconnectOverlay(state)
            OnlineStatus.DISCONNECTED -> DisconnectedOverlay(state.errorMessage, onLeave)
            else -> {}
        }
        if (state.game.winner != null) {
            OnlineWinnerOverlay(state.game.winner!!, state.players, onLeave, onSaveRecord)
        }
    }
}

// ─── Layouts ─────────────────────────────────────────────────────────────────

@Composable
private fun LandscapeLayout(
    state: OnlineState,
    onCellTap: (Int, Int) -> Unit,
    onSendChat: (String) -> Unit,
    onRotationComplete: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cellSize = minOf(maxWidth * 0.4f, maxHeight * 0.7f) / 4
        val ballSize = cellSize * 0.68f

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 56.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BoardArea(
                state = state,
                onCellTap = onCellTap,
                onSendChat = onSendChat,
                onRotationComplete = onRotationComplete,
                cellSize = cellSize,
                ballSize = ballSize,
                sideBallSize = 24.dp,
                isTablet = true,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )

            Column(
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight()
                    .background(Color(0x38FFFFFF), RoundedCornerShape(16.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PlayerListPanel(state.players)
                Box(Modifier.height(1.dp).fillMaxWidth().background(Color.White.copy(alpha = 0.08f)))
                ChatPanel(
                    messages = state.chatMessages,
                    myNickname = state.myNickname,
                    myRole = state.myRole,
                    players = state.players,
                    onSend = onSendChat,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PortraitLayout(
    state: OnlineState,
    onCellTap: (Int, Int) -> Unit,
    onSendChat: (String) -> Unit,
    onRotationComplete: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cellSize = (maxWidth - 160.dp) / 4
        val ballSize = cellSize * 0.68f
        var chatOpen by remember { mutableStateOf(false) }

        Box(Modifier.fillMaxSize()) {
            BoardArea(
                state = state,
                onCellTap = onCellTap,
                onSendChat = onSendChat,
                onRotationComplete = onRotationComplete,
                cellSize = cellSize,
                ballSize = ballSize,
                sideBallSize = 18.dp,
                isTablet = false,
                modifier = Modifier.fillMaxSize().padding(top = 56.dp, bottom = if (chatOpen) 260.dp else 48.dp)
            )

            TextButton(
                onClick = { chatOpen = !chatOpen },
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 4.dp, end = 4.dp)
            ) {
                Text(
                    text = if (chatOpen) "CHAT ▼" else "CHAT ▲",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
            }

            if (chatOpen) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(260.dp)
                        .background(Color(0x38FFFFFF), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .padding(12.dp)
                        .imePadding()
                ) {
                    ChatPanel(
                        messages = state.chatMessages,
                        myNickname = state.myNickname,
                        myRole = state.myRole,
                        players = state.players,
                        onSend = onSendChat,
                        onClose = { chatOpen = false },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

// ─── Board Area (board + bubbles + quick chat) ────────────────────────────────

@Composable
private fun BoardArea(
    state: OnlineState,
    onCellTap: (Int, Int) -> Unit,
    onSendChat: (String) -> Unit,
    onRotationComplete: () -> Unit,
    cellSize: Dp,
    ballSize: Dp,
    sideBallSize: Dp,
    isTablet: Boolean,
    modifier: Modifier = Modifier
) {
    // ── Bubble state ──────────────────────────────────────────────────────────
    var whiteBubbleMsg by remember { mutableStateOf("") }
    var whiteBubbleVer by remember { mutableIntStateOf(0) }
    var whiteBubbleVisible by remember { mutableStateOf(false) }
    var blackBubbleMsg by remember { mutableStateOf("") }
    var blackBubbleVer by remember { mutableIntStateOf(0) }
    var blackBubbleVisible by remember { mutableStateOf(false) }
    var quickChatOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.chatMessages.size) {
        val last = state.chatMessages.lastOrNull { !it.isSystem } ?: return@LaunchedEffect
        val role = state.players.find { it.nickname == last.nickname }?.role ?: return@LaunchedEffect
        when (role) {
            OnlineRole.WHITE -> { whiteBubbleMsg = last.message; whiteBubbleVer++ }
            OnlineRole.BLACK -> { blackBubbleMsg = last.message; blackBubbleVer++ }
            else -> {}
        }
    }
    LaunchedEffect(whiteBubbleVer) {
        if (whiteBubbleVer == 0) return@LaunchedEffect
        if (whiteBubbleVisible) { whiteBubbleVisible = false; delay(200) }
        whiteBubbleVisible = true; delay(4000); whiteBubbleVisible = false
    }
    LaunchedEffect(blackBubbleVer) {
        if (blackBubbleVer == 0) return@LaunchedEffect
        if (blackBubbleVisible) { blackBubbleVisible = false; delay(200) }
        blackBubbleVisible = true; delay(4000); blackBubbleVisible = false
    }

    // ── Left/right bubble mapping ─────────────────────────────────────────────
    // WHITE player: opponent(BLACK) on left, me(WHITE) on right
    // BLACK player: opponent(WHITE) on left, me(BLACK) on right
    // Spectator:    WHITE on left, BLACK on right
    val leftIsWhite = state.myRole != OnlineRole.WHITE
    val leftMsg     = if (leftIsWhite) whiteBubbleMsg else blackBubbleMsg
    val leftVisible = if (leftIsWhite) whiteBubbleVisible else blackBubbleVisible
    val leftColor   = if (leftIsWhite) WhiteBall else BlackBall
    val rightMsg     = if (!leftIsWhite) whiteBubbleMsg else blackBubbleMsg
    val rightVisible = if (!leftIsWhite) whiteBubbleVisible else blackBubbleVisible
    val rightColor   = if (!leftIsWhite) WhiteBall else BlackBall

    // AnimatedVisibility 안에서 ColumnScope 충돌 방지 — alpha 애니메이션으로 대체
    val leftAlpha by animateFloatAsState(
        targetValue = if (leftVisible) 1f else 0f,
        animationSpec = tween(200),
        label = "leftBubble"
    )
    val rightAlpha by animateFloatAsState(
        targetValue = if (rightVisible) 1f else 0f,
        animationSpec = tween(200),
        label = "rightBubble"
    )

    // ── Layout ───────────────────────────────────────────────────────────────
    Box(modifier = modifier) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OnlineTurnIndicator(state)

            // Bubble row — fixed height so board doesn't shift
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 8.dp)
            ) {
                if (leftMsg.isNotBlank()) {
                    Box(Modifier.align(Alignment.CenterStart).graphicsLayer { alpha = leftAlpha }) {
                        FloatingBubble(leftMsg, leftColor, isLeft = true)
                    }
                }
                if (rightMsg.isNotBlank()) {
                    Box(Modifier.align(Alignment.CenterEnd).graphicsLayer { alpha = rightAlpha }) {
                        FloatingBubble(rightMsg, rightColor, isLeft = false)
                    }
                }
            }

            NamePanel(
                whiteName = state.players.find { it.role == OnlineRole.WHITE }?.nickname ?: "WHITE",
                blackName = state.players.find { it.role == OnlineRole.BLACK }?.nickname ?: "BLACK",
                modifier = Modifier.fillMaxWidth().padding(horizontal = sideBallSize + 10.dp)
            )

            // Board row
            Row(verticalAlignment = Alignment.CenterVertically) {
                SideBallsPanel(state.game.whiteSideCount, WhiteBall, sideBallSize, isTablet)
                Spacer(Modifier.width(10.dp))
                BoardGrid(
                    state = state.toGameState(),
                    cellSize = cellSize,
                    ballSize = ballSize,
                    onCellTap = onCellTap,
                    onRotationComplete = onRotationComplete
                )
                Spacer(Modifier.width(10.dp))
                SideBallsPanel(state.game.blackSideCount, BlackBall, sideBallSize, isTablet)
            }
        }

        // Quick chat panel — slides up from bottom of board area
        AnimatedVisibility(
            visible = quickChatOpen,
            enter = slideInVertically(tween(200)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(start = 24.dp, end = 24.dp, bottom = 44.dp).fillMaxWidth()
        ) {
            QuickChatPanel(onSend = { msg -> onSendChat(msg); quickChatOpen = false })
        }

        // 💬 toggle button
        Text(
            text = "💬",
            fontSize = 40.sp,
            color = if (quickChatOpen) Color.White else Color.White.copy(alpha = 0.4f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 8.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { quickChatOpen = !quickChatOpen }
                .padding(6.dp)
        )
    }
}

// ─── Components ──────────────────────────────────────────────────────────────

@Composable
private fun FloatingBubble(text: String, ballColor: Color, isLeft: Boolean) {
    Row(
        modifier = Modifier
            .background(Color(0x38FFFFFF), RoundedCornerShape(12.dp))
            .border(1.dp, ballColor.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        if (isLeft) {
            Box(Modifier.size(7.dp).background(ballColor, RoundedCornerShape(50)))
            Text(text, color = Color.White.copy(alpha = 0.9f), fontSize = 19.sp)
        } else {
            Text(text, color = Color.White.copy(alpha = 0.9f), fontSize = 19.sp)
            Box(Modifier.size(7.dp).background(ballColor, RoundedCornerShape(50)))
        }
    }
}

@Composable
private fun QuickChatPanel(onSend: (String) -> Unit) {
    Column(
        modifier = Modifier
            .background(Color(0xF01C1C2E), RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (row in 0 until 4) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                QUICK_CHAT.slice(row * 2 until row * 2 + 2).forEach { msg ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(8.dp))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onSend(msg) }
                            .padding(horizontal = 10.dp, vertical = 9.dp)
                    ) {
                        Text(text = msg, color = Color.White.copy(alpha = 0.85f), fontSize = 20.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineTurnIndicator(state: OnlineState) {
    if (state.game.winner != null) return
    val currentPlayer = state.game.currentPlayer
    val playerColor = if (currentPlayer == Player.WHITE) WhiteBall else BlackBall
    val currentNick = state.players
        .find { it.role == if (currentPlayer == Player.WHITE) OnlineRole.WHITE else OnlineRole.BLACK }
        ?.nickname ?: currentPlayer.name
    val isMyTurn = when (state.myRole) {
        OnlineRole.WHITE -> currentPlayer == Player.WHITE
        OnlineRole.BLACK -> currentPlayer == Player.BLACK
        OnlineRole.SPECTATOR -> false
    }
    Row(
        modifier = Modifier
            .background(Color(0x26000000), RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Ball(color = playerColor, size = 10.dp)
        Text(
            text = if (isMyTurn) "YOUR TURN" else "$currentNick'S TURN",
            color = Color.White,
            fontSize = 11.sp,
            letterSpacing = 1.5.sp
        )
    }
}

@Composable
private fun PlayerListPanel(players: List<OnlinePlayer>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("PLAYERS", color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp, letterSpacing = 2.sp)
        players.forEach { player ->
            val ballColor = when (player.role) {
                OnlineRole.WHITE -> WhiteBall
                OnlineRole.BLACK -> BlackBall
                OnlineRole.SPECTATOR -> Color.White.copy(alpha = 0.3f)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier.size(8.dp).background(
                        if (player.connected) ballColor else Color.Gray,
                        RoundedCornerShape(50)
                    )
                )
                Text(
                    text = player.nickname,
                    color = if (player.connected) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.3f),
                    fontSize = 12.sp
                )
                Text(player.role.name, color = Color.White.copy(alpha = 0.3f), fontSize = 9.sp, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
fun ChatPanel(
    messages: List<ChatMessage>,
    myNickname: String,
    myRole: OnlineRole,
    players: List<OnlinePlayer>,
    onSend: (String) -> Unit,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = modifier) {
        if (onClose != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("CHAT", color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp, letterSpacing = 2.sp)
                TextButton(onClick = onClose) {
                    Text("✕", color = Color.White.copy(alpha = 0.45f), fontSize = 14.sp)
                }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(messages) { msg ->
                ChatBubbleItem(msg = msg, myNickname = myNickname, myRole = myRole, players = players)
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BasicTextField(
                value = input,
                onValueChange = { if (it.length <= 100) input = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            )
            TextButton(
                onClick = { if (input.isNotBlank()) { onSend(input); input = "" } },
                modifier = Modifier.height(36.dp).padding(0.dp)
            ) {
                Text("SEND", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
private fun ChatBubbleItem(
    msg: ChatMessage,
    myNickname: String,
    myRole: OnlineRole,
    players: List<OnlinePlayer>
) {
    if (msg.isSystem) {
        Text(
            text = msg.message,
            color = Color.White.copy(alpha = 0.35f),
            fontSize = 9.sp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
        )
        return
    }

    val senderRole = players.find { it.nickname == msg.nickname }?.role ?: OnlineRole.SPECTATOR
    val isMine = msg.nickname == myNickname

    // For players: mine on right, opponent on left
    // For spectators: WHITE on left, BLACK on right
    val alignRight = when (myRole) {
        OnlineRole.SPECTATOR -> senderRole == OnlineRole.BLACK
        else -> isMine
    }

    val bubbleColor = when (senderRole) {
        OnlineRole.WHITE -> WhiteBall
        OnlineRole.BLACK -> BlackBall
        OnlineRole.SPECTATOR -> Color.White.copy(alpha = 0.4f)
    }
    val bgColor = Color(0x38FFFFFF)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (alignRight) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.widthIn(max = 200.dp),
            horizontalAlignment = if (alignRight) Alignment.End else Alignment.Start
        ) {
            if (!isMine) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(Modifier.size(5.dp).background(bubbleColor, RoundedCornerShape(50)))
                    Text(msg.nickname, color = Color.White.copy(alpha = 0.45f), fontSize = 9.sp)
                }
            }
            Box(
                modifier = Modifier
                    .background(bgColor, RoundedCornerShape(10.dp))
                    .border(1.dp, bubbleColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(text = msg.message, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
            }
        }
    }
}

// ─── Overlays ─────────────────────────────────────────────────────────────────

@Composable
private fun ReconnectOverlay(state: OnlineState) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .background(Color(0xFF1C1C28), RoundedCornerShape(20.dp))
                .padding(horizontal = 40.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("연결 끊김", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, letterSpacing = 2.sp)
            Text(state.disconnectedNick, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("재접속 대기 중...", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
            Text(
                text = "${state.reconnectSecondsLeft}s",
                color = if (state.reconnectSecondsLeft <= 10) Color(0xFFFF6B6B) else Color.White.copy(alpha = 0.7f),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DisconnectedOverlay(message: String, onLeave: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .background(Color(0xFF1C1C28), RoundedCornerShape(20.dp))
                .padding(horizontal = 40.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(message, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
            TextButton(onClick = onLeave) {
                Text("← LOBBY", color = Color.White, fontSize = 12.sp, letterSpacing = 2.sp)
            }
        }
    }
}

@Composable
private fun OnlineWinnerOverlay(
    winner: Player,
    players: List<OnlinePlayer>,
    onLeave: () -> Unit,
    onSaveRecord: () -> Unit = {}
) {
    val winnerColor = if (winner == Player.WHITE) WhiteBall else BlackBall
    val winnerRole = if (winner == Player.WHITE) OnlineRole.WHITE else OnlineRole.BLACK
    val winnerNick = players.find { it.role == winnerRole }?.nickname
        ?: if (winner == Player.WHITE) "WHITE" else "BLACK"

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0x77000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .background(Color(0x26FFFFFF), RoundedCornerShape(20.dp))
                .padding(horizontal = 48.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("WINNER", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, letterSpacing = 3.sp)
            Ball(color = winnerColor, size = 36.dp)
            Text(winnerNick, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp)
            TextButton(onClick = onLeave) {
                Text("← LOBBY", color = Color.White, fontSize = 12.sp, letterSpacing = 2.sp)
            }
            TextButton(onClick = onSaveRecord) {
                Text("SAVE RECORD", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, letterSpacing = 2.sp)
            }
        }
    }
}
