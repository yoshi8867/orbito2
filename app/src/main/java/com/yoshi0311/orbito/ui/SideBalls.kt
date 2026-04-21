package com.yoshi0311.orbito.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SideBallsPanel(
    count: Int,
    ballColor: Color,
    ballSize: Dp,
    isTablet: Boolean,
    modifier: Modifier = Modifier
) {
    val gap = if (isTablet) 6.dp else 4.dp

    if (isTablet) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
            for (row in 0 until 4) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (col in 0 until 2) {
                        SideBall(visible = row * 2 + col < count, ballColor = ballColor, size = ballSize)
                    }
                }
            }
        }
    } else {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
            repeat(8) { idx ->
                SideBall(visible = idx < count, ballColor = ballColor, size = ballSize)
            }
        }
    }
}

@Composable
private fun SideBall(visible: Boolean, ballColor: Color, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (visible) Modifier.background(ballColor)
                else Modifier.border(1.dp, ballColor.copy(alpha = 0.25f), CircleShape)
            )
    )
}
