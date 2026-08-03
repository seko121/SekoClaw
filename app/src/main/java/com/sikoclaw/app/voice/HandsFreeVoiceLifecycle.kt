package com.sikoclaw.app.voice

/**
 * Original OctoBot voice-loop state machine. It deliberately contains no Dicio code.
 * Audio engines (Vosk/OpenWakeWord/TTS) feed events into this class.
 */
enum class VoiceLoopState { DISABLED, WAITING_FOR_WAKE_WORD, LISTENING, THINKING, WORKING, SPEAKING, ERROR }

sealed interface VoiceLoopEvent {
    data object Enable : VoiceLoopEvent
    data object Disable : VoiceLoopEvent
    data object WakeWordDetected : VoiceLoopEvent
    data object SpeechStarted : VoiceLoopEvent
    data object TranscriptReady : VoiceLoopEvent
    data object ToolStarted : VoiceLoopEvent
    data object ReplyReady : VoiceLoopEvent
    data object SpeechFinished : VoiceLoopEvent
    data object UserInterrupted : VoiceLoopEvent
    data class Failed(val message: String) : VoiceLoopEvent
}

data class VoiceLoopSnapshot(
    val state: VoiceLoopState = VoiceLoopState.DISABLED,
    val error: String? = null,
    val microphoneActive: Boolean = false,
)

object HandsFreeVoiceLifecycle {
    fun reduce(current: VoiceLoopSnapshot, event: VoiceLoopEvent): VoiceLoopSnapshot = when (event) {
        VoiceLoopEvent.Enable -> VoiceLoopSnapshot(VoiceLoopState.WAITING_FOR_WAKE_WORD)
        VoiceLoopEvent.Disable -> VoiceLoopSnapshot()
        VoiceLoopEvent.WakeWordDetected, VoiceLoopEvent.SpeechStarted ->
            VoiceLoopSnapshot(VoiceLoopState.LISTENING, microphoneActive = true)
        VoiceLoopEvent.TranscriptReady -> VoiceLoopSnapshot(VoiceLoopState.THINKING)
        VoiceLoopEvent.ToolStarted -> VoiceLoopSnapshot(VoiceLoopState.WORKING)
        VoiceLoopEvent.ReplyReady -> VoiceLoopSnapshot(VoiceLoopState.SPEAKING)
        VoiceLoopEvent.SpeechFinished -> VoiceLoopSnapshot(VoiceLoopState.WAITING_FOR_WAKE_WORD)
        VoiceLoopEvent.UserInterrupted -> VoiceLoopSnapshot(VoiceLoopState.LISTENING, microphoneActive = true)
        is VoiceLoopEvent.Failed -> VoiceLoopSnapshot(VoiceLoopState.ERROR, event.message)
    }
}
