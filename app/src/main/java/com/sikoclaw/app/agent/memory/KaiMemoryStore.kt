/* Adapted from Kai data/MemoryStore.kt, Apache-2.0. */
package com.sikoclaw.app.agent.memory

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sikoclaw.app.utils.KVUtils

enum class MemoryCategory { GENERAL, LEARNING, ERROR, PREFERENCE }
data class MemoryEntry(val key: String, val content: String, val createdAt: Long, val updatedAt: Long, val category: MemoryCategory = MemoryCategory.GENERAL, val hitCount: Int = 1, val source: String? = null)

object KaiMemoryStore {
    private const val KEY = "KAI_AGENT_MEMORIES_JSON"
    private const val ENABLED = "KAI_AGENT_MEMORIES_ENABLED"
    private val gson = Gson()
    @Synchronized fun all(): MutableList<MemoryEntry> = runCatching {
        gson.fromJson<MutableList<MemoryEntry>>(KVUtils.getString(KEY, "[]"), object : TypeToken<MutableList<MemoryEntry>>() {}.type)
    }.getOrNull() ?: mutableListOf()
    fun isEnabled() = KVUtils.getBoolean(ENABLED, true)
    fun setEnabled(value: Boolean) = KVUtils.putBoolean(ENABLED, value)
    @Synchronized fun store(key: String, content: String, category: MemoryCategory = MemoryCategory.GENERAL, source: String? = null): MemoryEntry {
        val rows = all(); val now = System.currentTimeMillis(); val index = rows.indexOfFirst { it.key == key }
        val entry = if (index >= 0) rows[index].copy(content = content, updatedAt = now, category = category, source = source ?: rows[index].source) else MemoryEntry(key, content, now, now, category, source = source)
        if (index >= 0) rows[index] = entry else rows.add(entry)
        KVUtils.putString(KEY, gson.toJson(rows)); return entry
    }
    @Synchronized fun delete(key: String): Boolean { val rows = all(); val removed = rows.removeAll { it.key == key }; if (removed) KVUtils.putString(KEY, gson.toJson(rows)); return removed }
    @Synchronized fun clear() = KVUtils.putString(KEY, "[]")
    fun promptBlock(): String = if (!isEnabled()) "" else all().sortedByDescending { it.updatedAt }.joinToString("\n") { "- ${it.key}: ${it.content}" }
}
