package com.yoshi0311.orbito.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yoshi0311.orbito.model.OnlinePlayer
import com.yoshi0311.orbito.model.OnlineRole
import com.yoshi0311.orbito.model.OnlineState
import com.yoshi0311.orbito.model.OnlineStatus
import com.yoshi0311.orbito.model.RoomInfo
import com.yoshi0311.orbito.ui.theme.BlackBall
import com.yoshi0311.orbito.ui.theme.WhiteBall

@Composable
fun OnlineModal(
    state: OnlineState,
    onCreateRoom: (String) -> Unit,
    onJoinRoom: (RoomInfo, String) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var nickname by remember { mutableStateOf(state.myNickname) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5000L)
            onRefresh()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xF2111118)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .background(Color(0xFF1C1C28), RoundedCornerShape(20.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ONLINE MATCH",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 3.sp
                )
                TextButton(onClick = onDismiss) {
                    Text("✕", color = Color.White.copy(alpha = 0.45f), fontSize = 14.sp)
                }
            }

            // Nickname input
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "NICK NAME",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    letterSpacing = 2.sp
                )
                BasicTextField(
                    value = nickname,
                    onValueChange = { if (it.length <= 12) nickname = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }

            // Room list
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ROOM LIST",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "↻ REFRESH",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 9.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = onRefresh
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                        .background(Color(0xFF141420), RoundedCornerShape(10.dp))
                ) {
                    if (state.discoveredRooms.isEmpty()) {
                        Text(
                            text = "",
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.padding(8.dp)) {
                            items(state.discoveredRooms) { room ->
                                RoomItem(
                                    room = room,
                                    onClick = { onJoinRoom(room, nickname) }
                                )
                            }
                        }
                    }
                }
            }

            // Create room button
            TextButton(
                onClick = { onCreateRoom(nickname) },
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
            ) {
                Text(
                    text = if (nickname.isBlank()) "ROOM CREATE" else "ROOM CREATE  (${nickname}'s ROOM)",
                    color = Color.White,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

@Composable
private fun RoomItem(room: RoomInfo, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = room.name,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            room.players.forEach { player ->
                PlayerTag(player)
            }
        }
    }
}

@Composable
private fun PlayerTag(player: OnlinePlayer) {
    val ballColor = when (player.role) {
        OnlineRole.WHITE -> WhiteBall
        OnlineRole.BLACK -> BlackBall
        OnlineRole.SPECTATOR -> Color.White.copy(alpha = 0.35f)
    }
    val prefix = if (player.connected) "○" else "✕"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(ballColor, RoundedCornerShape(50))
        )
        Text(
            text = "$prefix ${player.nickname}",
            color = if (player.connected) Color.White.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.3f),
            fontSize = 9.sp
        )
    }
}

@Composable
fun WaitingRoomOverlay(
    state: OnlineState,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xF2111118)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .background(Color(0xFF1C1C28), RoundedCornerShape(20.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = state.roomName,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(4.dp))
            state.players.forEach { player ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val ballColor = if (player.role == OnlineRole.WHITE) WhiteBall else BlackBall
                    Ball(color = ballColor, size = 8.dp)
                    Text(
                        text = player.nickname,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp
                    )
                    Text(
                        text = player.role.name,
                        color = Color.White.copy(alpha = 0.35f),
                        fontSize = 9.sp,
                        letterSpacing = 1.sp
                    )
                }
            }
            if (state.status == OnlineStatus.WAITING_OPPONENT) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "상대방을 기다리는 중...",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp
                )
            }
            TextButton(onClick = onLeave) {
                Text("← LEAVE", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, letterSpacing = 2.sp)
            }
        }
    }
}
