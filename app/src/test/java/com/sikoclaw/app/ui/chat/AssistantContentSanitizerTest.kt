package com.sikoclaw.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantContentSanitizerTest {
    @Test fun `thinking tags are hidden`() = assertEquals("Final answer", finalAssistantText("<think>private chain</think>Final answer"))
    @Test fun `structured response uses final content`() = assertEquals("Visible", finalAssistantText("{\"reasoning\":\"hidden\",\"content\":\"Visible\"}"))
}
