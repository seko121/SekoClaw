package com.sikoclaw.app.agent

import androidx.annotation.DrawableRes
import com.sikoclaw.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class OctoBotMotion { IDLE, THINKING, WORKING, READY }

/** Single source of truth for the avatar shown by chat, tasks and the floating assistant. */
object AgentAnimationState {
    private val mutable = MutableStateFlow(OctoBotMotion.IDLE)
    val state: StateFlow<OctoBotMotion> = mutable

    fun set(value: OctoBotMotion) { mutable.value = value }

    @DrawableRes
    fun drawable(value: OctoBotMotion = mutable.value): Int = when (value) {
        OctoBotMotion.IDLE -> R.drawable.octobot_idle
        OctoBotMotion.THINKING -> R.drawable.octobot_thinking
        OctoBotMotion.WORKING -> R.drawable.octobot_working
        OctoBotMotion.READY -> R.drawable.octobot_ready
    }
}
