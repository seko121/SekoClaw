package com.sikoclaw.app.heartbeat

import android.content.Context
import com.sikoclaw.app.cron.CronJob
import com.sikoclaw.app.cron.CronManager
import com.sikoclaw.app.utils.KVUtils
import java.util.Calendar

data class HeartbeatConfig(
    val enabled: Boolean = false,
    val intervalMinutes: Int = 30,
    val activeFrom: Int = 8,
    val activeUntil: Int = 23,
    val prompt: String = "Review my pending tasks, reminders, and recent context. Only notify me when something needs attention. Reply HEARTBEAT_OK when no action is needed.",
)

object HeartbeatManager {
    private const val JOB_ID = "siko-heartbeat"
    private const val KEY_ENABLED = "HEARTBEAT_ENABLED"
    private const val KEY_INTERVAL = "HEARTBEAT_INTERVAL"
    private const val KEY_FROM = "HEARTBEAT_FROM"
    private const val KEY_UNTIL = "HEARTBEAT_UNTIL"
    private const val KEY_PROMPT = "HEARTBEAT_PROMPT"
    private const val KEY_LAST_RUN = "HEARTBEAT_LAST_RUN"
    private const val KEY_LAST_RESULT = "HEARTBEAT_LAST_RESULT"

    fun config() = sanitize(HeartbeatConfig(
        enabled = KVUtils.getBoolean(KEY_ENABLED, false),
        intervalMinutes = KVUtils.getInt(KEY_INTERVAL, 30),
        activeFrom = KVUtils.getInt(KEY_FROM, 8),
        activeUntil = KVUtils.getInt(KEY_UNTIL, 23),
        prompt = KVUtils.getString(KEY_PROMPT, HeartbeatConfig().prompt),
    ))

    fun sanitize(value: HeartbeatConfig): HeartbeatConfig = value.copy(
        intervalMinutes = value.intervalMinutes.coerceIn(15, 59),
        activeFrom = value.activeFrom.coerceIn(0, 23),
        activeUntil = value.activeUntil.coerceIn(0, 23),
        prompt = value.prompt.trim().ifBlank { HeartbeatConfig().prompt },
    )

    fun save(context: Context, value: HeartbeatConfig): Result<Unit> = runCatching {
        val safe = sanitize(value)
        KVUtils.putBoolean(KEY_ENABLED, safe.enabled)
        KVUtils.putInt(KEY_INTERVAL, safe.intervalMinutes)
        KVUtils.putInt(KEY_FROM, safe.activeFrom)
        KVUtils.putInt(KEY_UNTIL, safe.activeUntil)
        KVUtils.putString(KEY_PROMPT, safe.prompt)
        if (!safe.enabled) {
            CronManager.delete(context, JOB_ID)
            return@runCatching
        }
        val guardedPrompt = """
            HEARTBEAT CHECK. Active hours are ${safe.activeFrom}:00-${safe.activeUntil}:00 local time. If outside those hours, reply HEARTBEAT_OK and do nothing. ${safe.prompt}
            If nothing needs the user's attention, reply with exactly HEARTBEAT_OK.
        """.trimIndent()
        CronManager.upsert(context, CronJob(JOB_ID, "Agent heartbeat", "*/${safe.intervalMinutes} * * * *", guardedPrompt, true))
    }

    fun isHeartbeatJob(id: String): Boolean = id == JOB_ID

    fun shouldRunNow(now: Calendar = Calendar.getInstance()): Boolean {
        val value = config()
        if (!value.enabled) return false
        val hour = now.get(Calendar.HOUR_OF_DAY)
        return if (value.activeFrom <= value.activeUntil) {
            hour in value.activeFrom until value.activeUntil
        } else {
            hour >= value.activeFrom || hour < value.activeUntil
        }
    }

    fun markRun(result: String) {
        KVUtils.putString(KEY_LAST_RUN, System.currentTimeMillis().toString())
        KVUtils.putString(KEY_LAST_RESULT, result.take(500))
    }

    fun lastRunAt(): Long = KVUtils.getString(KEY_LAST_RUN, "0").toLongOrNull() ?: 0L
    fun lastResult(): String = KVUtils.getString(KEY_LAST_RESULT, "")
}
