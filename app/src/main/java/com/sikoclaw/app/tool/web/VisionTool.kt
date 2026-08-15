package com.sikoclaw.app.tool.web

import android.net.Uri
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.sikoclaw.app.ClawApplication
import com.sikoclaw.app.agent.llm.MultiProviderStore
import com.sikoclaw.app.tool.BaseTool
import com.sikoclaw.app.tool.ToolParameter
import com.sikoclaw.app.tool.ToolResult
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class VisionTool : BaseTool() {
    override fun getName() = "analyze_vision"
    override fun getDisplayName() = "Vision"
    override fun getDescriptionEN() = "Analyze an image or screenshot using the separately selected Vision Model. Use for OCR, screenshot understanding, UI element detection and coordinates."
    override fun getDescriptionCN() = getDescriptionEN()
    override fun getParameters() = listOf(
        ToolParameter("image_uri", "string", "content:// URI from the user's attachment or a screenshot tool", true),
        ToolParameter("question", "string", "What to inspect; request x/y coordinates when needed", true),
    )

    override fun execute(params: Map<String, Any>): ToolResult = runCatching {
        val (model, provider) = MultiProviderStore.visionRoute() ?: return ToolResult.error("Choose a Vision Model in Services first")
        val uri = Uri.parse(requireString(params, "image_uri"))
        val bytes = ClawApplication.instance.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return ToolResult.error("The image could not be opened")
        require(bytes.size <= 20 * 1024 * 1024) { "Image exceeds the 20 MB vision limit" }
        val mime = ClawApplication.instance.contentResolver.getType(uri) ?: "image/png"
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val question = requireString(params, "question")
        val key = MultiProviderStore.apiKey(provider.id)
        val protocol = provider.protocol.uppercase()
        val gson = Gson()
        val (url, payload) = if (protocol == "GEMINI") {
            provider.baseUrl.trimEnd('/') + "/models/${model.apiModelName}:generateContent" to mapOf(
                "contents" to listOf(mapOf("role" to "user", "parts" to listOf(
                    mapOf("text" to question), mapOf("inline_data" to mapOf("mime_type" to mime, "data" to encoded)),
                ))),
            )
        } else {
            provider.baseUrl.trimEnd('/') + provider.chatPath to mapOf(
                "model" to model.apiModelName,
                "messages" to listOf(mapOf("role" to "user", "content" to listOf(
                    mapOf("type" to "text", "text" to question),
                    mapOf("type" to "image_url", "image_url" to mapOf("url" to "data:$mime;base64,$encoded")),
                ))),
                "stream" to false,
            )
        }
        val builder = Request.Builder().url(url).post(gson.toJson(payload).toRequestBody("application/json".toMediaType()))
        if (protocol == "GEMINI") { if (key.isNotBlank()) builder.header("x-goog-api-key", key) }
        else if (key.isNotBlank()) builder.header("Authorization", "Bearer $key")
        provider.headers.forEach { (name, value) -> builder.header(name, value) }
        OkHttpClient.Builder().callTimeout(model.timeoutSeconds.toLong(), TimeUnit.SECONDS).build().newCall(builder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return ToolResult.error("Vision request failed (HTTP ${response.code})")
            val root = JsonParser.parseString(body).asJsonObject
            val text = if (protocol == "GEMINI") root.getAsJsonArray("candidates")?.firstOrNull()?.asJsonObject
                ?.getAsJsonObject("content")?.getAsJsonArray("parts")?.firstOrNull()?.asJsonObject?.get("text")?.asString
            else root.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject?.getAsJsonObject("message")?.get("content")?.asString
            ToolResult.success(text?.takeIf { it.isNotBlank() } ?: "Vision model returned no description")
        }
    }.getOrElse { ToolResult.error(it.message ?: "Vision analysis failed") }
}
