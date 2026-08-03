package com.sikoclaw.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolActivityReducerTest {
    @Test fun `new tool replaces previous card`() {
        val messages = mutableListOf(
            ChatMessage(ChatMessage.Role.USER, "do it"),
            ChatMessage(ChatMessage.Role.TOOL_GROUP, "", toolSteps = listOf(ToolStep("Files", "Reading", true))),
        )
        ToolActivityReducer.start(messages, ToolStep("Terminal", "Running command", callId = "2"))
        assertEquals(1, messages.count { it.role == ChatMessage.Role.TOOL_GROUP })
        assertEquals("Terminal", messages.last().toolSteps?.single()?.toolName)
    }

    @Test fun `finish updates current call`() {
        val messages = mutableListOf<ChatMessage>()
        ToolActivityReducer.start(messages, ToolStep("Web Search", "Searching", callId = "search-1"))
        assertTrue(ToolActivityReducer.finish(messages, "search-1", true, "2 results"))
        assertEquals(ToolActivityStatus.COMPLETED, messages.single().toolSteps?.single()?.status)
    }
}
