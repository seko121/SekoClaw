package com.sikoclaw.app.ui.chat

import org.junit.Assert.*
import org.junit.Test

class PluginMessageCodecTest {
    @Test fun directiveNeverLeaksIntoVisibleText() {
        val encoded = PluginMessageCodec.encode("Find the latest news", listOf("browser", "web_search", "browser"))
        val decoded = PluginMessageCodec.decode(encoded)
        assertEquals("Find the latest news", decoded.visibleText)
        assertEquals(listOf("browser", "web_search"), decoded.pluginIds)
    }
}
