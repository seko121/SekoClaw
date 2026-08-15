package com.sikoclaw.app.agent.llm

import com.google.gson.Gson
import com.sikoclaw.app.ClawApplication

enum class MediaProviderKind { IMAGE, TTS }
data class MediaProviderConfig(
    val kind: MediaProviderKind,
    val name: String = "OpenAI compatible",
    val baseUrl: String = "https://api.openai.com/v1",
    val model: String = if (kind == MediaProviderKind.IMAGE) "gpt-image-1" else "gpt-4o-mini-tts",
    val option: String = if (kind == MediaProviderKind.IMAGE) "1024x1024" else "alloy",
    val format: String = if (kind == MediaProviderKind.IMAGE) "b64_json" else "mp3",
    val enabled: Boolean = false,
)

object MediaProviderStore {
    private const val PREFS = "siko_media_providers"
    private val gson = Gson()
    private val app get() = ClawApplication.instance
    fun get(kind: MediaProviderKind): MediaProviderConfig = runCatching {
        gson.fromJson(app.getSharedPreferences(PREFS, 0).getString(kind.name, null), MediaProviderConfig::class.java)
    }.getOrNull() ?: MediaProviderConfig(kind)
    fun apiKey(kind: MediaProviderKind): String = SecureSecretStore.get("media_${kind.name}")
    fun save(config: MediaProviderConfig, apiKey: String?): ProviderSaveResult = runCatching {
        require(config.baseUrl.startsWith("https://") || config.baseUrl.startsWith("http://")) { "Enter a valid Base URL" }
        require(config.model.isNotBlank()) { "Model is required" }
        if (!apiKey.isNullOrBlank()) check(SecureSecretStore.put("media_${config.kind.name}", apiKey)) { "API key could not be saved securely" }
        val json = gson.toJson(config)
        val prefs = app.getSharedPreferences(PREFS, 0)
        check(prefs.edit().putString(config.kind.name, json).commit() && prefs.getString(config.kind.name, null) == json) { "Media provider write could not be verified" }
        ProviderSaveResult(true, "${config.kind.name.lowercase().replaceFirstChar(Char::uppercase)} provider saved")
    }.getOrElse { ProviderSaveResult(false, it.message ?: "Provider could not be saved") }
}
