package com.sikoclaw.app.agent.llm

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import com.sikoclaw.app.ClawApplication
import com.sikoclaw.app.utils.XLog
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class ApiProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val baseUrl: String,
    val type: String = "OpenAI compatible",
    val headers: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
    val isDefault: Boolean = false,
    val catalogId: String = "openai-compatible",
    val protocol: String = "OPENAI_COMPATIBLE",
    val modelsPath: String? = "/models",
    val chatPath: String = "/chat/completions",
    val requiresApiKey: Boolean = true,
)

data class ApiModelConfig(
    val id: String = UUID.randomUUID().toString(),
    val providerId: String,
    val displayName: String,
    val apiModelName: String,
    val supportsTools: Boolean = true,
    val supportsVision: Boolean = false,
    val supportsStreaming: Boolean = true,
    val timeoutSeconds: Int = 120,
    val maxRetries: Int = 1,
    val enabled: Boolean = true,
    val isPrimary: Boolean = false,
    val contextLength: Long? = null,
    val isFree: Boolean = false,
    val isFavorite: Boolean = false,
    val isManual: Boolean = true,
)

data class MultiProviderState(
    val providers: List<ApiProviderConfig> = emptyList(),
    val models: List<ApiModelConfig> = emptyList(),
    val routing: List<String> = emptyList(),
    val activeModelId: String? = null,
    val visionModelId: String? = null,
)

data class ProviderSaveResult(val success: Boolean, val message: String)

/** Multiple providers/models with API keys kept separately in Android Keystore. */
object MultiProviderStore {
    private const val PREFS = "siko_multi_provider"
    private const val STATE = "state_json"
    private val app get() = ClawApplication.instance
    private val gson = Gson()
    @Volatile private var runtimeModelId: String? = null
    private val _stateFlow by lazy { MutableStateFlow(loadState()) }
    val stateFlow get() = _stateFlow.asStateFlow()

    private const val BUILTIN_FREE_PROVIDER_ID = "octobot-free-provider"
    private const val BUILTIN_FREE_FAST_ID = "octobot-free-fast"
    private const val BUILTIN_FREE_EXPERT_ID = "octobot-free-expert"

    private fun loadState(): MultiProviderState = runCatching {
        gson.fromJson(app.getSharedPreferences(PREFS, 0).getString(STATE, null), MultiProviderState::class.java)
    }.getOrNull()?.let { loaded ->
        val routed = loaded.routing.toSet()
        loaded.copy(models = loaded.models.map { model ->
            if (model.id in routed || model.isPrimary) model.copy(isFavorite = true) else model
        })
    } ?: MultiProviderState()

    @Synchronized fun state(): MultiProviderState = _stateFlow.value

    @Synchronized private fun save(state: MultiProviderState): Boolean {
        val json = gson.toJson(state)
        val committed = app.getSharedPreferences(PREFS, 0).edit().putString(STATE, json).commit()
        val verified = committed && app.getSharedPreferences(PREFS, 0).getString(STATE, null) == json
        if (verified) _stateFlow.value = state
        return verified
    }

    fun apiKey(providerId: String): String = SecureSecretStore.get("provider_$providerId")

    @Synchronized fun saveProvider(provider: ApiProviderConfig, apiKey: String? = null): ProviderSaveResult {
        return runCatching {
            val normalized = provider.copy(name = provider.name.trim(), baseUrl = provider.baseUrl.trim().trimEnd('/'))
            require(normalized.name.isNotBlank()) { "Provider name is required" }
            require(normalized.baseUrl.startsWith("https://") || normalized.baseUrl.startsWith("http://")) { "Enter a valid Base URL" }
            if (!apiKey.isNullOrBlank() && !SecureSecretStore.put("provider_${normalized.id}", apiKey)) {
                error("API key could not be saved securely on this device")
            }
            val old = state()
            val providers = (old.providers.filterNot { it.id == normalized.id } + normalized).map {
                if (normalized.isDefault && it.id != normalized.id) it.copy(isDefault = false) else it
            }
            check(save(old.copy(providers = providers))) { "Provider storage write could not be verified" }
            val persisted = loadState().providers.firstOrNull { it.id == normalized.id }
            check(persisted == normalized) { "Provider verification failed after saving" }
            if (!apiKey.isNullOrBlank()) check(apiKey(normalized.id) == apiKey) { "Secure API key verification failed" }
            seedCatalogModels(normalized)
            XLog.i("MultiProviderStore", "Provider saved and verified id=${normalized.id} protocol=${normalized.protocol}")
            ProviderSaveResult(true, "Provider saved")
        }.getOrElse { error ->
            XLog.e("MultiProviderStore", "Provider save failed id=${provider.id}: ${error.javaClass.simpleName}: ${error.message}")
            ProviderSaveResult(false, error.message ?: "Provider could not be saved")
        }
    }

