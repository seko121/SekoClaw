/*
 * Agent-facing adapter for the Linux Sandbox ported from Kai.
 * Kai project copyright Simon Schubert and contributors, Apache-2.0.
 */
package com.sikoclaw.app.tool.impl

import com.sikoclaw.app.linux.LinuxSandboxFeature
import com.sikoclaw.app.linux.SandboxSessions
import com.sikoclaw.app.tool.BaseTool
import com.sikoclaw.app.tool.ToolParameter
import com.sikoclaw.app.tool.ToolResult
import com.sikoclaw.app.utils.KVUtils
import kotlinx.coroutines.runBlocking

class LinuxSandboxTool : BaseTool() {
    override fun getName() = "linux_shell"
    override fun getDisplayName() = "Linux Sandbox"
    override fun getDescriptionEN() = "Run a shell command inside OctoBot's private Alpine Linux sandbox. The shell session is persistent, so working directory and environment variables survive between calls."
    override fun getDescriptionCN() = getDescriptionEN()
    override fun getParameters() = listOf(
        ToolParameter("command", "string", "Shell command to execute in Alpine Linux", true),
        ToolParameter("session_id", "string", "Optional persistent session id", false),
    )

    override fun execute(params: Map<String, Any>): ToolResult {
        if (!KVUtils.getBoolean("LINUX_SANDBOX_ENABLED", true)) return ToolResult.error("Linux Sandbox is disabled in Settings")
        val controller = LinuxSandboxFeature.controller
        if (!controller.status.value.ready) return ToolResult.error("Linux Sandbox is not installed or not ready. Open Settings > Linux Sandbox first.")
        val command = requireString(params, "command")
        val session = optionalString(params, "session_id", "agent-${SandboxSessions.DEFAULT}")
        val output = runBlocking { controller.executeCommand(command, session) }
        return ToolResult.success(output.ifBlank { "Command completed with no output" })
    }
}
