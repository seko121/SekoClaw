/* Adapted from Kai network/Requests.kt. Copyright Simon Schubert and contributors. Apache-2.0. */
package com.sikoclaw.app.agent.llm.kai

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.sikoclaw.app.agent.llm.ApiModelConfig
import com.sikoclaw.app.agent.llm.ApiProviderConfig
import com.sikoclaw.app.agent.llm.MultiProviderStore
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object KaiProviderGateway {
    private val gson = Gson()

    fun fetchModels(provider: ApiProviderConfig, apiKeyOverride: String? = null): List<String> {
        val key = apiKeyOverride ?: MultiProviderStore.apiKey(provider.id)
        val protocol = protocol(provider)
        val path = provider.modelsPath ?: return KaiServiceRegistry.byId(provider.catalogId)?.defaultModels.orEmpty()
        val request = authorized(Request.Builder().url(provider.baseUrl.trimEnd('/') + path), provider, key).get().build()
        OkHttpClient().newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ProviderRequestException(readableError(response.code, body))
            val root = JsonParser.parseString(body).asJsonObject
            return when (protocol) {
                ProviderProtocol.GEMINI -> root.getAsJsonArray("models")?.mapNotNull { item ->
                    item.asJsonObject.get("name")?.asString?.removePrefix("models/")
                }.orEmpty()
                else -> root.getAsJsonArray("data")?.mapNotNull { it.asJsonObject.get("id")?.asString }.orEmpty()
            }
        }
    }

    fun testModel(model: ApiModelConfig, provider: ApiProviderConfig, apiKeyOverride: String? = null) {
        val key = apiKeyOverride ?: MultiProviderStore.apiKey(provider.id)
        val protocol = protocol(provider)
        val url: String
        val payload: Map<String, Any>
        when (protocol) {
            ProviderProtocol.GEMINI -> {
                url = provider.baseUrl.trimEnd('/') + "/models/${model.apiModelName}:generateContent"
                payload = mapOf("contents" to listOf(mapOf("role" to "user", "parts" to listOf(mapOf("text" to "Reply OK")))))
            }
            ProviderProtocol.ANTHROPIC -> {
                url = provider.baseUrl.trimEnd('/') + provider.chatPath
                payload = mapOf("model" to model.apiModelName, "max_tokens" to 4, "messages" to listOf(mapOf("role" to "user", "content" to "Reply OK")))
            }
            ProviderProtocol.KAI_FREE -> {
                url = provider.baseUrl.trimEnd('/') + provider.chatPath
                payload = mapOf("model" to model.apiModelName, "messages" to listOf(mapOf("role" to "user", "content" to "Reply OK")))
            }
            else -> {
                url = provider.baseUrl.trimEnd('/') + provider.chatPath
                payload = mapOf("model" to model.apiModelName, "max_tokens" to 4, "messages" to listOf(mapOf("role" to "user", "content" to "Reply OK")))
            }
        }
        val request = authorized(Request.Builder().url(url), provider, key)
            .post(gson.toJson(payload).toRequestBody("application/json".toMediaType())).build()
        OkHttpClient.Builder().callTimeout(model.timeoutSeconds.toLong(), TimeUnit.SECONDS).build().newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ProviderRequestException(readableError(response.code, body))
        }
    }

    private fun authorized(builder: Request.Builder, provider: ApiProviderConfig, key: String): Request.Builder {
        when (protocol(provider)) {
            ProviderProtocol.GEMINI -> if (key.isNotBlank()) builder.header("x-goog-api-key", key)
            ProviderProtocol.ANTHROPIC -> {
                if (key.isNotBlank()) builder.header("x-api-key", key)
                builder.header("anthropic-version", "2023-06-01")
            }
            else -> if (key.isNotBlank()) builder.header("Authorization", "Bearer $key")
        }
        provider.headers.forEach { (name, value) -> builder.header(name, value) }
        return builder
    }

    private fun protocol(provider: ApiProviderConfig): ProviderProtocol =
        runCatching { ProviderProtocol.valueOf(provider.protocol) }.getOrDefault(ProviderProtocol.OPENAI_COMPATIBLE)

    fun readableError(code: Int, body: String): String {
        val detail = runCatching {
            val root = JsonParser.parseString(body).asJsonObject
            root.getAsJsonObject("error")?.get("message")?.asString
                ?: root.get("message")?.asString
        }.getOrNull()
        return when (code) {
            401, 403 -> "Invalid API key"
            404 -> "Model or endpoint not found"
            408 -> "Connection timed out"
            429 -> "Rate limit reached"
            402 -> "Insufficient balance"
            in 500..599 -> "Provider is unavailable"
            else -> detail?.take(220) ?: "Connection failed (HTTP $code)"
        }
    }

    fun friendlyError(error: Throwable): String {
        val text = generateSequence(error) { it.cause }.mapNotNull { it.message }.joinToString(" ").lowercase()
        return when {
            "401" in text || "403" in text || "unauthorized" in text || "api key" in text -> "Invalid API key"
            "404" in text || "model not found" in text -> "Model not found"
            "429" in text || "rate limit" in text -> "Rate limit reached"
            "402" in text || "insufficient" in text || "balance" in text -> "Insufficient balance"
            "timeout" in text || "timed out" in text -> "Connection timed out"
            listOf("500", "502", "503", "504", "unavailable").any(text::contains) -> "Provider is unavailable"
            "tool" in text && ("support" in text || "unsupported" in text) -> "This model does not support tools"
            else -> error.message?.take(240) ?: "Provider request failed"
        }
    }

    fun testConnection(provider: ApiProviderConfig, apiKeyOverride: String? = null) {
        if (provider.modelsPath != null) {
            fetchModels(provider, apiKeyOverride)
            return
        }
        val modelId = KaiServiceRegistry.byId(provider.catalogId)?.defaultModels?.firstOrNull()
            ?: throw ProviderRequestException("Add a model before testing this provider")
        testModel(ApiModelConfig(providerId = provider.id, displayName = modelId, apiModelName = modelId), provider, apiKeyOverride)
    }
}

class ProviderRequestException(message: String) : Exception(message)
