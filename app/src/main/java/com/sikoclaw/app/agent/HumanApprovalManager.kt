package com.sikoclaw.app.agent

import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ApprovalRequest(
    val id: String,
    val toolName: String,
    val action: String,
)

/** One process-wide approval queue shared by chat, tools and the floating UI. */
object HumanApprovalManager {
    private val _pending = MutableStateFlow<ApprovalRequest?>(null)
    val pending = _pending.asStateFlow()
    private val waiters = LinkedHashMap<String, CompletableFuture<Boolean>>()

    fun requiresApproval(toolName: String, params: Map<String, Any>): Boolean {
        val command = params["command"]?.toString().orEmpty().lowercase()
        val action = params["action"]?.toString().orEmpty().lowercase()
        return toolName in setOf("send_message", "send_file", "make_call", "install_mcp", "add_api_provider") ||
            (toolName == "agent_workspace" && action !in setOf("status", "read_profile")) ||
            (toolName.contains("file") && action in setOf("delete", "remove", "overwrite")) ||
            (toolName.contains("cron") && action in setOf("create", "update", "delete")) ||
            (toolName.contains("skill") && action in setOf("create", "update", "delete")) ||
            (toolName in setOf("linux_shell", "internal_terminal") && dangerousCommand(command))
    }

    fun requestBlocking(toolName: String, params: Map<String, Any>): Boolean {
        val id = UUID.randomUUID().toString()
        val future = CompletableFuture<Boolean>()
        synchronized(waiters) {
            if (_pending.value != null) return false
            waiters[id] = future
            _pending.value = ApprovalRequest(id, toolName, describe(toolName, params))
        }
        return try {
            future.get(2, TimeUnit.MINUTES)
        } catch (_: Exception) {
            false
        } finally {
            synchronized(waiters) {
                waiters.remove(id)
                if (_pending.value?.id == id) _pending.value = null
            }
        }
    }

    fun resolve(id: String, allowed: Boolean) {
        synchronized(waiters) {
            waiters[id]?.complete(allowed)
            if (_pending.value?.id == id) _pending.value = null
        }
    }

    private fun describe(toolName: String, params: Map<String, Any>): String {
        val target = params["path"] ?: params["command"] ?: params["recipient"] ?: params["name"] ?: params["action"]
        return buildString {
            append(toolName.replace('_', ' ').replaceFirstChar { it.uppercase() })
            target?.toString()?.takeIf { it.isNotBlank() }?.let { append("\n"); append(it.take(240)) }
        }
    }

    private fun dangerousCommand(command: String): Boolean = listOf(
        "rm ", "rm-", "apk add", "apk del", "apt install", "apt remove", "chmod 777",
        "curl ", "wget ", "reboot", "shutdown", "mkfs", "dd if=", "> /etc/",
    ).any(command::contains)
}
