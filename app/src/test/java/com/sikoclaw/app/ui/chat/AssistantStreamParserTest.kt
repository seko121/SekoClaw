package com.sikoclaw.app.ui.chat

import org.junit.Assert.*
import org.junit.Test

class AssistantStreamParserTest {
    @Test fun separatesReasoningFromFinalContent() {
        val parsed = AssistantStreamParser.parse("<think>checking facts</think>Here is **the answer**")
        assertEquals("checking facts", parsed.reasoning)
        assertEquals("Here is **the answer**", parsed.visible)
    }

    @Test fun hidesUnfinishedReasoningDuringStream() {
        val parsed = AssistantStreamParser.parse("<analysis>still working")
        assertEquals("still working", parsed.reasoning)
        assertTrue(parsed.visible.isBlank())
    }
}
