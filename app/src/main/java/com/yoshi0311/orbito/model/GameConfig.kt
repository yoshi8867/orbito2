package com.yoshi0311.orbito.model

enum class PlayerType { HUMAN, BOT }

enum class BotVsBotMode { STEP, BATCH }

data class BotConfig(
    val id: String,
    val name: String,
    val isUserBot: Boolean = false,
    val filePath: String? = null
)

val AVAILABLE_BOTS = listOf(
    BotConfig(id = "random_bot", name = "RANDOM"),
    BotConfig(id = "smart_bot",  name = "SMART")
)

data class GameConfig(
    val whiteType: PlayerType = PlayerType.HUMAN,
    val blackType: PlayerType = PlayerType.HUMAN,
    val whiteBot: BotConfig? = null,
    val blackBot: BotConfig? = null,
    val botVsBotMode: BotVsBotMode = BotVsBotMode.STEP,
    val batchCount: Int = 10
) {
    val isBotVsBot: Boolean get() = whiteType == PlayerType.BOT && blackType == PlayerType.BOT
    fun typeFor(player: Player): PlayerType = if (player == Player.WHITE) whiteType else blackType
    fun botFor(player: Player): BotConfig? = if (player == Player.WHITE) whiteBot else blackBot
}
