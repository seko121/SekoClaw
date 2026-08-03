/* Android/JVM adaptation of Kai's Gemini request/response path. Apache-2.0. */
package com.sikoclaw.app.agent.llm

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.sikoclaw.app.agent.AgentConfig
import com.sikoclaw.app.agent.llm.kai.KaiProviderGateway
import com.sikoclaw.app.agent.llm.kai.ProviderRequestException
import dev.langchain4j.agent.tool.ToolExecutionRequest
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.ToolExecutionResultMessage
import dev.langchain4j.data.message.UserMessage
import java.util.UUID
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class GeminiLlmClient(private val config: AgentConfig) : LlmClient {
    private val gson = Gson()

    override fun chat(messages: List<ChatMessage>, toolSpecs: List<ToolSpecification>): LlmResponse {
        val body = linkedMapOf<String, Any>()
        messages.filterIsInstance<SystemMessage>().firstOrNull()?.let {
            body["systemInstruction"] = mapOf("parts" to listOf(mapOf("text" to it.text())))
        }
        body["contents"] = messages.filterNot { it is SystemMessage }.mapNotNull(::content)
        if (toolSpecs.isNotEmpty()) {
            body["tools"] = listOf(mapOf("functionDeclarations" to toolSpecs.map { spec ->
                mapOf("name" to spec.name(), "description" to spec.description(), "parameters" to JsonParser.parseString(gson.toJson(spec.parameters())))
            }))
        }
        val base = config.baseUrl.trimEnd('/').removeSuffix("/openai")
        val url = "$base/models/${config.modelName}:generateContent"
        val request = Request.Builder().url(url).header("x-goog-api-key", config.apiKey)
            .post(gson.toJson(body).toRequestBody("application/json".toMediaType())).build()
        OkHttpClient.Builder().callTimeout(120, TimeUnit.SECONDS).build().newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ProviderRequestException(KaiProviderGateway.readableError(response.code, raw))
            val root = JsonParser.parseString(raw).asJsonObject
            val parts: JsonArray = root.getAsJsonArray("candidates")?.firstOrNull()?.asJsonObject?.getAsJsonObject("content")?.getAsJsonArray("parts") ?: JsonArray()
            val text = parts.mapNotNull { element -> element.asJsonObject.get("text")?.asString }.joinToString("\n").takeIf { it.isNotBlank() }
            val calls = parts.mapNotNull { part ->
                part.asJsonObject.getAsJsonObject("functionCall")?.let { call ->
                    ToolExecutionRequest.builder().id(UUID.randomUUID().toString()).name(call.get("name").asString).arguments(call.get("args")?.toString() ?: "{}").build()
                }
            }
            return LlmResponse(text, calls, modelName = config.modelName)
        }
    }

    override fun chatStreaming(messages: List<ChatMessage>, toolSpecs: List<ToolSpecification>, listener: StreamingListener): LlmResponse =
        try {
            chat(messages, toolSpecs).also { result -> result.text?.let(listener::onPartialText); listener.onComplete(result) }
        } catch (error: Throwable) {
            listener.onError(error); throw error
        }

    private fun content(message: ChatMessage): Map<String, Any>? = when (message) {
        is UserMessage -> mapOf("role" to "user", "parts" to listOf(mapOf("text" to message.singleText())))
        is AiMessage -> {
            val parts = mutableListOf<Map<String, Any>>()
            message.text()?.takeIf { it.isNotBlank() }?.let { parts += mapOf("text" to it) }
            message.toolExecutionRequests().orEmpty().forEach { call ->
                val args: JsonElement = runCatching { JsonParser.parseString(call.arguments()) }.getOrElse { JsonParser.parseString("{}") }
                parts += mapOf("functionCall" to mapOf("name" to call.name(), "args" to args))
            }
            mapOf("role" to "model", "parts" to parts)
        }
        is ToolExecutionResultMessage -> mapOf("role" to "user", "parts" to listOf(mapOf("functionResponse" to mapOf("name" to message.toolName(), "response" to mapOf("result" to message.text())))))
        else -> null
    }
}
