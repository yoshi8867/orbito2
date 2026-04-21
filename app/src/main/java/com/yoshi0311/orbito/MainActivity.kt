package com.yoshi0311.orbito

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.yoshi0311.orbito.model.BotVsBotMode
import com.yoshi0311.orbito.model.GameConfig
import com.yoshi0311.orbito.ui.BatchScreen
import com.yoshi0311.orbito.ui.GameScreen
import com.yoshi0311.orbito.ui.SetupScreen
import com.yoshi0311.orbito.ui.StartScreen
import com.yoshi0311.orbito.ui.theme.OrbitoTheme

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

                when (screen) {
                    "start" -> StartScreen(
                        onStart = { screen = "setup" },
                        modifier = Modifier.fillMaxSize()
                    )
                    "setup" -> SetupScreen(
                        onStartGame = { config ->
                            pendingConfig = config
                            gameKey++
                            screen = if (config.isBotVsBot && config.botVsBotMode == BotVsBotMode.BATCH) "batch" else "game"
                        },
                        onBack = { screen = "start" },
                        modifier = Modifier.fillMaxSize()
                    )
                    "batch" -> BatchScreen(
                        config = pendingConfig,
                        sessionKey = gameKey,
                        onBack = { screen = "setup" },
                        modifier = Modifier.fillMaxSize()
                    )
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
