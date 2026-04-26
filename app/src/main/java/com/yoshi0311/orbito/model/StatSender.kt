package com.yoshi0311.orbito.model

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object StatSender {

    private const val SERVER_URL = "https://orbito2.onrender.com/api/stats"

    private const val PREFS_NAME = "orbito_prefs"
    private const val KEY_LAST_SEND = "stat_last_send"
    private const val KEY_LAST_SEND_TS = "stat_last_send_ts"
    private const val INTERVAL_MS = 24 * 60 * 60 * 1000L
    private const val RETRY_DELAY_MS = 60 * 1000L

    suspend fun sendIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 24시간 미만이면 전송 안 함
        if (System.currentTimeMillis() - prefs.getLong(KEY_LAST_SEND_TS, 0L) < INTERVAL_MS) return

        val edit   = prefs.getInt("stat_min_bot_edit",   0)
        val batch  = prefs.getInt("stat_min_batch",      0)
        val game   = prefs.getInt("stat_min_game",       0)
        val online = prefs.getInt("stat_min_online",     0)
        val replay = prefs.getInt("stat_min_replay",     0)
        val wins   = prefs.getInt("stat_online_wins",    0)
        val losses = prefs.getInt("stat_online_losses",  0)
        val snapshot = "$edit/$batch/$game/$online/$replay/$wins/$losses"

        // 값이 변하지 않았으면 전송 안 함
        if (snapshot == prefs.getString(KEY_LAST_SEND, null)) return

        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val nickname = prefs.getString("stat_nickname", null)
        val payload = JSONObject().apply {
            put("deviceId", deviceId)
            put("account",  android.os.Build.MODEL)
            put("nickname", nickname)
            put("edit",     edit)
            put("batch",    batch)
            put("game",     game)
            put("online",   online)
            put("replay",   replay)
            put("wins",     wins)
            put("losses",   losses)
        }.toString()

        // 최대 3회 전송 (1, 2번째는 성공 확인, 3번째는 확인 없이 종료)
        for (attempt in 0..2) {
            if (attempt > 0) delay(RETRY_DELAY_MS)
            val isLast = attempt == 2
            val sent = try {
                withContext(Dispatchers.IO) { post(payload) }
            } catch (_: Exception) { false }

            if (sent || isLast) {
                prefs.edit()
                    .putString(KEY_LAST_SEND, snapshot)
                    .putLong(KEY_LAST_SEND_TS, System.currentTimeMillis())
                    .apply()
                break
            }
        }
    }

    private fun post(payload: String): Boolean {
        val conn = URL(SERVER_URL).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.outputStream.use { it.write(payload.toByteArray()) }
            conn.responseCode in 200..299
        } finally {
            conn.disconnect()
        }
    }
}
