// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package com.sikoclaw.app

/**
 * Typed event emitted by TaskOrchestrator during task execution.
 * Replaces the previous string-based callback protocol that required
 * fragile prefix parsing ("Task completed:", "Task failed", etc.).
 *
 * ComposeChatActivity (or any future UI) pattern-matches on these
 * to update the UI — no string parsing, no ambiguity.
 */
sealed class TaskEvent {

    /** LLM responded with text (chat answer or task summary). */
    data class Response(val text: String, val modelName: String? = null) : TaskEvent()

    /** A tool is being executed (e.g. "Send Message", "Open App"). */
    data class ToolAction(
        val toolName: String,
        val callId: String = toolName,
        val parameters: String = "",
        val rawToolName: String = toolName,
    ) : TaskEvent()

    /** Tool execution result. */
    data class ToolResult(
        val toolName: String,
        val success: Boolean,
        val detail: String,
        val callId: String = toolName,
    ) : TaskEvent()

    /** Agent loop started a new round. */
    data class LoopStart(val round: Int) : TaskEvent()

    /** Skill/workflow step progress. */
    data class Progress(val step: Int, val description: String) : TaskEvent()

    /** Token usage update. */
    data class TokenUpdate(
        val step: Int,
        val formattedTokens: String,
        val formattedCost: String,
        val tokenState: com.sikoclaw.app.agent.TokenMonitor.State
    ) : TaskEvent()

    /** Task completed successfully with an answer/summary. */
    data class Completed(val answer: String, val modelName: String? = null) : TaskEvent()

    /** Task failed with an error. */
    data class Failed(val error: String) : TaskEvent()

    /** Task was cancelled by user. */
    object Cancelled : TaskEvent()

    /** Task blocked by system dialog. */
    object Blocked : TaskEvent()

    /** A visible assistant-content delta. Never contains private reasoning. */
    data class ContentDelta(val content: String) : TaskEvent()

    /** Optional reasoning delta, rendered only when the user enabled thought visibility. */
    data class ReasoningDelta(val content: String) : TaskEvent()
}
