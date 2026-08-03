package com.sikoclaw.app.ui.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserInputTest {
    @Test fun preservesDirectUrl() = assertEquals("https://google.com", resolveBrowserInput("https://google.com"))
    @Test fun addsHttpsToHost() = assertEquals("https://example.com", resolveBrowserInput("example.com"))
    @Test fun convertsWordsToSearch() = assertTrue(resolveBrowserInput("best Android terminal").endsWith("best+Android+terminal"))
}
