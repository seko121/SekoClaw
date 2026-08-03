package com.sikoclaw.app.tool.web

import android.util.Base64
import androidx.core.content.FileProvider
import com.google.gson.JsonParser
import com.sikoclaw.app.ClawApplication
import com.sikoclaw.app.tool.BaseTool
import com.sikoclaw.app.tool.ToolParameter
import com.sikoclaw.app.tool.ToolResult
import com.sikoclaw.app.utils.KVUtils
import com.sikoclaw.app.agent.llm.MediaProviderKind
import com.sikoclaw.app.agent.llm.MediaProviderStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ImageGenerationTool : BaseTool() {
    override fun getName() = "generate_image"
    override fun getDisplayName() = "AI Image Generator"
    override fun getDescriptionEN() = "Generate an image from a prompt using the configured OpenAI-compatible Images API. The result includes a [[SIKO_IMAGE:...]] marker that must be copied exactly into the assistant reply so the image appears in chat with a Save button. Use only when the user explicitly asks for an image."
    override fun getDescriptionCN() = getDescriptionEN()
    override fun getParameters() = listOf(
        ToolParameter("prompt", "string", "Detailed description of the image", true),
        ToolParameter("model", "string", "Optional image model, default gpt-image-1", false),
        ToolParameter("size", "string", "Optional image size, default 1024x1024", false),
    )

    override fun execute(params: Map<String, Any>): ToolResult {
        if (!com.sikoclaw.app.plugin.PluginCatalog.isEnabled("image_generation")) return ToolResult.error("AI image generator plugin is disabled")
        val prompt = requireString(params, "prompt").trim()
        if (prompt.isEmpty()) return ToolResult.error("Image prompt is empty")
        val media = MediaProviderStore.get(MediaProviderKind.IMAGE)
        val provider = KVUtils.getLlmProvider()
        val apiKey = MediaProviderStore.apiKey(MediaProviderKind.IMAGE).ifBlank { KVUtils.getApiKeyForProvider(provider).ifEmpty { KVUtils.getLlmApiKey() } }
        if (apiKey.isBlank()) return ToolResult.error("Configure an image-capable API key in Models first")
        val base = (if (media.enabled) media.baseUrl else KVUtils.getLlmBaseUrl()).ifBlank { "https://api.openai.com/v1" }.trimEnd('/')
        val endpoint = "$base/images/generations"
        val model = optionalString(params, "model", media.model)
        val size = optionalString(params, "size", media.option)
        val body = com.google.gson.JsonObject().apply {
            addProperty("model", model); addProperty("prompt", prompt); addProperty("size", size); addProperty("n", 1)
            addProperty("response_format", "b64_json")
        }.toString().toRequestBody("application/json".toMediaType())
        return try {
            val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(180, TimeUnit.SECONDS).build()
            client.newCall(Request.Builder().url(endpoint).header("Authorization", "Bearer $apiKey").post(body).build()).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) return ToolResult.error("Image generation failed (HTTP ${response.code}): ${raw.take(500)}")
                val item = JsonParser.parseString(raw).asJsonObject.getAsJsonArray("data")?.firstOrNull()?.asJsonObject
                    ?: return ToolResult.error("Image API returned no image")
                val bytes = when {
                    item.has("b64_json") -> Base64.decode(item.get("b64_json").asString, Base64.DEFAULT)
                    item.has("url") -> downloadGeneratedImage(item.get("url").asString)
                    else -> return ToolResult.error("Image API response format is unsupported")
                }
                val name = "siko_image_${System.currentTimeMillis()}.png"
                val directory = java.io.File(ClawApplication.instance.filesDir, "generated_images").apply { mkdirs() }
                val file = java.io.File(directory, name).apply { writeBytes(bytes) }
                val uri = FileProvider.getUriForFile(ClawApplication.instance, "${ClawApplication.instance.packageName}.fileprovider", file)
                ToolResult.success("Image generated. Include this exact marker in your reply: [[SIKO_IMAGE:$uri]]")
            }
        } catch (e: Exception) { ToolResult.error("Image generation failed: ${e.message}") }
    }

    private fun downloadGeneratedImage(url: String): ByteArray {
        if (!url.startsWith("https://")) error("Image API returned an unsafe URL")
        return OkHttpClient().newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) error("Image download HTTP ${response.code}")
            response.body?.bytes() ?: error("Empty generated image")
        }
    }
}
