package com.yoshi0311.orbito.viewmodel

import android.app.Application
import android.net.wifi.WifiManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yoshi0311.orbito.model.CellState
import com.yoshi0311.orbito.model.ChatMessage
import com.yoshi0311.orbito.model.GamePhase
import com.yoshi0311.orbito.model.OnlineGameState
import com.yoshi0311.orbito.model.OnlinePlayer
import com.yoshi0311.orbito.model.OnlineRole
import com.yoshi0311.orbito.model.OnlineState
import com.yoshi0311.orbito.model.OnlineStatus
import com.yoshi0311.orbito.model.Player
import com.yoshi0311.orbito.model.RoomInfo
import com.yoshi0311.orbito.network.GameClient
import com.yoshi0311.orbito.network.GameServer
import com.yoshi0311.orbito.network.NetMsg
import com.yoshi0311.orbito.network.NsdHelper
import com.yoshi0311.orbito.network.ServerEvent
import com.yoshi0311.orbito.network.decodeBoard
import com.yoshi0311.orbito.network.toGamePhase
import com.yoshi0311.orbito.network.toModel
import com.yoshi0311.orbito.network.toPlayer
import com.yoshi0311.orbito.network.toRole
import com.yoshi0311.orbito.network.toStr
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "OnlineViewModel"

class OnlineViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(OnlineState())
    val state: StateFlow<OnlineState> = _state.asStateFlow()

    private var nsd: NsdHelper? = null
    private var server: GameServer? = null
    private var client: GameClient? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    // Pending optional move (not yet committed to server)
    private var pendingOptSrc: Int? = null
    private var pendingOptDst: Int? = null

    private val prefs by lazy {
        getApplication<Application>().getSharedPreferences("orbito_prefs", android.content.Context.MODE_PRIVATE)
    }
    private var savedNickname: String
        get() = prefs.getString("nickname", "") ?: ""
        set(value) { prefs.edit().putString("nickname", value).apply() }

    fun openLobby() {
        Log.d(TAG, "openLobby()")
        acquireMulticastLock()
        val helper = NsdHelper(getApplication()).also { nsd = it }
        helper.startDiscovery()
        _state.value = OnlineState(status = OnlineStatus.LOBBY, myNickname = savedNickname)
        viewModelScope.launch {
            helper.rooms.collect { rooms ->
                Log.d(TAG, "Discovered rooms: ${rooms.map { it.name }}")
                _state.value = _state.value.copy(discoveredRooms = rooms)
            }
        }
    }

    fun refreshRooms() { nsd?.refresh() }

    fun createRoom(nickname: String) {
        if (nickname.isBlank()) return
        savedNickname = nickname
        val roomName = "${nickname}'s ROOM"
        Log.d(TAG, "createRoom: nick='$nickname' room='$roomName'")
        val srv = GameServer().also { server = it }
        val port = srv.start(roomName, nickname)
        Log.d(TAG, "Server started on port $port")
        nsd?.stopDiscovery()
        nsd?.register(roomName, port)
        _state.value = _state.value.copy(
            status = OnlineStatus.WAITING_OPPONENT,
            myNickname = nickname,
            myRole = OnlineRole.WHITE,
            isHost = true,
            roomName = roomName,
            players = listOf(OnlinePlayer(nickname, OnlineRole.WHITE, true))
        )
        viewModelScope.launch {
            srv.events.collect { event ->
                Log.d(TAG, "ServerEvent: $event")
                handleServerEvent(event)
            }
        }
    }

    fun joinRoom(room: RoomInfo, nickname: String) {
        if (nickname.isBlank()) return
        savedNickname = nickname
        Log.d(TAG, "joinRoom: nick='$nickname' room='${room.name}' host=${room.host}:${room.port}")
        nsd?.stopDiscovery()
        _state.value = _state.value.copy(status = OnlineStatus.CONNECTING, myNickname = nickname)
        val cli = GameClient().also { client = it }
        // onConnected callback: send join AFTER socket is ready (fixes race condition)
        cli.connect(room.host, room.port) {
            Log.d(TAG, "Socket ready — sending join as '$nickname'")
            cli.send(NetMsg(type = "join", nickname = nickname))
        }
        viewModelScope.launch {
            cli.messages.collect { msg ->
                Log.d(TAG, "ClientMsg: type=${msg.type} role=${msg.role} status=${msg.players}")
                handleClientMsg(msg)
            }
        }
    }

    // Called when current player taps a cell
    fun onCellTap(row: Int, col: Int) {
        val s = _state.value
        if (s.status != OnlineStatus.IN_GAME) return
        if (s.game.winner != null) return

        val isMyTurn = when (s.myRole) {
            OnlineRole.WHITE -> s.game.currentPlayer == Player.WHITE
            OnlineRole.BLACK -> s.game.currentPlayer == Player.BLACK
            OnlineRole.SPECTATOR -> false
        }
        if (!isMyTurn) return

        when (s.effectivePhase) {
            GamePhase.OPTIONAL_MOVE -> handleOptionalTap(row, col)
            GamePhase.PLACE -> handlePlaceTap(row, col)
            GamePhase.DONE -> {}
        }
    }

    private fun handleOptionalTap(row: Int, col: Int) {
        val s = _state.value
        val g = s.game
        val opponentColor = if (s.myRole == OnlineRole.WHITE) CellState.BLACK else CellState.WHITE
        val sel = s.selectedCell
        when {
            sel != null && sel.first == row && sel.second == col ->
                _state.value = s.copy(selectedCell = null)
            sel != null && g.board[row][col] == CellState.EMPTY && isAdj(sel, row, col) -> {
                pendingOptSrc = sel.first * 4 + sel.second
                pendingOptDst = row * 4 + col
                // Show updated board locally and enter PLACE phase
                val newBoard = applyOptMoveLocally(g.board, sel.first, sel.second, row, col)
                _state.value = s.copy(
                    game = g.copy(board = newBoard),
                    selectedCell = null,
                    localPhase = GamePhase.PLACE
                )
            }
            g.board[row][col] == opponentColor ->
                _state.value = s.copy(selectedCell = Pair(row, col))
            g.board[row][col] == CellState.EMPTY -> {
                commitMove(null, null, row * 4 + col)
            }
        }
    }

    private fun handlePlaceTap(row: Int, col: Int) {
        val g = _state.value.game
        if (g.board[row][col] != CellState.EMPTY) return
        commitMove(pendingOptSrc, pendingOptDst, row * 4 + col)
    }

    private fun commitMove(optSrc: Int?, optDst: Int?, placePos: Int) {
        val s = _state.value
        pendingOptSrc = null; pendingOptDst = null
        _state.value = s.copy(selectedCell = null, localPhase = null)
        if (s.isHost) {
            server?.hostMove(optSrc, optDst, placePos)
        } else {
            client?.send(NetMsg(type = "move", optSrc = optSrc, optDst = optDst, placePos = placePos))
        }
    }

    fun sendChat(message: String) {
        val s = _state.value
        if (message.isBlank()) return
        if (s.isHost) {
            server?.broadcastChat(s.myNickname, message)
            addChat(s.myNickname, message)
        } else {
            client?.send(NetMsg(type = "chat", message = message))
        }
    }

    fun leaveRoom() {
        Log.d(TAG, "leaveRoom()")
        nsd?.stop(); server?.stop(); client?.stop()
        nsd = null; server = null; client = null
        releaseMulticastLock()
        _state.value = OnlineState()
        pendingOptSrc = null; pendingOptDst = null
    }

    private fun handleServerEvent(event: ServerEvent) {
        when (event) {
            is ServerEvent.RoomUpdate -> {
                val players = event.players.map { it.toModel() }
                val newStatus = if (players.any { it.role == OnlineRole.BLACK })
                    OnlineStatus.IN_GAME else OnlineStatus.WAITING_OPPONENT
                Log.d(TAG, "RoomUpdate: players=${players.map { "${it.nickname}(${it.role})" }} → status=$newStatus")
                _state.value = _state.value.copy(players = players, status = newStatus)
            }
            is ServerEvent.GameUpdate -> {
                applyGameMsg(event.msg)
                if (_state.value.status == OnlineStatus.RECONNECTING)
                    _state.value = _state.value.copy(status = OnlineStatus.IN_GAME, disconnectedNick = "", reconnectSecondsLeft = 0)
            }
            is ServerEvent.ChatReceived -> addChat(event.nick, event.message)
            is ServerEvent.PlayerDisconnected -> _state.value = _state.value.copy(
                status = OnlineStatus.RECONNECTING,
                disconnectedNick = event.nick,
                reconnectSecondsLeft = event.secondsLeft
            )
            is ServerEvent.PlayerReconnected -> {
                _state.value = _state.value.copy(
                    status = OnlineStatus.IN_GAME,
                    disconnectedNick = "",
                    reconnectSecondsLeft = 0
                )
                addChatSystem("${event.nick} reconnected")
            }
        }
    }

    private fun handleClientMsg(msg: NetMsg) {
        Log.d(TAG, "handleClientMsg: type=${msg.type}")
        when (msg.type) {
            "assigned" -> {
                val role = (msg.role ?: "spectator").toRole()
                Log.d(TAG, "assigned → myRole=$role")
                _state.value = _state.value.copy(myRole = role, status = OnlineStatus.WAITING_OPPONENT)
            }
            "room_state" -> {
                val players = msg.players?.map { it.toModel() } ?: emptyList()
                val newStatus = if (players.size >= 2) OnlineStatus.IN_GAME else OnlineStatus.WAITING_OPPONENT
                Log.d(TAG, "room_state → players=${players.map { "${it.nickname}(${it.role})" }} status=$newStatus")
                _state.value = _state.value.copy(
                    roomName = msg.roomName ?: _state.value.roomName,
                    players = players,
                    status = newStatus
                )
            }
            "game_state" -> {
                applyGameMsg(msg)
                if (_state.value.status == OnlineStatus.RECONNECTING)
                    _state.value = _state.value.copy(status = OnlineStatus.IN_GAME, disconnectedNick = "", reconnectSecondsLeft = 0)
            }
            "chat" -> addChat(msg.nickname ?: "?", msg.message ?: "")
            "player_disconnected" -> _state.value = _state.value.copy(
                status = OnlineStatus.RECONNECTING,
                disconnectedNick = msg.nickname ?: "",
                reconnectSecondsLeft = msg.secondsLeft ?: 0
            )
            "player_reconnected" -> {
                _state.value = _state.value.copy(
                    status = OnlineStatus.IN_GAME,
                    disconnectedNick = "",
                    reconnectSecondsLeft = 0
                )
                addChatSystem("${msg.nickname} reconnected")
            }
            "room_closed" -> _state.value = _state.value.copy(
                status = OnlineStatus.DISCONNECTED,
                errorMessage = "방이 종료되었습니다."
            )
        }
    }

    private fun applyGameMsg(msg: NetMsg) {
        val newGame = OnlineGameState(
            board = decodeBoard(msg.board ?: ""),
            currentPlayer = (msg.currentPlayer ?: "white").toPlayer(),
            phase = (msg.phase ?: "optional_move").toGamePhase(),
            whiteSideCount = msg.whiteSideCount ?: 8,
            blackSideCount = msg.blackSideCount ?: 8,
            winner = msg.winner?.toPlayer()
        )
        _state.value = _state.value.copy(
            game = newGame,
            status = OnlineStatus.IN_GAME,
            selectedCell = null,
            localPhase = null
        )
    }

    private fun applyOptMoveLocally(
        board: List<List<CellState>>, sr: Int, sc: Int, dr: Int, dc: Int
    ): List<List<CellState>> {
        val nb = board.map { it.toMutableList() }.toMutableList()
        nb[dr][dc] = nb[sr][sc]; nb[sr][sc] = CellState.EMPTY
        return nb.map { it.toList() }
    }

    private fun addChat(nick: String, message: String) {
        val msgs = _state.value.chatMessages + ChatMessage(nick, message)
        _state.value = _state.value.copy(chatMessages = msgs.takeLast(100))
    }

    private fun addChatSystem(message: String) {
        val msgs = _state.value.chatMessages + ChatMessage("", message, isSystem = true)
        _state.value = _state.value.copy(chatMessages = msgs.takeLast(100))
    }

    private fun isAdj(from: Pair<Int, Int>, toRow: Int, toCol: Int) =
        kotlin.math.abs(from.first - toRow) + kotlin.math.abs(from.second - toCol) == 1

    private fun acquireMulticastLock() {
        val wm = getApplication<Application>().getSystemService(WifiManager::class.java)
        multicastLock = wm?.createMulticastLock("orbito_nsd")?.also {
            it.setReferenceCounted(true)
            it.acquire()
        }
    }

    private fun releaseMulticastLock() {
        multicastLock?.let { if (it.isHeld) it.release() }
        multicastLock = null
    }

    override fun onCleared() {
        super.onCleared()
        server?.stop(); client?.stop(); nsd?.stop()
        releaseMulticastLock()
    }
}
