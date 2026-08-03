package com.sikoclaw.app.agent.llm

import com.sikoclaw.app.agent.llm.kai.KaiProviderGateway
import com.sikoclaw.app.agent.llm.kai.KaiServiceRegistry
import com.sikoclaw.app.agent.llm.kai.ProviderProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KaiProviderRegistryTest {
    @Test fun includesKaiImplementedProtocolsAndCatalog() {
        assertTrue(KaiServiceRegistry.cloudServices.size >= 25)
        assertEquals(ProviderProtocol.GEMINI, KaiServiceRegistry.byId("gemini")?.protocol)
        assertEquals(ProviderProtocol.ANTHROPIC, KaiServiceRegistry.byId("anthropic")?.protocol)
        assertEquals("https://openrouter.ai/api/v1", KaiServiceRegistry.byId("openrouter")?.baseUrl)
    }

    @Test fun mapsProviderErrorsToUserFacingMessages() {
        assertEquals("Invalid API key", KaiProviderGateway.friendlyError(IllegalStateException("HTTP 401 unauthorized")))
        assertEquals("Rate limit reached", KaiProviderGateway.friendlyError(IllegalStateException("HTTP 429")))
        assertEquals("Provider is unavailable", KaiProviderGateway.friendlyError(IllegalStateException("HTTP 503")))
    }
}
