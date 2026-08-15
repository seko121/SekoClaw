package com.sikoclaw.app.ui.chat

data class PluginMessagePayload(val visibleText: String, val pluginIds: List<String>)

object PluginMessageCodec {
    private val directive = Regex("^\\[Use plugins?: ([^]]+)]\\s*", RegexOption.IGNORE_CASE)

    fun encode(text: String, pluginIds: List<String>): String =
        if (pluginIds.isEmpty()) text else "[Use plugins: ${pluginIds.distinct().joinToString(",")}] $text"

    fun decode(raw: String): PluginMessagePayload {
        val match = directive.find(raw) ?: return PluginMessagePayload(raw, emptyList())
        val ids = match.groupValues[1].split(',').map(String::trim).filter(String::isNotBlank).distinct()
        return PluginMessagePayload(raw.removeRange(match.range).trimStart(), ids)
    }
}
