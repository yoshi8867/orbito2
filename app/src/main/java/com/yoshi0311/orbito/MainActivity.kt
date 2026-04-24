package com.yoshi0311.orbito

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.yoshi0311.orbito.model.BotVsBotMode
import com.yoshi0311.orbito.model.GameConfig
import com.yoshi0311.orbito.model.OnlineStatus
import com.yoshi0311.orbito.ui.BatchScreen
import com.yoshi0311.orbito.ui.BotEditScreen
import com.yoshi0311.orbito.ui.GameScreen
import com.yoshi0311.orbito.ui.OnlineGameScreen
import com.yoshi0311.orbito.ui.OnlineModal
import com.yoshi0311.orbito.ui.SetupScreen
import com.yoshi0311.orbito.ui.StartScreen
import com.yoshi0311.orbito.ui.WaitingRoomOverlay
import com.yoshi0311.orbito.ui.theme.OrbitoTheme
import com.yoshi0311.orbito.viewmodel.BotListViewModel
import com.yoshi0311.orbito.viewmodel.OnlineViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        hideNavBar()
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        setContent {
            OrbitoTheme {
                var screen by rememberSaveable { mutableStateOf("start") }
                var pendingConfig by remember { mutableStateOf(GameConfig()) }
                var gameKey by remember { mutableIntStateOf(0) }
                var botEditOrigin by remember { mutableStateOf("setup") }
                var botEditName by remember { mutableStateOf<String?>(null) }
                var botEditKey by remember { mutableIntStateOf(0) }
                val pendingOnlineRecord = remember { mutableStateOf<String?>(null) }
                val onlineSaveLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("text/plain")
                ) { uri ->
                    val record = pendingOnlineRecord.value ?: return@rememberLauncherForActivityResult
                    uri ?: return@rememberLauncherForActivityResult
                    contentResolver.openOutputStream(uri)?.use { it.write(record.toByteArray()) }
                    pendingOnlineRecord.value = null
                }

                val onlineVm: OnlineViewModel = viewModel()
                val onlineState by onlineVm.state.collectAsStateWithLifecycle()
                val botListVm: BotListViewModel = viewModel()
                val userBots by botListVm.userBots.collectAsStateWithLifecycle()

                // Refresh bot list when returning to setup/batch from bot_edit
                LaunchedEffect(screen) {
                    if (screen == "setup" || screen == "batch") botListVm.refresh()
                }

                fun navigateToBotEdit(name: String?, origin: String) {
                    botEditName = name
                    botEditOrigin = origin
                    botEditKey++
                    screen = "bot_edit"
                }

                when (screen) {
                    "start" -> StartScreen(
                        onStart = { screen = "setup" },
                        onOnline = {
                            onlineVm.openLobby()
                            screen = "online_lobby"
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    "setup" -> SetupScreen(
                        onStartGame = { config ->
                            pendingConfig = config
                            gameKey++
                            screen = if (config.isBotVsBot && config.botVsBotMode == BotVsBotMode.BATCH) "batch" else "game"
                        },
                        onBack = { screen = "start" },
                        onNewBot = { navigateToBotEdit(null, "setup") },
                        onEditBot = { name -> navigateToBotEdit(name, "setup") },
                        userBots = userBots,
                        modifier = Modifier.fillMaxSize()
                    )
                    "bot_edit" -> BotEditScreen(
                        initialBotName = botEditName,
                        sessionKey = botEditKey,
                        onBack = { screen = botEditOrigin },
                        modifier = Modifier.fillMaxSize()
                    )
                    "batch" -> BatchScreen(
                        config = pendingConfig,
                        sessionKey = gameKey,
                        onBack = { screen = "setup" },
                        userBots = userBots,
                        onNewBot = { navigateToBotEdit(null, "batch") },
                        onEditBot = { name -> navigateToBotEdit(name, "batch") },
                        modifier = Modifier.fillMaxSize()
                    )
                    "online_lobby" -> OnlineModal(
                        state = onlineState,
                        onCreateRoom = { nick -> onlineVm.createRoom(nick); screen = "online_waiting" },
                        onJoinRoom = { room, nick -> onlineVm.joinRoom(room, nick); screen = "online_waiting" },
                        onRefresh = onlineVm::refreshRooms,
                        onDismiss = {
                            onlineVm.leaveRoom()
                            screen = "start"
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    "online_waiting" -> {
                        when (onlineState.status) {
                            OnlineStatus.IN_GAME -> screen = "online_game"
                            OnlineStatus.DISCONNECTED -> {
                                onlineVm.leaveRoom()
                                screen = "start"
                            }
                            else -> WaitingRoomOverlay(
                                state = onlineState,
                                onLeave = {
                                    onlineVm.leaveRoom()
                                    screen = "start"
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    "online_game" -> {
                        when (onlineState.status) {
                            OnlineStatus.DISCONNECTED -> {
                                onlineVm.leaveRoom()
                                screen = "start"
                            }
                            else -> OnlineGameScreen(
                                state = onlineState,
                                onCellTap = onlineVm::onCellTap,
                                onSendChat = onlineVm::sendChat,
                                onLeave = {
                                    onlineVm.leaveRoom()
                                    screen = "start"
                                },
                                onRotationComplete = onlineVm::onRotationComplete,
                                onSaveRecord = {
                                    val record = onlineVm.generateRecord()
                                    onlineVm.saveLastRecord(record)
                                    pendingOnlineRecord.value = record
                                    onlineSaveLauncher.launch(onlineVm.defaultFileName())
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    else -> GameScreen(
                        config = pendingConfig,
                        sessionKey = gameKey,
                        onBack = { screen = "setup" },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideNavBar()
    }

    private fun hideNavBar() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
