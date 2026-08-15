package com.sikoclaw.app.agent.memory

import com.sikoclaw.app.tool.BaseTool
import com.sikoclaw.app.tool.ToolParameter
import com.sikoclaw.app.tool.ToolResult
import com.sikoclaw.app.utils.KVUtils

class AgentMemoryTool : BaseTool() {
    override fun getName() = "update_agent_memory"
    override fun getDisplayName() = "Update Agent Memory"
    override fun getDescriptionEN() = "Read or update durable user memory and the agent soul. Store only stable useful facts; never secrets or temporary chat details."
    override fun getDescriptionCN() = getDescriptionEN()
    override fun getParameters() = listOf(
        ToolParameter("action", "string", "read, append, or replace", true),
        ToolParameter("section", "string", "user or soul", true),
        ToolParameter("key", "string", "Stable identifier for a user memory", false),
        ToolParameter("content", "string", "Content for append/replace", false)
    )
    override fun execute(params: Map<String, Any>): ToolResult {
        val action = requireString(params, "action").lowercase()
        val section = requireString(params, "section").lowercase()
        if (section != "soul" && !KaiMemoryStore.isEnabled()) return ToolResult.error("Memories are disabled in Agent Settings")
        val current = if (section == "soul") KVUtils.getSoulPrompt() else KaiMemoryStore.promptBlock()
        if (action == "read") return ToolResult.success(current.ifBlank { "No memory saved." })
        val content = optionalString(params, "content", "").trim()
        if (content.isBlank()) return ToolResult.error("content is required")
        val next = if (action == "append" && current.isNotBlank()) "$current\n$content" else content
        if (section == "soul") KVUtils.setSoulPrompt(next) else {
            val key = params["key"]?.toString()?.trim().orEmpty().ifBlank { "memory-${System.currentTimeMillis()}" }
            KaiMemoryStore.store(key, content)
        }
        return ToolResult.success("$section memory updated")
    }
}
