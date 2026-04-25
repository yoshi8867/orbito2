package com.yoshi0311.orbito.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
fun TrackScreenTime(key: String) {
    val context = LocalContext.current
    val enterTime = remember { System.currentTimeMillis() }
    DisposableEffect(Unit) {
        onDispose {
            val elapsed = System.currentTimeMillis() - enterTime
            val minutes = ((elapsed + 30_000L) / 60_000L).toInt()
            if (minutes > 0) {
                val prefs = context.getSharedPreferences("orbito_prefs", Context.MODE_PRIVATE)
                prefs.edit().putInt(key, prefs.getInt(key, 0) + minutes).apply()
            }
        }
    }
}
