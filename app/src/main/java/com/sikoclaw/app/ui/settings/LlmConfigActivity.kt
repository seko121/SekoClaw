// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package com.sikoclaw.app.ui.settings

import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.sikoclaw.app.ClawApplication
import com.sikoclaw.app.R
import com.sikoclaw.app.agent.CloudModel
import com.sikoclaw.app.agent.CloudProvider
import com.sikoclaw.app.agent.ModelPricing
import com.sikoclaw.app.agent.llm.ActiveModelMode
import com.sikoclaw.app.agent.llm.LocalModelManager
import com.sikoclaw.app.agent.llm.ModelConfigRepository
import com.sikoclaw.app.base.BaseActivity
import com.sikoclaw.app.ui.chat.ThemeManager
import com.sikoclaw.app.utils.KVUtils
import com.sikoclaw.app.widget.CommonToolbar
import com.sikoclaw.app.widget.KButton
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.max

class LlmConfigActivity : BaseActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private var isDownloading = false
    private var selectedProvider: CloudProvider = CloudProvider.OPENAI
    private var selectedModelId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_llm_config)

        // Apply dark theme from ThemeManager to match rest of app
        val tc = ThemeManager.getColors()
        window.statusBarColor = tc.toolbarBg
        window.decorView.setBackgroundColor(tc.bg)
        val contentFrame = findViewById<android.view.ViewGroup>(android.R.id.content)
        contentFrame?.setBackgroundColor(tc.bg)
        (contentFrame?.getChildAt(0) as? View)?.setBackgroundColor(tc.bg)

        findViewById<CommonToolbar>(R.id.toolbar).apply {
            setTitle("Models")
            showBackButton(true) { finish() }
            setBackgroundColor(tc.toolbarBg)
            setTitleColor(tc.aiText)
            findViewById<android.widget.ImageView>(R.id.ivBack)?.setColorFilter(tc.aiText)
        }

        // Include user's custom local model URL (#36) at the tail of the rendered list.
        val models = LocalModelManager.AVAILABLE_MODELS + listOfNotNull(LocalModelManager.customModel())
        val activeModelName = findViewById<TextView>(R.id.tvActiveModelName)
        val activeModelMeta = findViewById<TextView>(R.id.tvActiveModelMeta)
        val activeModelStatus = findViewById<TextView>(R.id.tvActiveModelStatus)
        val defaultLocalName = findViewById<TextView>(R.id.tvDefaultLocalName)
        val defaultLocalMeta = findViewById<TextView>(R.id.tvDefaultLocalMeta)
        val defaultLocalStatus = findViewById<TextView>(R.id.tvDefaultLocalStatus)
        val defaultCloudName = findViewById<TextView>(R.id.tvDefaultCloudName)
        val defaultCloudMeta = findViewById<TextView>(R.id.tvDefaultCloudMeta)
        val defaultCloudStatus = findViewById<TextView>(R.id.tvDefaultCloudStatus)
        val modelList = findViewById<LinearLayout>(R.id.layoutModelList)
        val resolvedConfig = ModelConfigRepository.snapshot()
        val deviceSupport = LocalModelManager.deviceSupport(this)
        val catalog = LocalModelManager.catalog(this).associateBy { it.model.id }

        // Apply theme to active model card text
        activeModelName.setTextColor(tc.aiText)
        activeModelMeta.setTextColor(Color.parseColor("#8b949e"))
        defaultLocalName.setTextColor(tc.aiText)
        defaultLocalMeta.setTextColor(Color.parseColor("#8b949e"))
        defaultCloudName.setTextColor(tc.aiText)
        defaultCloudMeta.setTextColor(Color.parseColor("#8b949e"))

        // Apply theme to all CardViews in XML layout
        val scrollContent = findViewById<LinearLayout>(R.id.layoutModelList)?.parent as? LinearLayout
        if (scrollContent != null) {
            for (i in 0 until scrollContent.childCount) {
                val child = scrollContent.getChildAt(i)
                if (child is TextView && child.id == View.NO_ID) {
                    // Section headers ("Active Model", "Available Models", "Cloud LLM")
                    child.setTextColor(Color.parseColor("#8b949e"))
                }
                if (child is CardView) {
                    child.setCardBackgroundColor(tc.toolbarBg)
                }
            }
        }

        // Active model — show what is ACTUALLY active based on provider
        if (resolvedConfig.activeMode == ActiveModelMode.LOCAL) {
            val activeState = LocalModelManager.resolveActiveModelState(this, resolvedConfig.local)
            activeModelName.text = activeState.displayName
            activeModelMeta.text = activeState.metaText
            activeModelStatus.text = activeState.statusText
            activeModelStatus.setTextColor(
                when (activeState.statusKind) {
                    LocalModelManager.StatusKind.READY -> getColor(R.color.colorSuccessPrimary)
                    LocalModelManager.StatusKind.WARNING -> getColor(R.color.colorWarningPrimary)
                    LocalModelManager.StatusKind.NEUTRAL -> Color.parseColor("#8b949e")
                }
            )
        } else {
            val cloudModel = resolvedConfig.activeCloud.modelName
            if (cloudModel.isNotEmpty()) {
                activeModelName.text = cloudModel
                val providerName = resolvedConfig.activeCloud.provider.displayName
                activeModelMeta.text = "$providerName · Cloud"
                activeModelStatus.text = "● Connected"
                activeModelStatus.setTextColor(getColor(R.color.colorSuccessPrimary))
            } else {
                activeModelName.text = "No model selected"
                activeModelMeta.text = "Configure a cloud model below"
                activeModelStatus.text = "● Not configured"
                activeModelStatus.setTextColor(Color.parseColor("#8b949e"))
            }
        }

        val defaultLocalState = LocalModelManager.resolveActiveModelState(this, resolvedConfig.local)
        defaultLocalName.text = defaultLocalState.displayName
        defaultLocalMeta.text = defaultLocalState.metaText
        defaultLocalStatus.text = defaultLocalState.statusText
        defaultLocalStatus.setTextColor(
            when (defaultLocalState.statusKind) {
                LocalModelManager.StatusKind.READY -> getColor(R.color.colorSuccessPrimary)
                LocalModelManager.StatusKind.WARNING -> getColor(R.color.colorWarningPrimary)
                LocalModelManager.StatusKind.NEUTRAL -> Color.parseColor("#8b949e")
            }
        )

        if (resolvedConfig.defaultCloud.isConfigured) {
            defaultCloudName.text = resolvedConfig.defaultCloud.modelName
            defaultCloudMeta.text = "${resolvedConfig.defaultCloud.provider.displayName} · Cloud"
            defaultCloudStatus.text = "● Ready"
            defaultCloudStatus.setTextColor(getColor(R.color.colorSuccessPrimary))
        } else {
            defaultCloudName.text = "No default cloud model"
            defaultCloudMeta.text = "Configure a cloud model below"
            defaultCloudStatus.text = "● Not configured"
            defaultCloudStatus.setTextColor(Color.parseColor("#8b949e"))
        }

        val activeLocalModelId = if (resolvedConfig.activeMode == ActiveModelMode.LOCAL) resolvedConfig.local.modelId else ""
        val defaultLocalModelId = resolvedConfig.local.modelId
        val configuredBuiltInLocal = LocalModelManager.configuredBuiltInModel(resolvedConfig.local)

        // Build model list
        models.forEach { model ->
            val modelEntry = catalog[model.id]
            val availability = LocalModelManager.availabilityForModel(this, model, resolvedConfig.local)
            val downloaded = availability.isAvailable
            val isActive = model.id == activeLocalModelId
            val isDefaultLocal = model.id == defaultLocalModelId
            val supportedOnDevice = modelEntry?.isSupported == true
            val resolvedLocalPath = when (availability.source) {
                LocalModelManager.AvailabilitySource.MANAGED_DOWNLOAD -> LocalModelManager.getModelPath(this, model)
                LocalModelManager.AvailabilitySource.LINKED_FILE ->
                    if (configuredBuiltInLocal?.id == model.id) resolvedConfig.local.modelPath else null
                LocalModelManager.AvailabilitySource.MISSING -> null
            }

            val card = CardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(6) }
                radius = dp(12).toFloat()
                cardElevation = dp(1).toFloat()
                setCardBackgroundColor(tc.toolbarBg)
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
            }

            // Model info
            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val nameTV = TextView(this).apply {
                text = model.displayName
                textSize = 14f
                setTextColor(tc.aiText)
                if (isActive) setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            info.addView(nameTV)

            val descTV = TextView(this).apply {
                val baseText = "${model.sizeBytes / 1_000_000} MB · ${model.minRamGb}GB+ RAM"
                text = if (supportedOnDevice) baseText else "$baseText · This phone reports ${deviceSupport.deviceRamGb}GB"
                textSize = 12f
                setTextColor(if (supportedOnDevice) Color.parseColor("#8b949e") else getColor(R.color.colorWarningPrimary))
            }
            info.addView(descTV)

            row.addView(info)

            // Action button
            if (downloaded) {
                if (isActive) {
                    val check = TextView(this).apply {
                        text = if (supportedOnDevice) "✓ Active" else "⚠ Active"
                        textSize = 12f
                        setTextColor(if (supportedOnDevice) getColor(R.color.colorSuccessPrimary) else getColor(R.color.colorWarningPrimary))
                    }
                    row.addView(check)
                } else {
                    if (isDefaultLocal) {
                        row.addView(TextView(this).apply {
                            text = "✓ Default"
                            textSize = 12f
                            setTextColor(getColor(R.color.colorSuccessPrimary))
                            setPadding(dp(12), dp(6), dp(12), dp(6))
                        })
                    }
                    if (supportedOnDevice) {
                        val useBtn = TextView(this).apply {
                            text = "Use"
                            textSize = 13f
                            setTextColor(getColor(R.color.colorBrandPrimary))
                            setPadding(dp(12), dp(6), dp(12), dp(6))
                            setOnClickListener {
                                val path = resolvedLocalPath
                                if (path != null) {
                                    // Save as default local model (independent of cloud config)
                                    // Only switch active provider if currently on local tab
                                    val shouldActivateLocal = ModelConfigRepository.isLocalActive() || !KVUtils.hasDefaultCloudModel()
                                    ModelConfigRepository.saveLocalDefault(path, model.id, shouldActivateLocal)
                                    ClawApplication.appViewModelInstance.updateAgentConfig()
                                    ClawApplication.appViewModelInstance.initAgent()
                                    Toast.makeText(this@LlmConfigActivity, "Set default local: ${model.displayName}", Toast.LENGTH_SHORT).show()
                                    recreate()
                                } else {
                                    Toast.makeText(this@LlmConfigActivity, "Model file not found", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        row.addView(useBtn)
                    } else {
                        row.addView(TextView(this).apply {
                            text = "Needs ${model.minRamGb}GB+"
                            textSize = 12f
                            setTextColor(getColor(R.color.colorWarningPrimary))
                            setPadding(dp(12), dp(6), dp(12), dp(6))
                        })
                    }

                    if (availability.source == LocalModelManager.AvailabilitySource.MANAGED_DOWNLOAD) {
                        val delBtn = TextView(this).apply {
                            text = "🗑"
                            textSize = 16f
                            setPadding(dp(8), dp(4), dp(4), dp(4))
                            alpha = 0.4f
                            setOnClickListener {
                                LocalModelManager.deleteModel(this@LlmConfigActivity, model)
                                Toast.makeText(this@LlmConfigActivity, "Deleted ${model.displayName}", Toast.LENGTH_SHORT).show()
                                recreate()
                            }
                        }
                        row.addView(delBtn)
                    }
                }
            } else {
                if (supportedOnDevice) {
                    val dlBtn = TextView(this).apply {
                        text = "↓ Download"
                        textSize = 13f
                        setTextColor(getColor(R.color.colorInfoPrimary))
                        setPadding(dp(12), dp(6), dp(12), dp(6))
                        setOnClickListener {
                            if (isDownloading) {
                                Toast.makeText(this@LlmConfigActivity, "Already downloading", Toast.LENGTH_SHORT).show()
                                return@setOnClickListener
                            }
                            isDownloading = true
                            text = "Downloading..."
                            isEnabled = false

                            executor.submit {
                                LocalModelManager.downloadModel(this@LlmConfigActivity, model, object : LocalModelManager.DownloadCallback {
                                    override fun onProgress(bytesDownloaded: Long, totalBytes: Long, bytesPerSecond: Long) {
                                        val pct = if (totalBytes > 0) (bytesDownloaded * 100 / totalBytes).toInt() else 0
                                        runOnUiThread { text = "$pct%" }
                                    }
                                    override fun onComplete(modelPath: String) {
                                        runOnUiThread {
                                            ModelConfigRepository.saveLocalDefault(
                                                modelPath = modelPath,
                                                modelId = model.id,
                                                activateNow = false
                                            )
                                            isDownloading = false
                                            Toast.makeText(this@LlmConfigActivity, "Downloaded!", Toast.LENGTH_SHORT).show()
                                            recreate()
                                        }
                                    }
                                    override fun onError(error: String) {
                                        runOnUiThread {
                                            isDownloading = false
                                            text = "↓ Download"
                                            isEnabled = true
                                            Toast.makeText(this@LlmConfigActivity, error, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                })
                            }
                        }
                    }
                    row.addView(dlBtn)
                } else {
                    row.addView(TextView(this).apply {
                        text = "Needs ${model.minRamGb}GB+"
                        textSize = 12f
                        setTextColor(getColor(R.color.colorWarningPrimary))
                        setPadding(dp(12), dp(6), dp(12), dp(6))
                    })
                }
            }

            card.addView(row)
            modelList.addView(card)
        }

        // Storage info
        updateStorageInfo()

        // Cloud LLM — Provider tabs + model cards
        setupCloudLlm(tc)
    }

    private fun updateStorageInfo() {
        val models = LocalModelManager.AVAILABLE_MODELS + listOfNotNull(LocalModelManager.customModel())
        var totalSize = 0L
        var count = 0
        models.forEach { model ->
            if (LocalModelManager.isModelDownloaded(this, model)) {
                totalSize += model.sizeBytes
                count++
            }
        }
        val mbUsed = totalSize / 1_000_000
        val allocated = 4000L // 4GB rough estimate
        val pct = (mbUsed * 100 / allocated).toInt().coerceAtMost(100)

        findViewById<TextView>(R.id.tvStorageInfo).text = "$count model${if (count != 1) "s" else ""} · ${mbUsed} MB"
        findViewById<ProgressBar>(R.id.progressStorage).progress = pct
        findViewById<TextView>(R.id.tvStorageDetail).text = "${mbUsed} MB of ${allocated} MB allocated"
    }

    private fun setupCloudLlm(tc: ThemeManager.ChatColors) {
        val tabLayout = findViewById<LinearLayout>(R.id.layoutProviderTabs)
        val modelListLayout = findViewById<LinearLayout>(R.id.layoutCloudModels)
        val layoutBaseUrl = findViewById<View>(R.id.layoutBaseUrl)
        val tvCustomHint = findViewById<TextView>(R.id.tvCustomHint)
        val etApiKey = findViewById<EditText>(R.id.etApiKey)
        val etBaseUrl = findViewById<EditText>(R.id.etBaseUrl)
        val etModelName = findViewById<EditText>(R.id.etModelName)
        val btnBrowseModels = findViewById<TextView>(R.id.btnBrowseModels)
        val tvStatus = findViewById<TextView>(R.id.tvConnectionStatus)
        val btnTest = findViewById<TextView>(R.id.btnTestConnection)
        val btnSave = findViewById<KButton>(R.id.btnSaveCloud)
        val btnClear = findViewById<TextView>(R.id.btnClearApiKey)
        val scrollView = findViewById<ScrollView>(R.id.scrollContent)
        val configSnapshot = ModelConfigRepository.snapshot()
        val cloudSeed = configSnapshot.defaultCloud.let {
            if (it.modelName.isNotEmpty() || it.apiKey.isNotEmpty() || it.baseUrl.isNotEmpty()) it else configSnapshot.activeCloud
        }

        installKeyboardAwareScrolling(scrollView, listOf(etApiKey, etBaseUrl, etModelName))

        // Clear API key button
        btnClear.setOnClickListener {
            etApiKey.setText("")
            KVUtils.setApiKeyForProvider(selectedProvider.name, "")
            KVUtils.setLlmApiKey("")
            Toast.makeText(this, "API key cleared", Toast.LENGTH_SHORT).show()
        }

        // Determine current provider from saved config
        selectedProvider = CloudProvider.fromName(cloudSeed.providerName)
        selectedModelId = cloudSeed.modelName
        etApiKey.setText(cloudSeed.apiKey)

        // Build provider tabs
        val tabViews = mutableMapOf<CloudProvider, TextView>()
        CloudProvider.entries.forEach { provider ->
            val tab = TextView(this).apply {
                text = provider.displayName
                textSize = 13f
                setPadding(dp(14), dp(6), dp(14), dp(6))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                // Click handler set below after renderModels is defined
            }
            tabViews[provider] = tab
            tabLayout.addView(tab)
        }

        fun updateTabStyles() {
            tabViews.forEach { (provider, tab) ->
                if (provider == selectedProvider) {
                    tab.setTextColor(Color.WHITE)
                    tab.background = GradientDrawable().apply {
                        cornerRadius = dp(16).toFloat()
                        setColor(getColor(R.color.colorBrandPrimary))
                    }
                } else {
                    tab.setTextColor(Color.parseColor("#8b949e"))
                    tab.background = null
                }
            }
        }
        updateTabStyles()

        // Render model cards for current provider
        fun renderModels() {
            modelListLayout.removeAllViews()
            val isCustom = selectedProvider == CloudProvider.CUSTOM
            layoutBaseUrl.visibility = if (isCustom) View.VISIBLE else View.GONE
            tvCustomHint?.visibility = if (isCustom) View.VISIBLE else View.GONE

            if (isCustom) {
                etBaseUrl.setText(cloudSeed.baseUrl)
                etModelName.setText(selectedModelId)
                return
            }

            selectedProvider.models.forEach { model ->
                val isSelected = model.id == selectedModelId
                val card = CardView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(6) }
                    radius = dp(10).toFloat()
                    cardElevation = dp(1).toFloat()
                    setCardBackgroundColor(if (isSelected) Color.parseColor("#13233A") else tc.toolbarBg)
                    if (isSelected) {
                        // Blue brand border for selected
                        setContentPadding(dp(2), dp(2), dp(2), dp(2))
                    }
                    setOnClickListener {
                        selectedModelId = model.id
                        renderModels()
                    }
                }

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                }

                // Radio dot
                val dot = TextView(this).apply {
                    text = if (isSelected) "◉" else "○"
                    textSize = 16f
                    setTextColor(if (isSelected) getColor(R.color.colorBrandPrimary) else Color.parseColor("#8b949e"))
                    setPadding(0, 0, dp(10), 0)
                }
                row.addView(dot)

                // Model info
                val info = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val nameRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                nameRow.addView(TextView(this).apply {
                    text = model.displayName
                    textSize = 14f
                    setTextColor(tc.aiText)
                    if (isSelected) setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                // Recommended badge
                if (model.recommended) {
                    nameRow.addView(TextView(this).apply {
                        text = " ⚡"
                        textSize = 12f
                    })
                }
                info.addView(nameRow)

                // Price + tier
                info.addView(TextView(this).apply {
                    text = "${model.tier.stars} ${model.tier.label} · \$${model.inputPricePerM} / \$${model.outputPricePerM} per 1M"
                    textSize = 11f
                    setTextColor(Color.parseColor("#8b949e"))
                })
                row.addView(info)

                card.addView(row)
                modelListLayout.addView(card)
            }
        }
        renderModels()

        // Provider tab switch — load per-provider saved API key
        fun switchProvider(provider: CloudProvider, colors: ThemeManager.ChatColors) {
            selectedProvider = provider
            selectedModelId = when {
                provider == CloudProvider.CUSTOM -> cloudSeed.modelName
                provider == cloudSeed.provider && provider.models.any { it.id == cloudSeed.modelName } -> cloudSeed.modelName
                else -> provider.models.firstOrNull()?.id ?: ""
            }
            updateTabStyles()
            renderModels()
            tvStatus.visibility = View.GONE
            val savedKey = KVUtils.getApiKeyForProvider(provider.name)
            etApiKey.setText(savedKey)
        }
        // Re-assign click listeners with the inner function
        tabViews.forEach { (provider, tab) ->
            tab.setOnClickListener { switchProvider(provider, tc) }
        }

        // Discover models exposed by an OpenAI-compatible custom endpoint.
        btnBrowseModels.setOnClickListener {
            val baseUrl = etBaseUrl.text.toString().trim()
            val apiKey = etApiKey.text.toString().trim()
            if (baseUrl.isEmpty()) {
                etBaseUrl.error = "Enter the Base URL first"
                etBaseUrl.requestFocus()
                return@setOnClickListener
            }

            btnBrowseModels.isEnabled = false
            btnBrowseModels.alpha = 0.55f
            btnBrowseModels.text = "Loading models..."
            tvStatus.visibility = View.VISIBLE
            tvStatus.text = "Connecting to ${modelsEndpoint(baseUrl)}"
            tvStatus.setTextColor(Color.parseColor("#8b949e"))

            executor.submit {
                try {
                    val models = fetchAvailableModels(baseUrl, apiKey)
                    runOnUiThread {
                        btnBrowseModels.isEnabled = true
                        btnBrowseModels.alpha = 1f
                        btnBrowseModels.text = "Browse Available Models"
                        tvStatus.text = "Found ${models.size} models"
                        tvStatus.setTextColor(getColor(R.color.colorSuccessPrimary))
                        showModelPicker(models, etModelName)
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        btnBrowseModels.isEnabled = true
                        btnBrowseModels.alpha = 1f
                        btnBrowseModels.text = "Retry Loading Models"
                        tvStatus.text = "Could not load models: ${e.message ?: "Unknown error"}"
                        tvStatus.setTextColor(getColor(R.color.colorErrorPrimary))
                    }
                }
            }
        }

        // Test Connection
        btnTest.setOnClickListener {
            val apiKey = etApiKey.text.toString().trim()
            if (apiKey.isEmpty()) {
                Toast.makeText(this, "Enter API Key first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            tvStatus.visibility = View.VISIBLE
            tvStatus.text = "Testing..."
            tvStatus.setTextColor(Color.parseColor("#8b949e"))

            executor.submit {
                try {
                    val baseUrl = if (selectedProvider == CloudProvider.CUSTOM) etBaseUrl.text.toString().trim()
                        else selectedProvider.defaultBaseUrl
                    val modelId = if (selectedProvider == CloudProvider.CUSTOM) etModelName.text.toString().trim()
                        else selectedModelId
                    // Quick test: just validate the key format
                    if (apiKey.length < 10) throw RuntimeException("API key too short")
                    if (modelId.isEmpty()) throw RuntimeException("No model selected")
                    runOnUiThread {
                        tvStatus.text = "✓ Ready to save"
                        tvStatus.setTextColor(getColor(R.color.colorSuccessPrimary))
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        tvStatus.text = "✗ ${e.message}"
                        tvStatus.setTextColor(getColor(R.color.colorErrorPrimary))
                    }
                }
            }
        }

        // Save & Activate
        btnSave.setOnClickListener {
            val apiKey = etApiKey.text.toString().trim()
            if (apiKey.isEmpty()) {
                Toast.makeText(this, "Enter API Key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val isCustom = selectedProvider == CloudProvider.CUSTOM
            val baseUrl = if (isCustom) etBaseUrl.text.toString().trim() else selectedProvider.defaultBaseUrl
            val modelId = if (isCustom) etModelName.text.toString().trim() else selectedModelId

            if (modelId.isEmpty()) {
                Toast.makeText(this, "Select a model", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Save as default cloud model (independent of local config)
            ModelConfigRepository.saveCloudDefault(
                providerName = selectedProvider.name,
                modelId = modelId,
                baseUrl = baseUrl,
                apiKey = apiKey,
                activateNow = !ModelConfigRepository.isLocalActive()
            )
            ClawApplication.appViewModelInstance.updateAgentConfig()
            ClawApplication.appViewModelInstance.initAgent()
            ClawApplication.appViewModelInstance.afterInit()
            Toast.makeText(this, "Saved cloud default: $modelId", Toast.LENGTH_SHORT).show()
            finish()
        }

        // Active model card is already set in onCreate based on actual provider
    }

    private fun modelsEndpoint(baseUrl: String): String {
        val normalized = baseUrl.trim().trimEnd('/')
        return if (normalized.endsWith("/models", ignoreCase = true)) normalized else "$normalized/models"
    }

    private fun fetchAvailableModels(baseUrl: String, apiKey: String): List<String> {
        val requestBuilder = Request.Builder()
            .url(modelsEndpoint(baseUrl))
            .get()
            .header("Accept", "application/json")
        if (apiKey.isNotEmpty()) requestBuilder.header("Authorization", "Bearer $apiKey")

        val client = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()

        client.newCall(requestBuilder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching {
                    JSONObject(raw).optJSONObject("error")?.optString("message")
                }.getOrNull().orEmpty()
                throw IllegalStateException(detail.ifEmpty { "HTTP ${response.code}" })
            }

            val json = JSONObject(raw)
            val array = json.optJSONArray("data") ?: json.optJSONArray("models")
                ?: throw IllegalStateException("The endpoint did not return a model list")
            val models = buildList {
                for (index in 0 until array.length()) {
                    val item = array.opt(index)
                    val id = when (item) {
                        is JSONObject -> item.optString("id").ifEmpty { item.optString("name") }
                        is String -> item
                        else -> ""
                    }.trim()
                    if (id.isNotEmpty()) add(id)
                }
            }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
            if (models.isEmpty()) throw IllegalStateException("No models were returned")
            return models
        }
    }

    private fun showModelPicker(models: List<String>, target: EditText) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val search = EditText(this).apply {
            hint = "Search ${models.size} models"
            isSingleLine = true
            setPadding(dp(12), 0, dp(12), 0)
            minHeight = dp(48)
            background = getDrawable(R.drawable.bg_edit_text)
        }
        val list = ListView(this).apply {
            dividerHeight = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(420)
            ).apply { topMargin = dp(8) }
        }
        val visibleModels = models.toMutableList()
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, visibleModels)
        list.adapter = adapter
        container.addView(search)
        container.addView(list)

        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Choose a model")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .create()

        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim().orEmpty()
                visibleModels.clear()
                visibleModels.addAll(if (query.isEmpty()) models else models.filter { it.contains(query, true) })
                adapter.notifyDataSetChanged()
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        list.setOnItemClickListener { _, _, position, _ ->
            val model = visibleModels[position]
            target.setText(model)
            target.setSelection(model.length)
            selectedModelId = model
            dialog.dismiss()
            Toast.makeText(this, "Selected $model", Toast.LENGTH_SHORT).show()
        }
        dialog.setOnShowListener { search.requestFocus() }
        dialog.show()
    }

    /**
     * Keep focused fields visible above the IME on edge-to-edge layouts.
     * `adjustResize` alone is not reliable here because the ScrollView content
     * still needs extra bottom inset + explicit scroll after the keyboard opens.
     */
    private fun installKeyboardAwareScrolling(scrollView: ScrollView, fields: List<EditText>) {
        val baseBottomPadding = scrollView.paddingBottom

        val focusListener = View.OnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                scrollView.postDelayed({ scrollFieldIntoView(scrollView, view) }, 180)
            }
        }
        fields.forEach { it.onFocusChangeListener = focusListener }

        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { view, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = baseBottomPadding + max(imeInsets.bottom, systemInsets.bottom))

            if (imeInsets.bottom > 0) {
                val focused = currentFocus
                if (focused is EditText && fields.contains(focused)) {
                    view.post { scrollFieldIntoView(scrollView, focused) }
                }
            }
            insets
        }
        ViewCompat.requestApplyInsets(scrollView)
    }

    private fun scrollFieldIntoView(scrollView: ScrollView, field: View) {
        val rect = Rect()
        field.getDrawingRect(rect)
        scrollView.offsetDescendantRectToMyCoords(field, rect)

        val margin = dp(16)
        val visibleTop = scrollView.scrollY + margin
        val visibleBottom = scrollView.scrollY + scrollView.height - scrollView.paddingBottom - margin

        when {
            rect.top < visibleTop -> {
                scrollView.smoothScrollTo(0, (rect.top - margin).coerceAtLeast(0))
            }
            rect.bottom > visibleBottom -> {
                val targetScroll = scrollView.scrollY + (rect.bottom - visibleBottom) + margin
                scrollView.smoothScrollTo(0, targetScroll.coerceAtLeast(0))
            }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
