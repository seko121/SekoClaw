package com.sikoclaw.app.agent.llm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiProviderFallbackTest {
    @Test fun retriesTransientProviderFailures() {
        listOf("timeout", "HTTP 429", "HTTP 500", "HTTP 502", "HTTP 503", "HTTP 504", "network error", "provider unavailable")
            .forEach { assertTrue(it, MultiProviderStore.shouldFallback(IllegalStateException(it), false, false)) }
    }

    @Test fun neverRetriesUnsafeOrTerminalFailures() {
        listOf("invalid API key", "HTTP 401 unauthorized", "invalid request HTTP 400", "content policy rejection")
            .forEach { assertFalse(it, MultiProviderStore.shouldFallback(IllegalStateException(it), false, false)) }
        assertFalse(MultiProviderStore.shouldFallback(IllegalStateException("timeout"), true, false))
        assertFalse(MultiProviderStore.shouldFallback(IllegalStateException("timeout"), false, true))
        assertTrue(MultiProviderStore.shouldFallback(IllegalStateException("invalid API key"), false, false, hasOtherProvider = true))
    }
}
