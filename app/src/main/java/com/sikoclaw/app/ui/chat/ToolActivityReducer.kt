package com.sikoclaw.app.ui.chat

/** Keeps only the latest tool activity card in the chat flow. */
object ToolActivityReducer {
    fun start(messages: MutableList<ChatMessage>, step: ToolStep) {
        messages.removeAll { it.role == ChatMessage.Role.TOOL_GROUP }
        messages.add(ChatMessage(ChatMessage.Role.TOOL_GROUP, "", toolSteps = listOf(step.copy(status = ToolActivityStatus.RUNNING))))
    }

    fun finish(messages: MutableList<ChatMessage>, callId: String, success: Boolean, details: String): Boolean {
        val index = messages.indexOfLast { it.role == ChatMessage.Role.TOOL_GROUP }
        if (index < 0) return false
        val message = messages[index]
        val step = message.toolSteps?.singleOrNull() ?: return false
        if (step.callId != callId) return false
        messages[index] = message.copy(toolSteps = listOf(step.copy(
            success = success,
            status = if (success) ToolActivityStatus.COMPLETED else ToolActivityStatus.FAILED,
            details = details.ifBlank { step.details },
        )))
        return true
    }
}
