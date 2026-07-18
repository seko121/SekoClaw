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
            if (soul.isNotBlank()) append("SOUL / agent identity:\n$soul\n\n")
            if (userPrompt.isNotBlank()) append("USER PROMPT / standing instructions:\n$userPrompt\n\n")
            if (userMemory.isNotBlank()) append("USER MEMORY (stable facts and preferences):\n$userMemory\n\n")
            if (global.isNotBlank()) append("$PREFIX_HEADER\n$global\n\n")
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
                    append("- ${tool.getName()}: ${tool.getDescription().lineSequence().firstOrNull().orEmpty()}\n")
                }
                append("Use the smallest suitable tool or skill. Report tool progress honestly, inspect results, and never claim success before a successful result. Skills are reusable procedures; tools perform actions.\n\n")
            }
            append("BUILT-IN WORK ENVIRONMENT:\n")
            append("- For current or uncertain information, use web_search in the background and base the answer on the returned sources.\n")
            append("- For a requested direct HTTPS file, use download_file and report the real saved path.\n")
            append("- You have an internal terminal. Use environment=android for lightweight device-side shell work.\n")
            append("- You also have bundled isolated Alpine Linux on supported ARM64 phones. Use environment=linux for Linux commands and development utilities.\n")
            append("- Alpine uses apk. If Git is needed and missing, run: apk update && apk add git ca-certificates. Install other packages only when the task needs them.\n")
            append("- Inspect command exit status and output. Never say a search, download, package install, or command succeeded until its tool result confirms success.\n")
            append("- Do not run destructive commands or replace the Linux distribution without the user's explicit request.\n\n")
            append("SKILL AND TOOL USAGE RULES:\n")
            append("1. Understand the requested outcome before acting.\n")
            append("2. Check enabled skills first; use the closest matching reusable procedure only when it genuinely fits.\n")
            append("3. Use tools to perform and verify actions. A skill is guidance; a tool is an executable capability.\n")
            append("4. Show concise live progress while thinking, searching, running commands, or using tools.\n")
            append("5. If a successful multi-step workflow will likely be reused, save a generalized version with create_skill. Exclude secrets and one-time values.\n")
            append("6. If no skill fits, solve the task with available tools and optionally create a skill only after the workflow succeeds.\n\n")
            if (isNotBlank()) append("When the user reveals a durable preference or important stable fact, use update_agent_memory. Never store passwords, API keys, payment data, or one-time details.\n\n")
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
