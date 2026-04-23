package com.yoshi0311.orbito.model

import java.io.File

class BotRepository(filesDir: File) {
    private val botsDir = File(filesDir, "bots").also { it.mkdirs() }

    fun userBots(): List<BotConfig> =
        (botsDir.listFiles()?.filter { it.extension == "py" } ?: emptyList())
            .sortedBy { it.name }
            .map { BotConfig(id = it.nameWithoutExtension, name = it.nameWithoutExtension, isUserBot = true, filePath = it.absolutePath) }

    fun exists(name: String): Boolean = File(botsDir, "$name.py").exists()

    fun loadCode(name: String): String? =
        File(botsDir, "$name.py").takeIf { it.exists() }?.readText()

    fun writeCode(name: String, code: String) =
        File(botsDir, "$name.py").writeText(code)

    fun renameFile(oldName: String, newName: String) {
        val src = File(botsDir, "$oldName.py")
        val dst = File(botsDir, "$newName.py")
        if (src.exists()) src.renameTo(dst)
    }

    fun deleteBot(name: String) {
        File(botsDir, "$name.py").delete()
    }

    fun filePath(name: String): String = File(botsDir, "$name.py").absolutePath

    companion object {
        private val INVALID_CHARS = Regex("""[<>:"/\\|?*\n\r\t]""")

        fun isValidName(name: String): Boolean =
            name.isNotBlank() && !INVALID_CHARS.containsMatchIn(name)
    }
}
