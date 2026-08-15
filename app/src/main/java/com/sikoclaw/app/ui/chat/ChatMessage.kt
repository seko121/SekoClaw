// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package com.sikoclaw.app.ui.chat

data class ChatMessage(
    val role: Role,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val toolSteps: List<ToolStep>? = null,
    val modelName: String? = null,
    val pluginIds: List<String> = emptyList(),
    val attachments: List<ChatAttachment> = emptyList(),
    val isStreaming: Boolean = false,
) {
    enum class Role { USER, ASSISTANT, REASONING, SYSTEM, TOOL_GROUP }
}

data class ToolStep(
    val toolName: String,
    val summary: String,
    val success: Boolean = false,
    val callId: String = toolName,
    val details: String = "",
    val status: ToolActivityStatus = if (success) ToolActivityStatus.COMPLETED else ToolActivityStatus.RUNNING,
    val rawToolName: String = toolName,
)

enum class ToolActivityStatus { RUNNING, COMPLETED, FAILED }
