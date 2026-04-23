package com.yoshi0311.orbito.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

private const val TAG = "GameClient"

class GameClient {
    private val _messages = MutableSharedFlow<NetMsg>(extraBufferCapacity = 64)
    val messages: SharedFlow<NetMsg> = _messages

    private var writer: PrintWriter? = null
    private var socket: Socket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun connect(host: String, port: Int, onConnected: () -> Unit = {}) {
        Log.d(TAG, "connect() called → $host:$port")
        scope.launch {
            try {
                Log.d(TAG, "Connecting socket to $host:$port...")
                val sock = withContext(Dispatchers.IO) { Socket(host, port) }
                socket = sock
                writer = PrintWriter(sock.getOutputStream(), true)
                Log.d(TAG, "Socket connected. Calling onConnected().")
                onConnected()
                val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
                Log.d(TAG, "Starting read loop.")
                while (isActive) {
                    val line = withContext(Dispatchers.IO) { reader.readLine() } ?: break
                    Log.d(TAG, "Received: $line")
                    val msg = line.decodeMsg() ?: continue
                    if (msg.type == "ping") {
                        Log.d(TAG, "ping → pong")
                        send(NetMsg(type = "pong"))
                        continue
                    }
                    _messages.emit(msg)
                }
                Log.d(TAG, "Read loop ended (null line).")
            } catch (e: Exception) {
                Log.e(TAG, "Connection error: ${e.message}", e)
                _messages.emit(NetMsg(type = "room_closed"))
            }
        }
    }

    fun send(msg: NetMsg) {
        scope.launch(Dispatchers.IO) {
            val encoded = msg.encode()
            Log.d(TAG, "send() writer=${writer != null}  msg=$encoded")
            try {
                writer?.println(encoded) ?: Log.w(TAG, "send() skipped — writer is null")
            } catch (e: Exception) {
                Log.e(TAG, "Send error: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    fun stop() {
        Log.d(TAG, "stop() called")
        scope.cancel()
        try { socket?.close() } catch (_: Exception) {}
    }
}
