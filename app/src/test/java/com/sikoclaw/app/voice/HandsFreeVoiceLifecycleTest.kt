package com.sikoclaw.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HandsFreeVoiceLifecycleTest {
    @Test fun `continuous voice cycle returns to wake word`() {
        var state = HandsFreeVoiceLifecycle.reduce(VoiceLoopSnapshot(), VoiceLoopEvent.Enable)
        state = HandsFreeVoiceLifecycle.reduce(state, VoiceLoopEvent.WakeWordDetected)
        assertTrue(state.microphoneActive)
        state = HandsFreeVoiceLifecycle.reduce(state, VoiceLoopEvent.TranscriptReady)
        state = HandsFreeVoiceLifecycle.reduce(state, VoiceLoopEvent.ToolStarted)
        state = HandsFreeVoiceLifecycle.reduce(state, VoiceLoopEvent.ReplyReady)
        state = HandsFreeVoiceLifecycle.reduce(state, VoiceLoopEvent.SpeechFinished)
        assertEquals(VoiceLoopState.WAITING_FOR_WAKE_WORD, state.state)
    }

    @Test fun `speech interruption stops speaking and listens`() {
        val speaking = VoiceLoopSnapshot(VoiceLoopState.SPEAKING)
        val interrupted = HandsFreeVoiceLifecycle.reduce(speaking, VoiceLoopEvent.UserInterrupted)
        assertEquals(VoiceLoopState.LISTENING, interrupted.state)
        assertTrue(interrupted.microphoneActive)
    }
}
