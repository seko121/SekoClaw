package com.sikoclaw.app.tool.web

import com.sikoclaw.app.utils.KVUtils
import com.sikoclaw.app.agent.llm.SecureSecretStore

/** Search is provider-driven; DuckDuckGo remains the private no-key default. */
enum class SearchProvider { DUCKDUCKGO, BRAVE, TAVILY, GOOGLE_CUSTOM }
data class SearchProviderConfig(val provider: SearchProvider, val apiKey: String = "", val engineId: String = "")

object SearchProviderStore {
    private const val PROVIDER = "SEARCH_PROVIDER"
    private const val KEY = "SEARCH_PROVIDER_API_KEY"
    private const val ENGINE = "SEARCH_PROVIDER_ENGINE_ID"

    fun current(): SearchProviderConfig = SearchProviderConfig(
        provider = runCatching { SearchProvider.valueOf(KVUtils.getString(PROVIDER, SearchProvider.DUCKDUCKGO.name)) }.getOrDefault(SearchProvider.DUCKDUCKGO),
        apiKey = SecureSecretStore.get(KEY).ifBlank { KVUtils.getString(KEY) }, engineId = KVUtils.getString(ENGINE),
    )
    fun save(config: SearchProviderConfig) {
        KVUtils.putString(PROVIDER, config.provider.name)
        if (config.apiKey.isNotBlank()) SecureSecretStore.put(KEY, config.apiKey)
        KVUtils.putString(ENGINE, config.engineId)
    }
}