    /**
     * Kai's Free service is a built-in runtime, not a user-created API provider.
     * Keep it available on a fresh install so chat can never fall into a false
     * "No model selected" state before the owner adds a paid provider.
     */
    @Synchronized fun ensureBuiltInFreeModel() {
        val old = state()
        val definition = com.sikoclaw.app.agent.llm.kai.KaiServiceRegistry.byId("free") ?: return
        val provider = old.providers.firstOrNull { it.catalogId == "free" } ?: ApiProviderConfig(
            id = BUILTIN_FREE_PROVIDER_ID,
            name = definition.displayName,
            baseUrl = definition.baseUrl,
            type = definition.protocol.name,
            enabled = true,
            isDefault = old.routing.isEmpty(),
            catalogId = definition.id,
            protocol = definition.protocol.name,
            modelsPath = definition.modelsPath,
            chatPath = definition.chatPath,
            requiresApiKey = false,
        )
        val existingByApiId = old.models.filter { it.providerId == provider.id }.associateBy { it.apiModelName }
        val builtIns = definition.defaultModels.mapIndexed { index, apiId ->
            existingByApiId[apiId] ?: ApiModelConfig(
                id = if (index == 0) BUILTIN_FREE_FAST_ID else BUILTIN_FREE_EXPERT_ID,
                providerId = provider.id,
                displayName = if (apiId == "fast") "Free · Fast" else "Free · Expert",
                apiModelName = apiId,
                supportsTools = true,
                supportsVision = false,
                supportsStreaming = true,
                enabled = true,
                isFree = true,
                isFavorite = index == 0 && old.routing.isEmpty(),
                isPrimary = index == 0 && old.routing.isEmpty(),
                isManual = false,
            )
        }
        val models = old.models.filterNot { model ->
            model.providerId == provider.id && builtIns.any { it.apiModelName == model.apiModelName }
        } + builtIns
        val routing = if (old.routing.isEmpty()) listOf(builtIns.first().id) else old.routing
        val active = old.activeModelId ?: routing.firstOrNull()
        val desired = old.copy(
            providers = old.providers.filterNot { it.id == provider.id } + provider,
            models = models.map { it.copy(isPrimary = it.id == active) },
            routing = routing,
            activeModelId = active,
        )
        if (desired != old) check(save(desired)) { "Built-in Free model could not be persisted" }
        active?.let { modelId ->
            models.firstOrNull { it.id == modelId }?.let { model ->
                val activeProvider = (old.providers + provider).firstOrNull { it.id == model.providerId }
                if (activeProvider != null) syncLegacyRuntimeKeys(model, activeProvider)
            }
        }
    }

    @Synchronized private fun seedCatalogModels(provider: ApiProviderConfig) {
        val defaults = com.sikoclaw.app.agent.llm.kai.KaiServiceRegistry.byId(provider.catalogId)?.defaultModels.orEmpty()
        if (defaults.isEmpty()) return
        val before = state()
        defaults.forEachIndexed { index, apiId ->
            if (state().models.none { it.providerId == provider.id && it.apiModelName == apiId }) {
                val shouldActivate = before.routing.isEmpty() && index == 0
                saveModel(ApiModelConfig(
                    providerId = provider.id,
                    displayName = apiId,
                    apiModelName = apiId,
                    supportsTools = true,
                    supportsVision = provider.catalogId != "free",
                    isFree = provider.catalogId == "free",
                    isFavorite = shouldActivate,
                    isPrimary = shouldActivate,
                    isManual = false,
                ))
                if (shouldActivate) setFavorite(state().models.last { it.providerId == provider.id && it.apiModelName == apiId }.id, true)
            }
        }
    }

    @Synchronized fun duplicateProvider(id: String): ApiProviderConfig? = state().providers.firstOrNull { it.id == id }?.let {
        it.copy(id = UUID.randomUUID().toString(), name = "${it.name} copy", isDefault = false).also { copy -> saveProvider(copy, apiKey(id)) }
    }

    @Synchronized fun deleteProvider(id: String) {
        val old = state(); val removedModels = old.models.filter { it.providerId == id }.map { it.id }.toSet()
        save(old.copy(providers = old.providers.filterNot { it.id == id }, models = old.models.filterNot { it.providerId == id }, routing = old.routing.filterNot { it in removedModels }))
        SecureSecretStore.remove("provider_$id")
    }

