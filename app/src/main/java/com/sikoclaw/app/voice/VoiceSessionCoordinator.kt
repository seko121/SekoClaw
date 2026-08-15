package com.sikoclaw.app.voice

import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Enforces one microphone/TTS session process-wide, including minimized calls. */
object VoiceSessionCoordinator {
    private var active: WeakReference<VoiceCallActivity>? = null
    private val _state = MutableStateFlow(VoiceLoopState.DISABLED)
    val state = _state.asStateFlow()
    @Synchronized fun attach(call: VoiceCallActivity) { active?.get()?.takeIf { it !== call }?.closeFromCoordinator(); active = WeakReference(call) }
    @Synchronized fun update(state: VoiceLoopState) { _state.value = state }
    @Synchronized fun detach(call: VoiceCallActivity) { if (active?.get() === call) { active = null; _state.value = VoiceLoopState.DISABLED } }
}
