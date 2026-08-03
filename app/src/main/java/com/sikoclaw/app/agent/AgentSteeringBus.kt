package com.sikoclaw.app.agent

import java.util.concurrent.ConcurrentLinkedQueue

/** Thread-safe instructions consumed by the running agent at the next loop checkpoint. */
object AgentSteeringBus {
    private val steering = ConcurrentLinkedQueue<String>()
    fun steer(instruction: String) { instruction.trim().takeIf(String::isNotBlank)?.let(steering::offer) }
    fun drainSteering(): List<String> = buildList { while (true) add(steering.poll() ?: break) }
    fun clear() = steering.clear()
}
