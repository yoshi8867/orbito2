package com.yoshi0311.orbito.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yoshi0311.orbito.model.GameState
import com.yoshi0311.orbito.ui.theme.AppBackground
import com.yoshi0311.orbito.ui.theme.BlackBall
import com.yoshi0311.orbito.ui.theme.WhiteBall
import com.yoshi0311.orbito.viewmodel.ReplayUiState
import com.yoshi0311.orbito.viewmodel.ReplayViewModel

@Composable
fun ReplayScreen(
    sessionKey: Int = 0,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ReplayViewModel = viewModel(key = "replay_$sessionKey")
) {
    TrackScreenTime("stat_min_replay")
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.openOutputStream(uri)?.use { it.write(state.record.toByteArray()) }
    }

    val loadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.openInputStream(uri)?.use { stream ->
            viewModel.loadFromString(stream.bufferedReader().readText())
        }
    }

    LaunchedEffect(state.toast) {
        val msg = state.toast ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        viewModel.dismissToast()
    }

    val onCopy = {
        if (state.record.isNotBlank()) {
            clipboardManager.setText(AnnotatedString(state.record))
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }
    val onSaveRecord = {
        if (state.record.isNotBlank()) saveLauncher.launch(viewModel.getDefaultFileName())
    }
    val onLoadRecord = { loadLauncher.launch(arrayOf("text/plain")) }

    Box(modifier = modifier.fillMaxSize().background(AppBackground)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val isTablet = maxWidth >= 600.dp
            val cellSize: Dp = if (isTablet) minOf(maxWidth * 0.38f, maxHeight * 0.5f) / 4
                               else (maxWidth - 100.dp) / 4
            val ballSize: Dp = cellSize * 0.68f
            val sideBallSize: Dp = if (isTablet) 22.dp else 16.dp

            if (isTablet) {
                Row(Modifier.fillMaxSize().padding(top = 48.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.62f)
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        ReplayBoardSection(
                            state = state,
                            cellSize = cellSize, ballSize = ballSize, sideBallSize = sideBallSize,
                            isTablet = true,
                            onFirst = viewModel::goFirst, onPrev = viewModel::goPrev,
                            onNext = viewModel::goNext, onLast = viewModel::goLast,
                            onRotationComplete = viewModel::onRotationComplete
                        )
                    }
                    ReplayEditorPanel(
                        record = state.record,
                        hasRecord = state.record.isNotBlank(),
                        onClear = viewModel::clearRecord,
                        onSaveRecord = onSaveRecord,
                        onLoadRecord = onLoadRecord,
                        onCopy = onCopy,
                        modifier = Modifier.fillMaxHeight().weight(0.38f)
                    )
                }
            } else {
                Column(Modifier.fillMaxSize().padding(top = 48.dp)) {
                    ReplayEditorPanel(
                        record = state.record,
                        hasRecord = state.record.isNotBlank(),
                        onClear = viewModel::clearRecord,
                        onSaveRecord = onSaveRecord,
                        onLoadRecord = onLoadRecord,
                        onCopy = onCopy,
                        modifier = Modifier.fillMaxWidth().weight(0.33f)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.67f)
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        ReplayBoardSection(
                            state = state,
                            cellSize = cellSize, ballSize = ballSize, sideBallSize = sideBallSize,
                            isTablet = false,
                            onFirst = viewModel::goFirst, onPrev = viewModel::goPrev,
                            onNext = viewModel::goNext, onLast = viewModel::goLast,
                            onRotationComplete = viewModel::onRotationComplete
                        )
                    }
                }
            }
        }

        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 4.dp)
        ) {
            Text("←", color = Color.White.copy(alpha = 0.45f), fontSize = 18.sp)
        }
    }
}

// ── Board section ─────────────────────────────────────────────────────────────

@Composable
private fun ReplayBoardSection(
    state: ReplayUiState,
    cellSize: Dp, ballSize: Dp, sideBallSize: Dp, isTablet: Boolean,
    onFirst: () -> Unit, onPrev: () -> Unit, onNext: () -> Unit, onLast: () -> Unit,
    onRotationComplete: () -> Unit
) {
    val display: GameState = state.displayBoardState ?: GameState()
    val canPrev = !state.isAnimating && state.currentIndex > 0
    val canNext = !state.isAnimating && state.currentIndex < state.moveLines.size

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SideBallsPanel(display.whiteSideCount, WhiteBall, sideBallSize, isTablet)
            Spacer(Modifier.width(10.dp))
            BoardGrid(
                state = display,
                cellSize = cellSize,
                ballSize = ballSize,
                onCellTap = { _, _ -> },
                onRotationComplete = onRotationComplete
            )
            Spacer(Modifier.width(10.dp))
            SideBallsPanel(display.blackSideCount, BlackBall, sideBallSize, isTablet)
        }

        Text(
            text = "${state.currentIndex} / ${state.moveLines.size}",
            color = Color.White.copy(alpha = 0.35f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReplayNavButton("FIRST", canPrev, onFirst)
            ReplayNavButton("PREV",  canPrev, onPrev)
            ReplayNavButton("NEXT",  canNext, onNext)
            ReplayNavButton("LAST",  canNext, onLast)
        }
    }
}

@Composable
private fun ReplayNavButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .border(1.dp, Color.White.copy(alpha = if (enabled) 0.35f else 0.12f), RoundedCornerShape(12.dp))
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
        Text(
            text = text,
            color = Color.White.copy(alpha = if (enabled) 0.7f else 0.22f),
            fontSize = 10.sp,
            letterSpacing = 1.sp
        )
    }
}

// ── Editor panel ──────────────────────────────────────────────────────────────

@Composable
private fun ReplayEditorPanel(
    record: String,
    hasRecord: Boolean,
    onClear: () -> Unit,
    onSaveRecord: () -> Unit,
    onLoadRecord: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.background(Color(0xFF1E1E1E))) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ReplayEditorButton("CLEAR",       enabled = hasRecord, onClick = onClear)
            ReplayEditorButton("SAVE RECORD", enabled = hasRecord, onClick = onSaveRecord)
            ReplayEditorButton("LOAD RECORD", enabled = true, onClick = onLoadRecord)
            ReplayEditorButton("COPY",        enabled = hasRecord, onClick = onCopy)
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))

        val scrollState = rememberScrollState()
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(scrollState)) {
            if (!hasRecord) {
                Text(
                    text = "— no record —",
                    color = Color.White.copy(alpha = 0.22f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(12.dp)
                )
            } else {
                Text(
                    text = record,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp,
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun ReplayEditorButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = if (enabled) 0.55f else 0.2f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp
        )
    }
}
