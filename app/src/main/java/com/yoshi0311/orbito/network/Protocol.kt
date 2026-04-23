package com.yoshi0311.orbito.network

import com.yoshi0311.orbito.model.CellState
import com.yoshi0311.orbito.model.GamePhase
import com.yoshi0311.orbito.model.OnlinePlayer
import com.yoshi0311.orbito.model.OnlineRole
import com.yoshi0311.orbito.model.Player
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val NetJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable
data class NetMsg(
    val type: String,
    val nickname: String? = null,
    val role: String? = null,
    val optSrc: Int? = null,
    val optDst: Int? = null,
    val placePos: Int? = null,
    val message: String? = null,
    val roomName: String? = null,
    val players: List<NetPlayer>? = null,
    val board: String? = null,
    val currentPlayer: String? = null,
    val phase: String? = null,
    val whiteSideCount: Int? = null,
    val blackSideCount: Int? = null,
    val winner: String? = null,
    val secondsLeft: Int? = null
)

@Serializable
data class NetPlayer(
    val nickname: String,
    val role: String,
    val connected: Boolean = true
)

fun NetMsg.encode(): String = NetJson.encodeToString(this)
fun String.decodeMsg(): NetMsg? = try { NetJson.decodeFromString<NetMsg>(this.trim()) } catch (_: Exception) { null }

fun encodeBoard(board: List<List<CellState>>): String =
    board.flatten().joinToString("") {
        when (it) { CellState.WHITE -> "w"; CellState.BLACK -> "b"; else -> "." }
    }

fun decodeBoard(s: String): List<List<CellState>> {
    val cells = s.map { when (it) { 'w' -> CellState.WHITE; 'b' -> CellState.BLACK; else -> CellState.EMPTY } }
    return if (cells.size == 16) List(4) { r -> List(4) { c -> cells[r * 4 + c] } }
    else List(4) { List(4) { CellState.EMPTY } }
}

fun Player.toStr() = if (this == Player.WHITE) "white" else "black"
fun String.toPlayer() = if (this == "white") Player.WHITE else Player.BLACK
fun GamePhase.toStr() = name.lowercase()
fun String.toGamePhase() =
    GamePhase.entries.find { it.name.lowercase() == this.lowercase() } ?: GamePhase.OPTIONAL_MOVE

fun OnlineRole.toStr() = name.lowercase()
fun String.toRole() =
    OnlineRole.entries.find { it.name.lowercase() == this.lowercase() } ?: OnlineRole.SPECTATOR

fun NetPlayer.toModel() = OnlinePlayer(nickname, role.toRole(), connected)
