package com.sikoclaw.app.heartbeat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartbeatManagerTest {
    @Test fun `legacy and damaged settings are sanitized`() {
        val safe = HeartbeatManager.sanitize(HeartbeatConfig(intervalMinutes = -10, activeFrom = -2, activeUntil = 99, prompt = "  "))
        assertEquals(15, safe.intervalMinutes)
        assertEquals(0, safe.activeFrom)
        assertEquals(23, safe.activeUntil)
        assertTrue(safe.prompt.isNotBlank())
    }
}