    @Synchronized fun saveModel(model: ApiModelConfig) {
        val old = state(); val models = (old.models.filterNot { it.id == model.id } + model).map { if (model.isPrimary && it.id != model.id) it.copy(isPrimary = false) else it }
        val existingRouting = old.routing.distinct().filter { id -> models.any { it.id == id } }
        val routing = if (model.isPrimary) listOf(model.id) + existingRouting.filterNot { it == model.id } else existingRouting
        val active = if (model.isPrimary) model.id else old.activeModelId
        save(old.copy(models = models, routing = routing, activeModelId = active))
        if (model.isPrimary) {
            runtimeModelId = model.id
            old.providers.firstOrNull { it.id == model.providerId }?.let { provider -> syncLegacyRuntimeKeys(model, provider) }
        }
    }

    private fun syncLegacyRuntimeKeys(model: ApiModelConfig, provider: ApiProviderConfig) {
        com.sikoclaw.app.utils.KVUtils.setLlmProvider(if (provider.protocol == "ANTHROPIC") "ANTHROPIC" else "CUSTOM")
        com.sikoclaw.app.utils.KVUtils.setLlmModelName(model.apiModelName)
        com.sikoclaw.app.utils.KVUtils.setLlmBaseUrl(provider.baseUrl)
        com.sikoclaw.app.utils.KVUtils.setDefaultCloudProvider(provider.name)
        com.sikoclaw.app.utils.KVUtils.setDefaultCloudModel(model.apiModelName)
        com.sikoclaw.app.utils.KVUtils.setDefaultCloudBaseUrl(provider.baseUrl)
    }

    @Synchronized fun deleteModel(id: String) { val old = state(); save(old.copy(models = old.models.filterNot { it.id == id }, routing = old.routing.filterNot { it == id }, activeModelId = old.activeModelId.takeUnless { it == id })) }
    @Synchronized fun setRouting(ids: List<String>) {
        val old = state()
        val routing = ids.distinct().filter { id -> old.models.any { it.id == id && it.isFavorite } }
        val active = routing.firstOrNull()
        save(old.copy(routing = routing, activeModelId = active, models = old.models.map { it.copy(isPrimary = it.id == active) }))
        active?.let(::activateModel)
    }

    @Synchronized fun setFavorite(modelId: String, favorite: Boolean): Boolean {
        val old = state()
        if (old.models.none { it.id == modelId }) return false
        if (!favorite && old.routing.size == 1 && old.routing.singleOrNull() == modelId) return false
        val routing = if (favorite) (old.routing + modelId).distinct() else old.routing.filterNot { it == modelId }
        val active = routing.firstOrNull()
        save(old.copy(
            models = old.models.map { model -> model.copy(isFavorite = if (model.id == modelId) favorite else model.isFavorite, isPrimary = model.id == active) },
            routing = routing,
            activeModelId = active,
        ))
        active?.let(::activateModel)
        return true
    }

    fun enabledRouting(): List<Pair<ApiModelConfig, ApiProviderConfig>> {
        val current = state(); val providers = current.providers.associateBy { it.id }
        val models = current.models.associateBy { it.id }
        val ordered = (current.routing + current.models.sortedByDescending { it.isPrimary }.map { it.id }).distinct()
        return ordered.mapNotNull { models[it] }.filter { it.enabled }.mapNotNull { model -> providers[model.providerId]?.takeIf { it.enabled }?.let { model to it } }
    }

    fun selectRuntimeModel(modelId: String?) { runtimeModelId = modelId }

    @Synchronized fun setVisionModel(modelId: String?) {
        val old = state()
        require(modelId == null || old.models.any { it.id == modelId && it.supportsVision }) { "Choose a model that supports vision" }
        check(save(old.copy(visionModelId = modelId))) { "Vision model could not be saved" }
    }

    fun visionRoute(): Pair<ApiModelConfig, ApiProviderConfig>? {
        val current = state()
        val model = current.models.firstOrNull { it.id == current.visionModelId && it.enabled && it.supportsVision } ?: return null
        val provider = current.providers.firstOrNull { it.id == model.providerId && it.enabled } ?: return null
        return model to provider
    }

    @Synchronized fun activateModel(modelId: String) {
        val old = state()
        val model = old.models.firstOrNull { it.id == modelId } ?: return
        saveModel(model.copy(isPrimary = true))
    }

