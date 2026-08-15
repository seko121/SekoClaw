package com.sikoclaw.app.tool.web

import androidx.core.content.FileProvider
import com.sikoclaw.app.ClawApplication
import com.sikoclaw.app.tool.BaseTool
import com.sikoclaw.app.tool.ToolParameter
import com.sikoclaw.app.tool.ToolResult
import com.sikoclaw.app.ui.chat.AttachmentState
import com.sikoclaw.app.ui.chat.ChatAttachment
import com.sikoclaw.app.ui.chat.ChatAttachmentManager
import com.sikoclaw.app.utils.KVUtils
import com.sikoclaw.app.agent.llm.MediaProviderKind
import com.sikoclaw.app.agent.llm.MediaProviderStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/** OpenAI-compatible speech generation. The resulting audio is rendered as a chat attachment. */
class SpeechGenerationTool : BaseTool() {
    override fun getName() = "generate_speech"
    override fun getDisplayName() = "Text to speech"
    override fun getDescriptionEN() = "Generate spoken audio from text using the configured OpenAI-compatible speech API. Use when the user asks for speech or an audio file, and include the returned attachment marker exactly in the reply."
    override fun getDescriptionCN() = getDescriptionEN()
    override fun getParameters() = listOf(
        ToolParameter("text", "string", "Text to speak", true),
        ToolParameter("model", "string", "Speech model; default gpt-4o-mini-tts", false),
        ToolParameter("voice", "string", "Voice; default alloy", false),
        ToolParameter("speed", "number", "Playback speed from 0.25 to 4.0", false),
        ToolParameter("format", "string", "mp3, wav, opus, aac, or flac", false),
    )

    override fun execute(params: Map<String, Any>): ToolResult {
        if (!com.sikoclaw.app.plugin.PluginCatalog.isEnabled("tts")) return ToolResult.error("Text-to-speech plugin is disabled")
        val text = requireString(params, "text").trim()
        if (text.isBlank()) return ToolResult.error("Speech text is empty")
        val media = MediaProviderStore.get(MediaProviderKind.TTS)
        val provider = KVUtils.getLlmProvider()
        val apiKey = MediaProviderStore.apiKey(MediaProviderKind.TTS).ifBlank { KVUtils.getApiKeyForProvider(provider).ifEmpty { KVUtils.getLlmApiKey() } }
        if (apiKey.isBlank()) return ToolResult.error("Configure a speech-capable API key in Services first")
        val base = (if (media.enabled) media.baseUrl else KVUtils.getLlmBaseUrl()).ifBlank { "https://api.openai.com/v1" }.trimEnd('/')
        val format = optionalString(params, "format", media.format).lowercase().let { if (it in setOf("mp3", "wav", "opus", "aac", "flac")) it else "mp3" }
        val speed = params["speed"]?.toString()?.toDoubleOrNull()?.coerceIn(0.25, 4.0) ?: 1.0
        val body = com.google.gson.JsonObject().apply {
            addProperty("model", optionalString(params, "model", media.model))
            addProperty("voice", optionalString(params, "voice", media.option))
            addProperty("input", text)
            addProperty("speed", speed)
            addProperty("response_format", format)
        }.toString().toRequestBody("application/json".toMediaType())
        return runCatching {
            val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(180, TimeUnit.SECONDS).build()
            client.newCall(Request.Builder().url("$base/audio/speech").header("Authorization", "Bearer $apiKey").post(body).build()).execute().use { response ->
                if (!response.isSuccessful) return ToolResult.error("Speech generation failed (HTTP ${response.code})")
                val bytes = response.body?.bytes() ?: return ToolResult.error("Speech API returned an empty file")
                val dir = File(ClawApplication.instance.filesDir, "generated_files").apply { mkdirs() }
                val file = File(dir, "siko_speech_${System.currentTimeMillis()}.$format").apply { writeBytes(bytes) }
                val uri = FileProvider.getUriForFile(ClawApplication.instance, "${ClawApplication.instance.packageName}.fileprovider", file)
                val mime = when (format) { "wav" -> "audio/wav"; "opus" -> "audio/opus"; "aac" -> "audio/aac"; "flac" -> "audio/flac"; else -> "audio/mpeg" }
                val attachment = ChatAttachment(uri = uri.toString(), name = file.name, mimeType = mime,
                    sizeBytes = file.length(), state = AttachmentState.READY)
                ToolResult.success("Speech generated. Include this exact marker in your reply: ${ChatAttachmentManager.encodeMarker(attachment)}")
            }
        }.getOrElse { ToolResult.error("Speech generation failed: ${it.message}") }
    }
}
