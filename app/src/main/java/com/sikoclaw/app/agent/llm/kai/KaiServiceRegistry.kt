/*
 * Adapted from Kai's data/Service.kt.
 * Copyright Simon Schubert and Kai contributors. Apache-2.0.
 * Compose-resource references were intentionally replaced with Android-neutral metadata.
 */
package com.sikoclaw.app.agent.llm.kai

enum class ProviderProtocol { OPENAI_COMPATIBLE, ANTHROPIC, GEMINI, LOCAL_LITERT, KAI_FREE }

data class KaiServiceDefinition(
    val id: String,
    val displayName: String,
    val protocol: ProviderProtocol = ProviderProtocol.OPENAI_COMPATIBLE,
    val baseUrl: String,
    val modelsPath: String? = "/models",
    val chatPath: String = "/chat/completions",
    val requiresApiKey: Boolean = true,
    val optionalApiKey: Boolean = false,
    val supportsImages: Boolean = true,
    val supportsPdf: Boolean = false,
    val supportsTools: Boolean = true,
    val defaultModels: List<String> = emptyList(),
    val apiKeyUrl: String? = null,
)

/** The provider catalog and endpoints are kept in the same order as Kai. */
object KaiServiceRegistry {
    val all: List<KaiServiceDefinition> = listOf(
        KaiServiceDefinition("free", "Free", ProviderProtocol.KAI_FREE, "https://api.kai9000.com", null, "/chat/completions", false, supportsImages = false, defaultModels = listOf("fast", "expert")),
        KaiServiceDefinition("atlascloud", "Atlas Cloud", baseUrl = "https://api.atlascloud.ai/v1", apiKeyUrl = "https://www.atlascloud.ai/console/api-keys"),
        KaiServiceDefinition("gemini", "Gemini", ProviderProtocol.GEMINI, "https://generativelanguage.googleapis.com/v1beta", null, "/models", supportsPdf = true, apiKeyUrl = "https://aistudio.google.com/apikey"),
        KaiServiceDefinition("anthropic", "Anthropic", ProviderProtocol.ANTHROPIC, "https://api.anthropic.com/v1", "/models", "/messages", supportsPdf = true, apiKeyUrl = "https://console.anthropic.com/settings/keys"),
        KaiServiceDefinition("openai", "OpenAI", baseUrl = "https://api.openai.com/v1", supportsPdf = true, apiKeyUrl = "https://platform.openai.com/api-keys"),
        KaiServiceDefinition("deepseek", "DeepSeek", baseUrl = "https://api.deepseek.com", apiKeyUrl = "https://platform.deepseek.com/api_keys"),
        KaiServiceDefinition("mistral", "Mistral", baseUrl = "https://api.mistral.ai/v1", apiKeyUrl = "https://console.mistral.ai/api-keys"),
        KaiServiceDefinition("xai", "xAI", baseUrl = "https://api.x.ai/v1", apiKeyUrl = "https://console.x.ai"),
        KaiServiceDefinition("openrouter", "OpenRouter", baseUrl = "https://openrouter.ai/api/v1", supportsPdf = true, apiKeyUrl = "https://openrouter.ai/settings/keys"),
        KaiServiceDefinition("groqcloud", "GroqCloud", baseUrl = "https://api.groq.com/openai/v1", apiKeyUrl = "https://console.groq.com/keys"),
        KaiServiceDefinition("nvidia", "NVIDIA", baseUrl = "https://integrate.api.nvidia.com/v1", apiKeyUrl = "https://build.nvidia.com/settings/api-keys"),
        KaiServiceDefinition("cerebras", "Cerebras", baseUrl = "https://api.cerebras.ai/v1", apiKeyUrl = "https://cloud.cerebras.ai"),
        KaiServiceDefinition("ollamacloud", "Ollama Cloud", baseUrl = "https://ollama.com/v1", apiKeyUrl = "https://ollama.com/settings/keys"),
        KaiServiceDefinition("longcat", "LongCat", baseUrl = "https://api.longcat.chat/openai/v1", defaultModels = listOf("LongCat-Flash-Chat", "LongCat-Flash-Thinking", "LongCat-Flash-Thinking-2601", "LongCat-Flash-Lite", "LongCat-Flash-Omni-2603"), apiKeyUrl = "https://longcat.chat/platform"),
        KaiServiceDefinition("together", "Together AI", baseUrl = "https://api.together.xyz/v1", apiKeyUrl = "https://api.together.ai/settings/api-keys"),
        KaiServiceDefinition("huggingface", "Hugging Face", baseUrl = "https://router.huggingface.co/v1", apiKeyUrl = "https://huggingface.co/settings/tokens"),
        KaiServiceDefinition("venice", "Venice AI", baseUrl = "https://api.venice.ai/api/v1", apiKeyUrl = "https://venice.ai/settings/api"),
        KaiServiceDefinition("moonshot", "Moonshot AI", baseUrl = "https://api.moonshot.cn/v1", apiKeyUrl = "https://platform.moonshot.cn/console/api-keys"),
        KaiServiceDefinition("zai", "Z.AI", baseUrl = "https://api.z.ai/api/paas/v4", apiKeyUrl = "https://z.ai/manage-apikey/apikey-list"),
        KaiServiceDefinition("zai-coding-plan", "Z.AI Coding Plan", baseUrl = "https://api.z.ai/api/coding/paas/v4", apiKeyUrl = "https://z.ai/manage-apikey/apikey-list"),
        KaiServiceDefinition("minimax", "MiniMax", baseUrl = "https://api.minimax.io/v1", apiKeyUrl = "https://platform.minimax.io"),
        KaiServiceDefinition("aihubmix", "AIHubMix", baseUrl = "https://aihubmix.com/v1", apiKeyUrl = "https://aihubmix.com/token"),
        KaiServiceDefinition("deepinfra", "Deep Infra", baseUrl = "https://api.deepinfra.com/v1/openai", apiKeyUrl = "https://deepinfra.com/dash/api_keys"),
        KaiServiceDefinition("fireworksai", "Fireworks AI", baseUrl = "https://api.fireworks.ai/inference/v1", apiKeyUrl = "https://app.fireworks.ai/settings/users/api-keys"),
        KaiServiceDefinition("opencode", "OpenCode", baseUrl = "https://opencode.ai/zen/v1", apiKeyUrl = "https://opencode.ai/docs/zen"),
        KaiServiceDefinition("publicai", "Public AI", baseUrl = "https://api.publicai.co/v1", apiKeyUrl = "https://platform.publicai.co"),
        KaiServiceDefinition("perplexity", "Perplexity", baseUrl = "https://api.perplexity.ai", modelsPath = null, defaultModels = listOf("sonar", "sonar-pro", "sonar-reasoning-pro", "sonar-deep-research"), apiKeyUrl = "https://console.perplexity.ai"),
        KaiServiceDefinition("openai-compatible", "OpenAI-Compatible API", baseUrl = "http://localhost:11434/v1", requiresApiKey = false, optionalApiKey = true),
        KaiServiceDefinition("litert", "Local Model", ProviderProtocol.LOCAL_LITERT, "", null, "", false),
    )

    val cloudServices: List<KaiServiceDefinition> = all.filter { it.protocol != ProviderProtocol.LOCAL_LITERT }

    fun byId(id: String): KaiServiceDefinition? = all.firstOrNull { it.id == id }
}
