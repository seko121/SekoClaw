package com.sikoclaw.app.agent

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sikoclaw.app.utils.KVUtils
import java.util.UUID

data class QueuedAgentTask(val id: String = UUID.randomUUID().toString(), val prompt: String, val createdAt: Long = System.currentTimeMillis())

/** Persistent FIFO for tasks requested while another task is running. */
object AgentTaskQueue {
    private const val KEY = "AGENT_PENDING_TASKS_V1"
    private val gson = Gson()
    @Synchronized fun all(): List<QueuedAgentTask> = runCatching {
        gson.fromJson<List<QueuedAgentTask>>(KVUtils.getString(KEY, "[]"), object : TypeToken<List<QueuedAgentTask>>() {}.type)
    }.getOrDefault(emptyList())
    @Synchronized fun enqueue(prompt: String): QueuedAgentTask {
        val task = QueuedAgentTask(prompt = prompt.trim())
        save(all() + task); return task
    }
    @Synchronized fun poll(): QueuedAgentTask? {
        val items = all(); val first = items.firstOrNull() ?: return null
        save(items.drop(1)); return first
    }
    @Synchronized fun remove(id: String) = save(all().filterNot { it.id == id })
    @Synchronized fun reorder(ids: List<String>) {
        val byId = all().associateBy { it.id }
        save(ids.mapNotNull(byId::get) + byId.values.filter { it.id !in ids })
    }
    private fun save(items: List<QueuedAgentTask>) { KVUtils.putString(KEY, gson.toJson(items)) }
}
