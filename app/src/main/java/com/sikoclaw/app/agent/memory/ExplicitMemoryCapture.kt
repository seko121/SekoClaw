package com.sikoclaw.app.agent.memory

import java.util.Locale

object ExplicitMemoryCapture {
    private val triggers = listOf("remember this", "remember that", "save this", "don't forget", "do not forget", "افتكر", "احفظ", "سجل المعلوم", "معلومة مهمة")

    fun capture(text: String): MemoryEntry? {
        if (!KaiMemoryStore.isEnabled()) return null
        val fact = extract(text) ?: return null
        MemoryCaptureApproval.request(fact)
        return null
    }

    fun extract(text: String): String? {
        val normalized = text.trim()
        if (normalized.length !in 4..1200 || triggers.none { normalized.lowercase(Locale.ROOT).contains(it) }) return null
        // Explicit memory requests are useful, but secrets must never become durable memory.
        val sensitive = Regex(
            "(?i)(password|passcode|api[ _-]?key|access[ _-]?token|secret|pin|cvv|card number|" +
                "\\u0643\\u0644\\u0645\\u0629 \\u0627\\u0644\\u0633\\u0631|\\u0631\\u0645\\u0632 \\u0627\\u0644\\u062f\\u062e\\u0648\\u0644|\\u0645\\u0641\\u062a\\u0627\\u062d api|\\u0631\\u0642\\u0645 \\u0627\\u0644\\u0628\\u0637\\u0627\\u0642\\u0629)"
        )
        if (sensitive.containsMatchIn(normalized)) return null
        return normalized
            .replace(Regex("(?i)^(please\\s+)?(remember this|remember that|save this|don't forget|do not forget)[:،,\\s-]*"), "")
            .replace(Regex("^(لو سمحت\\s+)?(افتكر|احفظ|سجل)[^:،,]*[:،,\\s-]*"), "")
            .trim().ifBlank { normalized }
    }
}
