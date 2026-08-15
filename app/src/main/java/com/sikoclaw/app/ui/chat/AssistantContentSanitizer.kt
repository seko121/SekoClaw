package com.sikoclaw.app.ui.chat

import com.google.gson.JsonParser

/** Keeps provider reasoning metadata out of user-visible assistant bubbles. */
internal fun finalAssistantText(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
        runCatching {
            val json = JsonParser.parseString(trimmed).asJsonObject
            val final = listOf("content", "final", "answer", "output_text")
                .firstNotNullOfOrNull { key -> json.get(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() } }
            if (final != null) return final
        }
    }
    return raw
        .replace(Regex("(?is)<think>.*?</think>"), "")
        .replace(Regex("(?is)<analysis>.*?</analysis>"), "")
        .trim()
}
