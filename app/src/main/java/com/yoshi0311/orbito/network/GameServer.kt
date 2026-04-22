package com.yoshi0311.orbito.network

import android.util.Log
import com.yoshi0311.orbito.model.CellState
import com.yoshi0311.orbito.model.GamePhase
import com.yoshi0311.orbito.model.OnlineRole
import com.yoshi0311.orbito.model.Player
import com.yoshi0311.orbito.model.ROTATION_MAPPING
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

private const val TAG = "GameServer"
private const val PING_INTERVAL = 5000L
private const val PING_TIMEOUT = 15000L
private const val RECONNECT_WINDOW = 30

sealed class ServerEvent {
    data class RoomUpdate(val players: List<NetPlayer>) : ServerEvent()
    data class GameUpdate(val msg: NetMsg) : ServerEvent()
    data class ChatReceived(val nick: String, val message: String) : ServerEvent()
    data class PlayerDisconnected(val nick: String, val role: String, val secondsLeft: Int) : ServerEvent()
    data class PlayerReconnected(val nick: String) : ServerEvent()
}

data class ClientConn(val nick: String, val role: OnlineRole, val writer: PrintWriter)

class GameServer {
    private var board = List(4) { List(4) { CellState.EMPTY } }
    private var whiteSideCount = 8
    private var blackSideCount = 8
    private var currentPlayer = Player.WHITE
    private var phase = GamePhase.OPTIONAL_MOVE
    private var winner: Player? = null
    private var gameStarted = false

    private val clients = mutableListOf<ClientConn>()
    private val pendingReconnect = mutableMapOf<String, OnlineRole>()
    private var hostNick = ""
    private var roomName = ""

    private val _events = MutableSharedFlow<ServerEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ServerEvent> = _events

    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val port: Int get() = serverSocket?.localPort ?: 0

    fun start(room: String, hostNickname: String): Int {
        roomName = room
        hostNick = hostNickname
        serverSocket = ServerSocket(0)
        val p = serverSocket!!.localPort
        Log.d(TAG, "Server started: room='$room' host='$hostNickname' port=$p")
        scope.launch {
            while (isActive) {
                try {
                    val sock = serverSocket?.accept() ?: break
                    Log.d(TAG, "New TCP connection from ${sock.inetAddress.hostAddress}:${sock.port}")
                    launch { handleClient(sock) }
                } catch (e: Exception) {
                    Log.e(TAG, "Accept error: ${e.message}")
                    break
                }
            }
            Log.d(TAG, "Accept loop ended.")
        }
        return p
    }

