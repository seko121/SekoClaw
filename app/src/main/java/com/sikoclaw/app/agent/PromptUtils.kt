// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package com.sikoclaw.app.agent

import com.sikoclaw.app.utils.KVUtils
import com.sikoclaw.app.utils.XLog

/**
 * Prompt composition helpers (#45 persistent global prompt).
 *
 * Single responsibility: take a system prompt that some component is about to feed
 * to the LLM, and prepend the user's persistent global instructions when present.
 *
 * Empty / blank user global prompt = no-op, base prompt returned unchanged. This is
 * the disable signal — no separate boolean toggle, less state to misconfigure.
 */
object PromptUtils {
    private const val TAG = "PromptUtils"

    private const val PREFIX_HEADER = "User's persistent global instructions:"
    private const val SEPARATOR = "\n\n---\n\n"

    /**
     * Returns the base prompt, prepended with the user's global instructions if any.
     * Stable separator so downstream debug-report tooling can detect injection.
     */
    fun applyGlobalPrompt(basePrompt: String): String {
        val global = KVUtils.getGlobalPrompt()
        val userMemory = KVUtils.getUserMemoryPrompt()
        val userPrompt = KVUtils.getUserPrompt()
        val soul = KVUtils.getSoulPrompt()
        val custom = buildString {
            if (KVUtils.consumeFirstWakeContext()) append(AgentPromptDefaults.firstWake).append("\n\n")
            if (soul.isNotBlank()) append("SOUL / agent identity:\n$soul\n\n")
            if (userPrompt.isNotBlank()) append("USER PROMPT / standing instructions:\n$userPrompt\n\n")
            val structuredMemory = com.sikoclaw.app.agent.memory.KaiMemoryStore.promptBlock()
            if (userMemory.isNotBlank() && com.sikoclaw.app.agent.memory.KaiMemoryStore.isEnabled()) append("USER MEMORY (legacy stable facts and preferences):\n$userMemory\n\n")
            if (structuredMemory.isNotBlank()) append("AGENT MEMORIES:\n$structuredMemory\n\n")
            if (global.isNotBlank()) append("$PREFIX_HEADER\n$global\n\n")
            append(AgentPromptDefaults.memoryPolicy).append("\n\n")
            append(com.sikoclaw.app.agent.skill.UserSkillStore.promptBlock())
            val builtInSkills = com.sikoclaw.app.agent.skill.SkillRegistry.getAll()
                .filter { KVUtils.getBoolean("SKILL_ENABLED_${it.id}", true) }
            if (builtInSkills.isNotEmpty()) {
                append("BUILT-IN SKILLS (reusable procedures available now):\n")
                builtInSkills.forEach { skill -> append("- ${skill.id}: ${skill.name} — ${skill.description}\n") }
                append("Choose a skill when its description closely matches the request. Follow its procedure and parameters, but inspect the live screen and tool results. For a compound or unfamiliar task, reason with tools instead of forcing an unrelated skill.\n\n")
            }
            val enabledTools = com.sikoclaw.app.tool.ToolRegistry.getAllRegisteredTools()
                .filter { KVUtils.isToolEnabled(it.getName()) || it.getName() == "finish" }
            if (enabledTools.isNotEmpty()) {
                append("AVAILABLE TOOLS (call them directly when they match the request):\n")
                enabledTools.forEach { tool ->
                    val params = tool.getParameters().joinToString(", ") { parameter ->
                        "${parameter.name}${if (parameter.isRequired) " (required)" else ""}: ${parameter.type}"
                    }
                    val approval = tool.getName() in setOf(
                        "send_message", "draft_sms", "make_call", "linux_sandbox", "download_file",
                        "generate_image", "generate_speech", "create_pdf", "create_docx", "create_xlsx", "create_pptx",
                    )
                    append("- ${tool.getName()}: ${tool.getDescription().lineSequence().firstOrNull().orEmpty()}")
                    if (params.isNotBlank()) append(" Inputs: $params.")
                    append(if (approval) " Approval: confirm before sensitive, costly, install, send, or destructive actions." else " Approval: normal read-only use needs no extra confirmation.")
                    append(" Example: call ${tool.getName()} only when its stated capability directly matches the request.\n")
                }
                append("Use the smallest suitable tool or skill. Report tool progress honestly, inspect results, and never claim success before a successful result. Skills are reusable procedures; tools perform actions.\n\n")
            }
            append("BUILT-IN WORK ENVIRONMENT:\n")
            append("- For current or uncertain information, use web_search in the background and base the answer on the returned sources.\n")
            append("- For a requested direct HTTPS file, use download_file and report the real saved path.\n")
            append("- Ask for explicit user approval before destructive file operations, downloading and executing scripts, shared-storage access, opening ports, or long-running services.\n")
            append("- Inspect command exit status and output. Never say a search, download, package install, or command succeeded until its tool result confirms success.\n")
            append("- Do not run destructive commands or replace the Linux distribution without the user's explicit request.\n\n")
            append("SKILL AND TOOL USAGE RULES:\n")
            append("1. Understand the requested outcome before acting.\n")
            append("2. Check enabled skills first; use the closest matching reusable procedure only when it genuinely fits.\n")
            append("3. Use tools to perform and verify actions. A skill is guidance; a tool is an executable capability.\n")
            append("4. Show concise live progress while thinking, searching, running commands, or using tools.\n")
            append("5. If a successful multi-step workflow will likely be reused, save a generalized version with create_skill. Exclude secrets and one-time values.\n")
            append("6. If no skill fits, solve the task with available tools and optionally create a skill only after the workflow succeeds.\n\n")
            append("VISIBLE RESPONSE FORMAT:\nKeep private reasoning separate from the final answer. Always provide a user-visible final response. When several short messages are clearer than one long message, separate them with the exact marker <message-break>. Do not use that marker inside code.\n\n")
            if (isNotBlank()) append("Memory writes must follow the MEMORY POLICY above and should be acknowledged briefly after the tool succeeds.\n\n")
        }
        if (custom.isBlank()) {
            XLog.d(TAG, "applyGlobalPrompt: no global prompt set, returning base (${basePrompt.length} chars)")
            return basePrompt
        }
        XLog.i(
            TAG,
            "applyGlobalPrompt: injecting global prompt (${global.length} chars) into base prompt (${basePrompt.length} chars)"
        )
        return "$custom$SEPARATOR$basePrompt"
    }
}
