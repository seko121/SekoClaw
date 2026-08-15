package com.sikoclaw.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationSearchFormatterTest {
    @Test fun `creates compact highlighted snippet`() {
        val result = ConversationSearchFormatter.format("one two three important phrase four five", "IMPORTANT", 8)
        assertTrue(result.text.length < 45)
        assertEquals(1, result.matches.size)
        assertEquals("important", result.text.substring(result.matches.single()).lowercase())
    }
}
