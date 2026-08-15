package com.sikoclaw.app.agent.llm

fun filterProviderModels(models: List<ApiModelConfig>, query: String, favoritesOnly: Boolean): List<ApiModelConfig> {
    val needle = query.trim()
    return models.asSequence()
        .filter { !favoritesOnly || it.isFavorite }
        .filter { needle.isEmpty() || it.displayName.contains(needle, ignoreCase = true) || it.apiModelName.contains(needle, ignoreCase = true) }
        .toList()
}
