package com.sikoclaw.app.agent.memory

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

data class MemoryCaptureRequest(val id: String, val fact: String)

/** Keeps long-term memory explicit: a detected fact is never persisted until the user agrees. */
object MemoryCaptureApproval {
    private val _pending = MutableStateFlow<MemoryCaptureRequest?>(null)
    val pending = _pending.asStateFlow()
    fun request(fact: String) { if (_pending.value == null) _pending.value = MemoryCaptureRequest(UUID.randomUUID().toString(), fact) }
    fun resolve(id: String, allow: Boolean) {
        val request = _pending.value ?: return
        if (request.id != id) return
        if (allow && KaiMemoryStore.isEnabled()) {
            val key = "user-${request.fact.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').take(48)}"
            KaiMemoryStore.store(key, request.fact, MemoryCategory.GENERAL, "user-approved-capture")
        }
        _pending.value = null
    }
}
