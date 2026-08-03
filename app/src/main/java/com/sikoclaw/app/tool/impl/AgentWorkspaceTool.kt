package com.sikoclaw.app.tool.impl

import com.google.gson.Gson
import com.sikoclaw.app.agent.llm.ApiProviderConfig
import com.sikoclaw.app.agent.llm.MultiProviderStore
import com.sikoclaw.app.mcp.McpManager
import com.sikoclaw.app.mcp.McpServer
import com.sikoclaw.app.tool.BaseTool
import com.sikoclaw.app.tool.ToolParameter
import com.sikoclaw.app.tool.ToolResult
import com.sikoclaw.app.utils.KVUtils
import java.util.UUID

/** Controlled access to owner-editable OctoBot state; never exposes secret values. */
class AgentWorkspaceTool : BaseTool() {
    override fun getName() = "agent_workspace"
    override fun getDisplayName() = "OctoBot Workspace"
    override fun getDescriptionEN() = "Read or update OctoBot's own prompts, API providers and MCP server configuration through a protected API. Secret values are write-only. Skills, schedules and memories use their dedicated tools."
    override fun getDescriptionCN() = getDescriptionEN()
    override fun getParameters() = listOf(
        ToolParameter("action", "string", "status, read_profile, update_soul, update_user_profile, add_provider, add_mcp", true),
        ToolParameter("content", "string", "Prompt/profile content", false),
        ToolParameter("name", "string", "Provider or MCP name", false),
        ToolParameter("url", "string", "Provider Base URL or MCP endpoint", false),
        ToolParameter("api_key", "string", "Write-only provider API key", false),
        ToolParameter("model", "string", "Provider model id", false),
        ToolParameter("token", "string", "Write-only MCP bearer token", false),
    )

    override fun execute(params: Map<String, Any>): ToolResult = runCatching {
        when (val action = requireString(params, "action").lowercase()) {
            "status" -> ToolResult.success(Gson().toJson(mapOf(
                "providers" to MultiProviderStore.state().providers.map { mapOf("id" to it.id, "name" to it.name, "baseUrl" to it.baseUrl, "enabled" to it.enabled) },
                "models" to MultiProviderStore.state().models.map { mapOf("name" to it.displayName, "modelId" to it.apiModelName, "active" to it.isPrimary) },
                "mcpServers" to McpManager.all().map { mapOf("id" to it.id, "name" to it.name, "url" to it.url, "enabled" to it.enabled) },
            )))
            "read_profile" -> ToolResult.success("Soul:\n${KVUtils.getSoulPrompt()}\n\nUser Profile:\n${KVUtils.getUserPrompt()}")
            "update_soul" -> { KVUtils.setSoulPrompt(requireString(params, "content")); ToolResult.success("Soul updated") }
            "update_user_profile" -> { KVUtils.setUserPrompt(requireString(params, "content")); ToolResult.success("User Profile updated") }
            "add_provider" -> {
                val provider = ApiProviderConfig(
                    name = requireString(params, "name"),
                    baseUrl = requireString(params, "url"),
                    isDefault = MultiProviderStore.state().routing.isEmpty(),
                )
                val result = MultiProviderStore.saveProvider(provider, optionalString(params, "api_key", ""))
                if (!result.success) ToolResult.error(result.message) else {
                    optionalString(params, "model", "").takeIf { it.isNotBlank() }?.let { modelId ->
                        MultiProviderStore.saveModel(com.sikoclaw.app.agent.llm.ApiModelConfig(providerId = provider.id, displayName = modelId, apiModelName = modelId, isFavorite = true, isPrimary = true))
                    }
                    ToolResult.success("Provider saved")
                }
            }
            "add_mcp" -> {
                val server = McpServer(UUID.randomUUID().toString(), requireString(params, "name"), requireString(params, "url"), optionalString(params, "token", ""), enabled = false)
                McpManager.upsert(server)
                ToolResult.success("MCP server saved disabled. The owner can review and enable it in Settings.")
            }
            else -> ToolResult.error("Unsupported workspace action: $action")
        }
    }.getOrElse { ToolResult.error(it.message ?: "Workspace operation failed") }
}
