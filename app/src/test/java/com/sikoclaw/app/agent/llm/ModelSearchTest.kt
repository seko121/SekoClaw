package com.sikoclaw.app.agent.llm

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelSearchTest {
    @Test fun `search is instant case insensitive and handles large catalogs`() {
        val models = (0 until 150).map { ApiModelConfig(providerId = "p", displayName = "Model $it", apiModelName = "vendor/model-$it", isFavorite = it % 10 == 0) }
        assertEquals(61, filterProviderModels(models, "MODEL 1", false).size)
        assertEquals(6, filterProviderModels(models, "vendor/model-1", true).size)
    }
}