    /** One-time, non-destructive conversion of Siko's legacy single cloud config. */
    @Synchronized fun migrateLegacyIfNeeded() {
        if (app.getSharedPreferences(PREFS, 0).getBoolean("kai_services_migrated", false)) return
        if (state().providers.isEmpty()) {
            val providerName = com.sikoclaw.app.utils.KVUtils.getDefaultCloudProvider()
                .ifBlank { com.sikoclaw.app.utils.KVUtils.getLlmProvider() }
                .ifBlank { "CUSTOM" }
            val baseUrl = com.sikoclaw.app.utils.KVUtils.getDefaultCloudBaseUrl()
                .ifBlank { com.sikoclaw.app.utils.KVUtils.getLlmBaseUrl() }
            val modelId = com.sikoclaw.app.utils.KVUtils.getDefaultCloudModel()
                .ifBlank { com.sikoclaw.app.utils.KVUtils.getLlmModelName() }
            val apiKey = com.sikoclaw.app.utils.KVUtils.getApiKeyForProvider(providerName)
                .ifBlank { com.sikoclaw.app.utils.KVUtils.getLlmApiKey() }
            if (baseUrl.isNotBlank() && modelId.isNotBlank()) {
                val catalog = com.sikoclaw.app.agent.llm.kai.KaiServiceRegistry.all.firstOrNull {
                    it.id.equals(providerName, true) || it.displayName.equals(providerName, true)
                }
                val provider = ApiProviderConfig(
                    name = catalog?.displayName ?: providerName,
                    baseUrl = baseUrl.trimEnd('/'),
                    type = catalog?.protocol?.name ?: "OPENAI_COMPATIBLE",
                    isDefault = true,
                    catalogId = catalog?.id ?: "openai-compatible",
                    protocol = catalog?.protocol?.name ?: "OPENAI_COMPATIBLE",
                    modelsPath = catalog?.modelsPath ?: "/models",
                    chatPath = catalog?.chatPath ?: "/chat/completions",
                    requiresApiKey = catalog?.requiresApiKey ?: true,
                )
                saveProvider(provider, apiKey)
                saveModel(ApiModelConfig(providerId = provider.id, displayName = modelId, apiModelName = modelId, isPrimary = true))
                if (apiKey.isNotBlank()) {
                    com.sikoclaw.app.utils.KVUtils.setLlmApiKey("")
                    com.sikoclaw.app.utils.KVUtils.setApiKeyForProvider(providerName, "")
                }
            }
        }
        app.getSharedPreferences(PREFS, 0).edit()
            .putString("legacy_backup_provider", com.sikoclaw.app.utils.KVUtils.getLlmProvider())
            .putString("legacy_backup_base_url", com.sikoclaw.app.utils.KVUtils.getLlmBaseUrl())
            .putString("legacy_backup_model", com.sikoclaw.app.utils.KVUtils.getLlmModelName())
            .putBoolean("kai_services_migrated", true)
            .commit()
    }
    fun selectedRouting(): List<Pair<ApiModelConfig, ApiProviderConfig>> {
        val routes = enabledRouting(); val selected = runtimeModelId ?: state().activeModelId ?: return routes
        return routes.sortedBy { if (it.first.id == selected) 0 else 1 }
    }

    fun shouldFallback(error: Throwable, toolAlreadyExecuted: Boolean, cancelled: Boolean, hasOtherProvider: Boolean = false): Boolean {
        if (toolAlreadyExecuted || cancelled) return false
        val text = (error.message ?: "").lowercase()
        if (listOf("invalid request", "400", "content policy", "safety").any(text::contains)) return false
        if (listOf("invalid api key", "unauthorized", "401").any(text::contains)) return hasOtherProvider
        return listOf("timeout", "timed out", "429", "500", "502", "503", "504", "network", "connection", "unavailable", "dns").any(text::contains)
    }
}

internal object SecureSecretStore {
    private const val ALIAS = "siko_provider_keys_v1"
    private const val PREFS = "siko_encrypted_secrets"
    private val app get() = ClawApplication.instance

    fun put(key: String, value: String): Boolean {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val payload = Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        return app.getSharedPreferences(PREFS, 0).edit().putString(key, payload).commit() && get(key) == value
    }
    fun get(key: String): String = runCatching {
        val bytes = Base64.decode(app.getSharedPreferences(PREFS, 0).getString(key, ""), Base64.NO_WRAP)
        if (bytes.size <= 12) return@runCatching ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }.getOrDefault("")
    fun remove(key: String) { app.getSharedPreferences(PREFS, 0).edit().remove(key).apply() }
    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
}
