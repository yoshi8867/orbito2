package com.yoshi0311.orbito.model

enum class OnlineRole { WHITE, BLACK, SPECTATOR }

enum class OnlineStatus {
    IDLE, LOBBY, CONNECTING, WAITING_OPPONENT, IN_GAME, RECONNECTING, DISCONNECTED
}

data class OnlinePlayer(
    val nickname: String,
    val role: OnlineRole,
    val connected: Boolean = true
)

data class RoomInfo(
    val name: String,
    val host: String,
    val port: Int,
    val players: List<OnlinePlayer> = emptyList()
)

data class ChatMessage(
    val nickname: String,
    val message: String,
    val isSystem: Boolean = false
)

data class OnlineGameState(
    val board: List<List<CellState>> = List(4) { List(4) { CellState.EMPTY } },
    val currentPlayer: Player = Player.WHITE,
    val phase: GamePhase = GamePhase.OPTIONAL_MOVE,
    val whiteSideCount: Int = 8,
    val blackSideCount: Int = 8,
    val winner: Player? = null
)

data class OnlineState(
    val status: OnlineStatus = OnlineStatus.IDLE,
    val myNickname: String = "",
    val myRole: OnlineRole = OnlineRole.SPECTATOR,
    val isHost: Boolean = false,
    val roomName: String = "",
    val players: List<OnlinePlayer> = emptyList(),
    val discoveredRooms: List<RoomInfo> = emptyList(),
    val game: OnlineGameState = OnlineGameState(),
    val chatMessages: List<ChatMessage> = emptyList(),
    val disconnectedNick: String = "",
    val reconnectSecondsLeft: Int = 0,
    val errorMessage: String = "",
    val selectedCell: Pair<Int, Int>? = null,
    val localPhase: GamePhase? = null  // overrides game.phase during PLACE input
) {
    val effectivePhase: GamePhase get() = localPhase ?: game.phase

    fun toGameState(): GameState = GameState(
        board = game.board,
        currentPlayer = game.currentPlayer,
        phase = effectivePhase,
        whiteSideCount = game.whiteSideCount,
        blackSideCount = game.blackSideCount,
        winner = game.winner,
        selectedCell = selectedCell,
        isRotating = false,
        boardBeforeRotation = null,
        isBotThinking = false,
        timeLimitSeconds = null
    )
}
