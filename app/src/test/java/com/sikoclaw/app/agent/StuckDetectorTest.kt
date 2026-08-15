package com.sikoclaw.app.agent

import org.junit.Assert.*
import org.junit.Test

class StuckDetectorTest {
    @Test fun unchangedScreenAloneNeverKillsBalancedTask() {
        val detector = StuckDetector(mode = StuckDetectionMode.BALANCED)
        repeat(30) { step ->
            val result = detector.record(action = "wait:$step", screenHash = 7, screenDiffCount = 0, error = null)
            assertNotEquals(StuckDetector.RecoveryLevel.AUTO_KILL, result?.level)
        }
    }

    @Test fun disabledModeNeverReports() {
        val detector = StuckDetector(mode = StuckDetectionMode.DISABLED)
        repeat(30) { assertNull(detector.record("tap:same", 1, 0, "same error")) }
    }

    @Test fun aRealRepeatedActionLoopEventuallyStops() {
        val detector = StuckDetector(mode = StuckDetectionMode.STRICT)
        var killed = false
        repeat(20) { killed = killed || detector.record("tap:same", 1, 0, null)?.level == StuckDetector.RecoveryLevel.AUTO_KILL }
        assertTrue(killed)
    }
}