    private suspend fun handleClient(socket: Socket) {
        val addr = "${socket.inetAddress.hostAddress}:${socket.port}"
        Log.d(TAG, "handleClient start — $addr")
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val writer = PrintWriter(socket.getOutputStream(), true)
        var conn: ClientConn? = null
        var lastPong = System.currentTimeMillis()

        val pingJob = scope.launch {
            while (isActive) {
                delay(PING_INTERVAL)
                val elapsed = System.currentTimeMillis() - lastPong
                if (elapsed > PING_TIMEOUT) {
                    Log.w(TAG, "Ping timeout for $addr (${elapsed}ms). Closing.")
                    socket.close()
                    return@launch
                }
                writer.println(NetMsg(type = "ping").encode())
            }
        }
        try {
            while (true) {
                val line = withContext(Dispatchers.IO) { reader.readLine() } ?: break
                Log.d(TAG, "[$addr] recv: $line")
                val msg = line.decodeMsg() ?: continue
                when (msg.type) {
                    "join" -> {
                        val nick = msg.nickname ?: run { Log.w(TAG, "join without nickname"); continue }
                        Log.d(TAG, "join request from '$nick'")
                        conn = assignAndJoin(nick, writer)
                        Log.d(TAG, "'$nick' assigned role=${conn.role}")
                        broadcastRoomState()
                        if (gameStarted) {
                            Log.d(TAG, "Game in progress — sending game_state to '$nick'")
                            writer.println(buildGameMsg().encode())
                        }
                        _events.emit(ServerEvent.RoomUpdate(buildNetPlayers()))
                    }
                    "move" -> {
                        Log.d(TAG, "move from '${conn?.nick}': optSrc=${msg.optSrc} optDst=${msg.optDst} placePos=${msg.placePos}")
                        conn?.let { applyClientMove(it, msg) }
                    }
                    "chat" -> {
                        val nick = conn?.nick ?: run { Log.w(TAG, "chat from unknown conn"); continue }
                        val text = msg.message ?: continue
                        Log.d(TAG, "chat from '$nick': $text")
                        broadcastAll(NetMsg(type = "chat", nickname = nick, message = text).encode())
                        _events.emit(ServerEvent.ChatReceived(nick, text))
                    }
                    "pong" -> {
                        lastPong = System.currentTimeMillis()
                    }
                    else -> Log.w(TAG, "Unknown msg type: ${msg.type}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "handleClient exception for $addr: ${e.message}")
        } finally {
            pingJob.cancel()
            Log.d(TAG, "handleClient finally for $addr  conn=${conn?.nick}")
            conn?.let { onDisconnect(it) }
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun assignAndJoin(nick: String, writer: PrintWriter): ClientConn {
        val prevRole = pendingReconnect.remove(nick)
        if (prevRole != null) {
            Log.d(TAG, "Reconnect: '$nick' restoring role=$prevRole")
            val conn = ClientConn(nick, prevRole, writer)
            synchronized(clients) { clients.add(conn) }
            writer.println(NetMsg(type = "assigned", nickname = nick, role = prevRole.toStr()).encode())
            broadcastAll(NetMsg(type = "player_reconnected", nickname = nick).encode())
            scope.launch { _events.emit(ServerEvent.PlayerReconnected(nick)) }
            return conn
        }
        val taken = synchronized(clients) { clients.map { it.role }.toSet() }
        Log.d(TAG, "assignAndJoin: taken roles=$taken")
        val role = if (OnlineRole.BLACK !in taken) OnlineRole.BLACK else OnlineRole.SPECTATOR
        Log.d(TAG, "Assigning '$nick' → $role")
        val conn = ClientConn(nick, role, writer)
        synchronized(clients) { clients.add(conn) }
        writer.println(NetMsg(type = "assigned", nickname = nick, role = role.toStr()).encode())
        Log.d(TAG, "Sent 'assigned' to '$nick' with role=${role.toStr()}")

        if (role == OnlineRole.BLACK) {
            Log.d(TAG, "Black joined — starting game!")
            gameStarted = true
            broadcastAndEmitGameState()
        }

        return conn
    }

    private fun onDisconnect(conn: ClientConn) {
        Log.d(TAG, "onDisconnect: '${conn.nick}' role=${conn.role}")
        synchronized(clients) { clients.remove(conn) }
        if (conn.role == OnlineRole.SPECTATOR) {
            broadcastRoomState()
            scope.launch { _events.emit(ServerEvent.RoomUpdate(buildNetPlayers())) }
            return
        }
        pendingReconnect[conn.nick] = conn.role
        val roleStr = conn.role.toStr()
        broadcastAll(NetMsg(type = "player_disconnected", nickname = conn.nick, role = roleStr, secondsLeft = RECONNECT_WINDOW).encode())
        scope.launch {
            _events.emit(ServerEvent.PlayerDisconnected(conn.nick, roleStr, RECONNECT_WINDOW))
            for (s in (RECONNECT_WINDOW - 1) downTo 0) {
                delay(1000)
                if (!pendingReconnect.containsKey(conn.nick)) return@launch
                broadcastAll(NetMsg(type = "player_disconnected", nickname = conn.nick, role = roleStr, secondsLeft = s).encode())
                _events.emit(ServerEvent.PlayerDisconnected(conn.nick, roleStr, s))
                if (s == 0) {
                    pendingReconnect.remove(conn.nick)
                    winner = if (conn.role == OnlineRole.BLACK) Player.WHITE else Player.BLACK
                    phase = GamePhase.DONE
                    Log.d(TAG, "Reconnect window expired for '${conn.nick}' — winner=$winner")
                    broadcastAndEmitGameState()
                }
            }
        }
    }

    private fun applyClientMove(conn: ClientConn, msg: NetMsg) {
        val expectedRole = if (currentPlayer == Player.WHITE) OnlineRole.WHITE else OnlineRole.BLACK
        if (conn.role != expectedRole) {
            Log.w(TAG, "Move from '${conn.nick}' (${conn.role}) but expected $expectedRole — ignored")
            return
        }
        applyMove(msg.optSrc, msg.optDst, msg.placePos ?: return)
    }

    fun hostMove(optSrc: Int?, optDst: Int?, placePos: Int) {
        Log.d(TAG, "hostMove: optSrc=$optSrc optDst=$optDst placePos=$placePos currentPlayer=$currentPlayer")
        scope.launch(Dispatchers.IO) {
            if (currentPlayer != Player.WHITE) {
                Log.w(TAG, "hostMove called but currentPlayer=$currentPlayer — ignored")
                return@launch
            }
            applyMove(optSrc, optDst, placePos)
        }
    }

    private fun applyMove(optSrc: Int?, optDst: Int?, placePos: Int) {
        if (winner != null) return
        if (optSrc != null && optDst != null) {
            val opponentColor = if (currentPlayer == Player.WHITE) CellState.BLACK else CellState.WHITE
            val sr = optSrc / 4; val sc = optSrc % 4
            val dr = optDst / 4; val dc = optDst % 4
            if (board[sr][sc] == opponentColor && board[dr][dc] == CellState.EMPTY && isAdj(sr, sc, dr, dc)) {
                val nb = mutableBoard(board)
                nb[dr][dc] = nb[sr][sc]; nb[sr][sc] = CellState.EMPTY
                board = nb.toImmutable()
                Log.d(TAG, "Optional move applied: ($sr,$sc)→($dr,$dc)")
            }
        }
        val r = placePos / 4; val c = placePos % 4
        if (r !in 0..3 || c !in 0..3 || board[r][c] != CellState.EMPTY) {
            Log.w(TAG, "applyMove: invalid placePos=$placePos (r=$r,c=$c cell=${board[r][c]}) — ignored")
            return
        }
        val sideCount = if (currentPlayer == Player.WHITE) whiteSideCount else blackSideCount
        if (sideCount <= 0) {
            Log.w(TAG, "applyMove: no stones left for $currentPlayer")
            return
        }

        val color = if (currentPlayer == Player.WHITE) CellState.WHITE else CellState.BLACK
        val nb = mutableBoard(board)
        nb[r][c] = color
        board = rotate(nb.toImmutable())
        if (currentPlayer == Player.WHITE) whiteSideCount-- else blackSideCount--
        gameStarted = true
        Log.d(TAG, "Placed $color at ($r,$c). After rotate. W=$whiteSideCount B=$blackSideCount")

        val winners = checkWinners(board)
        winner = when {
            winners.size == 2 -> if (currentPlayer == Player.WHITE) Player.BLACK else Player.WHITE
            winners.size == 1 -> winners.first()
            whiteSideCount == 0 && blackSideCount == 0 -> currentPlayer
            else -> null
        }
        if (winner != null) {
            phase = GamePhase.DONE
            Log.d(TAG, "Winner: $winner")
        } else {
            currentPlayer = if (currentPlayer == Player.WHITE) Player.BLACK else Player.WHITE
            phase = GamePhase.OPTIONAL_MOVE
        }
        broadcastAndEmitGameState()
    }

    private fun broadcastAndEmitGameState() {
        val gameMsg = buildGameMsg()
        Log.d(TAG, "broadcastAndEmitGameState: currentPlayer=$currentPlayer phase=$phase winner=$winner")
        broadcastAll(gameMsg.encode())
        scope.launch { _events.emit(ServerEvent.GameUpdate(gameMsg)) }
    }

    private fun buildGameMsg() = NetMsg(
        type = "game_state",
        board = encodeBoard(board),
        currentPlayer = currentPlayer.toStr(),
        phase = phase.toStr(),
        whiteSideCount = whiteSideCount,
        blackSideCount = blackSideCount,
        winner = winner?.toStr()
    )

    private fun broadcastRoomState() {
        val msg = NetMsg(type = "room_state", roomName = roomName, players = buildNetPlayers())
        Log.d(TAG, "broadcastRoomState: players=${buildNetPlayers().map { it.nickname }}")
        broadcastAll(msg.encode())
    }

    private fun buildNetPlayers(): List<NetPlayer> {
        val list = mutableListOf(NetPlayer(hostNick, "white", true))
        synchronized(clients) { clients.forEach { list.add(NetPlayer(it.nick, it.role.toStr(), true)) } }
        pendingReconnect.forEach { (nick, role) ->
            if (list.none { it.nickname == nick }) list.add(NetPlayer(nick, role.toStr(), false))
        }
        return list
    }

    fun broadcastChat(fromNick: String, message: String) {
        Log.d(TAG, "broadcastChat: '$fromNick': $message")
        scope.launch(Dispatchers.IO) {
            broadcastAll(NetMsg(type = "chat", nickname = fromNick, message = message).encode())
        }
    }

    private fun broadcastAll(msg: String) {
        synchronized(clients) {
            Log.d(TAG, "broadcastAll to ${clients.size} clients: $msg")
            clients.forEach { c -> try { c.writer.println(msg) } catch (e: Exception) { Log.e(TAG, "broadcastAll error for '${c.nick}': ${e.message}") } }
        }
    }

    private fun isAdj(r1: Int, c1: Int, r2: Int, c2: Int) =
        kotlin.math.abs(r1 - r2) + kotlin.math.abs(c1 - c2) == 1

    private fun rotate(b: List<List<CellState>>): List<List<CellState>> {
        val n = mutableBoard(b)
        for ((src, dst) in ROTATION_MAPPING) n[dst.first][dst.second] = b[src.first][src.second]
        return n.toImmutable()
    }

    private fun checkWinners(b: List<List<CellState>>): Set<Player> {
        fun CellState.p() = if (this == CellState.WHITE) Player.WHITE else Player.BLACK
        val winners = mutableSetOf<Player>()
        for (r in 0..3) { val c = b[r][0]; if (c != CellState.EMPTY && b[r].all { it == c }) winners.add(c.p()) }
        for (col in 0..3) { val c = b[0][col]; if (c != CellState.EMPTY && (0..3).all { b[it][col] == c }) winners.add(c.p()) }
        val d1 = b[0][0]; if (d1 != CellState.EMPTY && (0..3).all { b[it][it] == d1 }) winners.add(d1.p())
        val d2 = b[0][3]; if (d2 != CellState.EMPTY && (0..3).all { b[it][3 - it] == d2 }) winners.add(d2.p())
        return winners
    }

    private fun mutableBoard(b: List<List<CellState>>) = b.map { it.toMutableList() }.toMutableList()
    private fun List<MutableList<CellState>>.toImmutable() = map { it.toList() }

    fun stop() {
        Log.d(TAG, "stop() called")
        broadcastAll(NetMsg(type = "room_closed").encode())
        scope.cancel()
        try { serverSocket?.close() } catch (_: Exception) {}
    }
}
