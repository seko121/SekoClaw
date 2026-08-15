package com.sikoclaw.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AgentControlState { IDLE, STARTING, ACTIVE, STOPPING, FAILED }

object AgentControlSession {
    private val _state = MutableStateFlow(AgentControlState.IDLE)
    val state: StateFlow<AgentControlState> = _state.asStateFlow()

    @Synchronized fun start() {
        if (_state.value == AgentControlState.ACTIVE) return
        _state.value = AgentControlState.STARTING
        AgentControlOverlay.setAgentActive(true)
        _state.value = AgentControlState.ACTIVE
        if (com.sikoclaw.app.floating.FloatingAssistantConfig.enabled() && com.sikoclaw.app.floating.FloatingAssistantConfig.autoStart() && android.provider.Settings.canDrawOverlays(com.sikoclaw.app.ClawApplication.instance)) {
            androidx.core.content.ContextCompat.startForegroundService(com.sikoclaw.app.ClawApplication.instance, android.content.Intent(com.sikoclaw.app.ClawApplication.instance, com.sikoclaw.app.floating.FloatingAssistantService::class.java))
        }
    }

    @JvmStatic @Synchronized fun stop(failed: Boolean = false) {
        _state.value = if (failed) AgentControlState.FAILED else AgentControlState.STOPPING
        AgentControlOverlay.stopControlSession()
        if (com.sikoclaw.app.floating.FloatingAssistantConfig.enabled() && com.sikoclaw.app.floating.FloatingAssistantConfig.autoStop()) {
            com.sikoclaw.app.ClawApplication.instance.stopService(android.content.Intent(com.sikoclaw.app.ClawApplication.instance, com.sikoclaw.app.floating.FloatingAssistantService::class.java))
        }
        _state.value = AgentControlState.IDLE
    }
}
