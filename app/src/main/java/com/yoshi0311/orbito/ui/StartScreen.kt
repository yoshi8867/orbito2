package com.yoshi0311.orbito.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.yoshi0311.orbito.model.StatSender
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yoshi0311.orbito.ui.theme.AppBackground
import com.yoshi0311.orbito.ui.theme.BlackBall
import com.yoshi0311.orbito.ui.theme.WhiteBall

@Composable
fun StartScreen(
    onStart: () -> Unit,
    onOnline: () -> Unit,
    onReplay: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { StatSender.sendIfNeeded(context) }
    val isWifiConnected by produceState(initialValue = false, context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        fun check(): Boolean {
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }
        value = check()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(n: Network, c: NetworkCapabilities) {
                value = check()
            }
            override fun onLost(n: Network) { value = check() }
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        cm.registerNetworkCallback(request, callback)
        awaitDispose { cm.unregisterNetworkCallback(callback) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Text(
                text = "ORBITO",
                color = Color.White,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 8.sp
            )

            Spacer(Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Ball(color = WhiteBall, size = 14.dp)
                Ball(color = BlackBall, size = 14.dp)
            }

            Spacer(Modifier.height(48.dp))

            StartMenuButton(text = "START", onClick = onStart)

            Spacer(Modifier.height(12.dp))

            StartMenuButton(
                text = "ONLINE",
                enabled = isWifiConnected,
                onClick = {
                    if (isWifiConnected) onOnline()
                    else Toast.makeText(context, "Wi-Fi required", Toast.LENGTH_SHORT).show()
                }
            )

            Spacer(Modifier.height(12.dp))

            StartMenuButton(text = "REPLAY", onClick = onReplay)
        }
    }
}

@Composable
private fun StartMenuButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = if (enabled) 0.5f else 0.15f),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(horizontal = 8.dp)
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = if (enabled) 1f else 0.3f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 4.sp
        )
    }
}
