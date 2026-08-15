package com.sikoclaw.app.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sikoclaw.app.agent.llm.ApiModelConfig
import com.sikoclaw.app.agent.llm.ApiProviderConfig
import com.sikoclaw.app.agent.llm.MultiProviderStore
import com.sikoclaw.app.agent.llm.LocalModelManager
import com.sikoclaw.app.agent.llm.MediaProviderConfig
import com.sikoclaw.app.agent.llm.MediaProviderKind
import com.sikoclaw.app.agent.llm.MediaProviderStore
import com.sikoclaw.app.agent.llm.kai.KaiServiceRegistry
import com.sikoclaw.app.agent.llm.kai.KaiProviderGateway
import com.sikoclaw.app.tool.web.SearchProvider
import com.sikoclaw.app.tool.web.SearchProviderConfig
import com.sikoclaw.app.tool.web.SearchProviderStore
import com.sikoclaw.app.ui.chat.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.math.abs

class ProviderManagementActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val palette = ThemeManager.getColors()
        val dark = ThemeManager.isDark()
        WindowCompat.getInsetsController(window, window.decorView).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark }
        setContent {
            val scheme = if (dark) darkColorScheme(
                primary = Color(palette.sendColor), onPrimary = Color.White,
                background = Color(palette.bg), onBackground = Color(palette.aiText),
                surface = Color(palette.aiBubble), onSurface = Color(palette.aiText),
                surfaceVariant = Color(palette.aiBubbleBorder), onSurfaceVariant = Color(0xFFB0BAC8),
                outline = Color(palette.inputBorder),
            ) else lightColorScheme(
                primary = Color(palette.sendColor), onPrimary = Color.White,
                background = Color(palette.bg), onBackground = Color(palette.aiText),
                surface = Color(palette.aiBubble), onSurface = Color(palette.aiText),
                surfaceVariant = Color(palette.aiBubbleBorder), onSurfaceVariant = Color(0xFF475569),
                outline = Color(palette.inputBorder),
            )
            MaterialTheme(
                colorScheme = scheme,
            ) { ProviderManagementScreen { finish() } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderManagementScreen(onBack: () -> Unit) {
    val palette = remember { ThemeManager.getColors() }
    val bg = Color(palette.bg); val surface = Color(palette.aiBubble); val accent = Color(palette.sendColor)
    val state by MultiProviderStore.stateFlow.collectAsState()
    var section by rememberSaveable { mutableIntStateOf(0) }
    var editingProvider by remember { mutableStateOf<ApiProviderConfig?>(null) }
    var providerDialog by remember { mutableStateOf(false) }
    var editingModel by remember { mutableStateOf<ApiModelConfig?>(null) }
    var modelProviderId by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun refresh() = Unit

    Scaffold(
        containerColor = bg,
        topBar = { CenterAlignedTopAppBar(title = { Text("Services") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } }, colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color(palette.toolbarBg))) },
        floatingActionButton = { if (section == 0) FloatingActionButton({ editingProvider = null; providerDialog = true }, containerColor = accent) { Icon(Icons.Outlined.Add, "Add provider") } },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TabRow(section, containerColor = surface, contentColor = accent) {
                Tab(section == 0, { section = 0 }, text = { Text("Providers") })
                Tab(section == 1, { section = 1 }, text = { Text("Local") })
                Tab(section == 2, { section = 2 }, text = { Text("Routing") })
                Tab(section == 3, { section = 3 }, text = { Text("Media") })
                Tab(section == 4, { section = 4 }, text = { Text("Search") })
            }
            if (section == 0) LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                notice?.let { item { Text(it, color = accent, modifier = Modifier.padding(horizontal = 4.dp)) } }
                item {
                    val vision = state.models.firstOrNull { it.id == state.visionModelId }
                    Card(colors = CardDefaults.cardColors(containerColor = surface), border = BorderStroke(1.dp, Color(palette.inputBorder))) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Visibility, null, tint = accent)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Vision model", style = MaterialTheme.typography.titleSmall)
                                Text(vision?.displayName ?: "Not selected · choose the eye icon on a vision-capable model", style = MaterialTheme.typography.bodySmall, color = Color(palette.toolDefault))
                            }
                        }
                    }
                }
                if (state.providers.isEmpty()) item { EmptyProviderCard(surface, accent) { providerDialog = true } }
                state.providers.forEach { provider -> item(key = provider.id) {
                    var expanded by rememberSaveable(provider.id) { mutableStateOf(false) }
                    var query by rememberSaveable(provider.id) { mutableStateOf("") }
                    var onlyFavorites by rememberSaveable(provider.id) { mutableStateOf(false) }
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = surface), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(palette.inputBorder))) {
                        Column(Modifier.padding(16.dp)) {
                            val providerModels = state.models.filter { it.providerId == provider.id }
                            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(provider.name, style = MaterialTheme.typography.titleMedium)
                                    Text("${providerModels.size} models · ${providerModels.count { it.isFavorite }} favorites", style = MaterialTheme.typography.bodySmall, color = Color(palette.toolDefault))
                                }
                                if (provider.isDefault) AssistChip({}, { Text("Default") })
                                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (expanded) "Collapse" else "Expand")
                            }
                            if (expanded) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(if (provider.enabled) "Connected" else "Disabled", color = if (provider.enabled) accent else Color(palette.toolDefault), modifier = Modifier.weight(1f)); Switch(provider.enabled, { MultiProviderStore.saveProvider(provider.copy(enabled = it)); refresh() }) }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton({ editingProvider = provider; providerDialog = true }) { Text("Edit") }
                                TextButton({ MultiProviderStore.duplicateProvider(provider.id); refresh() }) { Text("Duplicate") }
                                TextButton({ MultiProviderStore.saveProvider(provider.copy(isDefault = true)); refresh() }) { Text("Set default") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton({ scope.launch { notice = testProvider(provider) } }) { Text("Test connection") }
                                TextButton({ scope.launch { notice = fetchProviderModels(provider); refresh() } }) { Text("Fetch models") }
                                TextButton({ MultiProviderStore.deleteProvider(provider.id); refresh() }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                            }
                            HorizontalDivider(color = Color(palette.inputBorder))
                            OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search models") }, leadingIcon = { Icon(Icons.Outlined.Search, null) })
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Checkbox(onlyFavorites, { onlyFavorites = it }); Text("Favorites only") }
                            val visibleModels = com.sikoclaw.app.agent.llm.filterProviderModels(providerModels, query, onlyFavorites)
                            Text("${visibleModels.size} results", style = MaterialTheme.typography.bodySmall, color = Color(palette.toolDefault))
                            if (visibleModels.isEmpty()) Text("No models found", modifier = Modifier.padding(vertical = 20.dp), color = Color(palette.toolDefault))
                            visibleModels.forEach { model ->
                                Row(Modifier.fillMaxWidth().clickable { editingModel = model; modelProviderId = provider.id }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(model.displayName)
                                            if (model.isFree) SuggestionChip(onClick = {}, label = { Text("Free") })
                                        }
                                        Text(buildString { append(model.apiModelName); model.contextLength?.let { append(" · ${it / 1000}K context") }; if (!model.supportsTools) append(" · No tools") }, style = MaterialTheme.typography.bodySmall, color = Color(palette.toolDefault))
                                    }
                                    IconButton({ scope.launch { notice = testModel(model, provider) } }) { Icon(Icons.Outlined.PlayArrow, "Test model", tint = accent) }
                                    if (model.supportsVision) IconButton({
                                        MultiProviderStore.setVisionModel(model.id)
                                        notice = "${model.displayName} is now the Vision model"
                                    }) { Icon(if (state.visionModelId == model.id) Icons.Filled.Visibility else Icons.Outlined.Visibility, "Set as Vision model", tint = accent) }
                                    IconButton({
                                        if (!MultiProviderStore.setFavorite(model.id, !model.isFavorite)) notice = "Keep at least one favorite model, or add another model first."
                                        refresh()
                                    }) { Icon(if (model.isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder, "Favorite", tint = accent) }
                                    if (model.isManual) IconButton({ MultiProviderStore.deleteModel(model.id); refresh() }) { Icon(Icons.Outlined.Delete, "Delete model", tint = MaterialTheme.colorScheme.error) }
                                }
                            }
                            OutlinedButton({ editingModel = null; modelProviderId = provider.id }) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("Add model") }
                            }
                        }
                    }
                } }
            } else if (section == 1) LocalModelsSection(surface, accent)
            else if (section == 2) RoutingList(state.routing, state.models, state.providers, surface, accent, ::refresh)
            else if (section == 3) MediaProvidersSection(surface, accent)
            else SearchProvidersSection(surface, accent)
        }
    }
    if (providerDialog) ProviderDialog(
        existing = editingProvider,
        dismiss = { providerDialog = false },
        save = { provider, key -> withContext(Dispatchers.IO) { MultiProviderStore.saveProvider(provider, key) } },
        saved = { notice = it; providerDialog = false },
    )
    modelProviderId?.let { providerId -> ModelDialog(editingModel, providerId, { modelProviderId = null; editingModel = null }, { MultiProviderStore.saveModel(it); modelProviderId = null; editingModel = null; refresh() }) }
}

@Composable
private fun SearchProvidersSection(surface: Color, accent: Color) {
    var config by remember { mutableStateOf(SearchProviderStore.current()) }
    var apiKey by remember { mutableStateOf(config.apiKey) }
    var notice by remember { mutableStateOf<String?>(null) }
    val requiresKey = config.provider == SearchProvider.BRAVE || config.provider == SearchProvider.TAVILY || config.provider == SearchProvider.GOOGLE_CUSTOM
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Web search", style = MaterialTheme.typography.titleMedium)
            Text("DuckDuckGo is the private default and works without an API key. Choose Brave or Tavily if you want their API results.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = surface), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SearchProvider.entries.forEach { option ->
                        Row(Modifier.fillMaxWidth().clickable { config = config.copy(provider = option); apiKey = if (option == config.provider) apiKey else "" }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = config.provider == option, onClick = { config = config.copy(provider = option); apiKey = if (option == config.provider) apiKey else "" })
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(when (option) { SearchProvider.DUCKDUCKGO -> "DuckDuckGo"; SearchProvider.BRAVE -> "Brave Search API"; SearchProvider.TAVILY -> "Tavily Search API"; SearchProvider.GOOGLE_CUSTOM -> "Google Custom Search" })
                                Text(when (option) { SearchProvider.DUCKDUCKGO -> "Default · no API key"; SearchProvider.BRAVE -> "Requires a Brave Search API key"; SearchProvider.TAVILY -> "Requires a Tavily API key"; SearchProvider.GOOGLE_CUSTOM -> "Requires API key and Search Engine ID" }, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (requiresKey) OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API key") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    if (config.provider == SearchProvider.GOOGLE_CUSTOM) OutlinedTextField(config.engineId, { config = config.copy(engineId = it) }, label = { Text("Search Engine ID") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        if (requiresKey && apiKey.isBlank()) notice = "Add an API key before saving"
                        else { SearchProviderStore.save(config.copy(apiKey = apiKey)); config = SearchProviderStore.current(); notice = "Search provider saved" }
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = accent)) { Text("Save search provider") }
                }
            }
        }
        notice?.let { item { Text(it, color = accent) } }
    }
}

@Composable
private fun MediaProvidersSection(surface: Color, accent: Color) {
    var editKind by remember { mutableStateOf<MediaProviderKind?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var image by remember { mutableStateOf(MediaProviderStore.get(MediaProviderKind.IMAGE)) }
    var tts by remember { mutableStateOf(MediaProviderStore.get(MediaProviderKind.TTS)) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Image Generation Providers", style = MaterialTheme.typography.titleMedium) }
        item { MediaProviderCard(image, surface, accent) { editKind = MediaProviderKind.IMAGE } }
        item { Spacer(Modifier.height(4.dp)); Text("Text-to-Speech Providers", style = MaterialTheme.typography.titleMedium) }
        item { MediaProviderCard(tts, surface, accent) { editKind = MediaProviderKind.TTS } }
        notice?.let { item { Text(it, color = if (it.contains("saved", true)) accent else MaterialTheme.colorScheme.error) } }
    }
    editKind?.let { kind ->
        MediaProviderDialog(
            existing = if (kind == MediaProviderKind.IMAGE) image else tts,
            dismiss = { editKind = null },
            save = { config, key ->
                val result = MediaProviderStore.save(config, key)
                notice = result.message
                if (result.success) { if (kind == MediaProviderKind.IMAGE) image = config else tts = config; editKind = null }
            },
        )
    }
}

@Composable
private fun MediaProviderCard(config: MediaProviderConfig, surface: Color, accent: Color, edit: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = edit), colors = CardDefaults.cardColors(containerColor = surface), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (config.kind == MediaProviderKind.IMAGE) Icons.Outlined.Image else Icons.Outlined.GraphicEq, null, tint = accent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(config.name); Text("${config.model} · ${if (config.enabled) "Enabled" else "Disabled"}", style = MaterialTheme.typography.bodySmall) }
            Icon(Icons.Outlined.Edit, "Edit")
        }
    }
}

@Composable
private fun MediaProviderDialog(existing: MediaProviderConfig, dismiss: () -> Unit, save: (MediaProviderConfig, String) -> Unit) {
    var name by remember { mutableStateOf(existing.name) }; var url by remember { mutableStateOf(existing.baseUrl) }
    var key by remember { mutableStateOf("") }; var model by remember { mutableStateOf(existing.model) }
    var option by remember { mutableStateOf(existing.option) }; var format by remember { mutableStateOf(existing.format) }
    var enabled by remember { mutableStateOf(existing.enabled) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (existing.kind == MediaProviderKind.IMAGE) "Image API configuration" else "TTS API configuration") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Provider name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(url, { url = it }, label = { Text("Base URL") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(key, { key = it }, label = { Text("API key (leave blank to keep saved key)") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(model, { model = it }, label = { Text("Model") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(option, { option = it }, label = { Text(if (existing.kind == MediaProviderKind.IMAGE) "Image size" else "Voice") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(format, { format = it }, label = { Text("Response format") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Enabled", Modifier.weight(1f)); Switch(enabled, { enabled = it }) }
        } },
        confirmButton = { Button({ save(existing.copy(name = name.trim(), baseUrl = url.trim().trimEnd('/'), model = model.trim(), option = option.trim(), format = format.trim(), enabled = enabled), key) }, enabled = name.isNotBlank() && url.isNotBlank() && model.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(dismiss) { Text("Cancel") } },
    )
}

private suspend fun testProvider(provider: ApiProviderConfig): String = withContext(Dispatchers.IO) {
    runCatching {
        KaiProviderGateway.testConnection(provider)
        "${provider.name}: connection successful"
    }.getOrElse { "${provider.name}: ${it.message ?: "connection failed"}" }
}

private suspend fun fetchProviderModels(provider: ApiProviderConfig): String = withContext(Dispatchers.IO) {
    runCatching {
        val ids = KaiProviderGateway.fetchModels(provider)
        ids.forEach { id ->
            val existing = MultiProviderStore.state().models.firstOrNull { it.providerId == provider.id && it.apiModelName == id }
            MultiProviderStore.saveModel((existing ?: ApiModelConfig(providerId = provider.id, displayName = id, apiModelName = id)).copy(isFree = provider.catalogId == "free", isManual = false))
        }
        "Fetched ${ids.size} models from ${provider.name}"
    }.getOrElse { "Could not fetch models: ${it.message}" }
}

private suspend fun testModel(model: ApiModelConfig, provider: ApiProviderConfig): String = withContext(Dispatchers.IO) {
    runCatching {
        KaiProviderGateway.testModel(model, provider)
        "${model.displayName}: model test successful"
    }.getOrElse { "${model.displayName}: ${it.message ?: "test failed"}" }
}

@Composable
private fun LocalModelsSection(surface: Color, accent: Color) {
    val context = LocalContext.current
    val catalog = remember { LocalModelManager.catalog(context) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("On-device models", style = MaterialTheme.typography.titleMedium)
            Text("Downloads run locally and remain available after restarting the app.", style = MaterialTheme.typography.bodySmall)
        }
        catalog.forEach { entry -> item(key = entry.model.id) {
            Card(colors = CardDefaults.cardColors(containerColor = surface), shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.model.displayName)
                        Text("Context ${entry.model.defaultContextTokens} · ${if (entry.isSupported) "Good" else "Heavy"} · ${if (entry.isDownloaded) "Downloaded" else "Not downloaded"}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (entry.isDownloaded) Icon(Icons.Outlined.CheckCircle, "Downloaded", tint = accent)
                }
            }
        } }
        item {
            Button(
                onClick = { context.startActivity(Intent(context, LlmConfigActivity::class.java)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
            ) { Text("Manage local models") }
        }
    }
}

@Composable private fun EmptyProviderCard(surface: Color, accent: Color, add: () -> Unit) = Card(colors = CardDefaults.cardColors(containerColor = surface)) { Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.Cloud, null, tint = accent, modifier = Modifier.size(44.dp)); Text("No API providers yet"); Button(add, colors = ButtonDefaults.buttonColors(containerColor = accent)) { Text("Add provider") } } }

@Composable private fun ProviderDialog(
    existing: ApiProviderConfig?,
    dismiss: () -> Unit,
    save: suspend (ApiProviderConfig, String) -> com.sikoclaw.app.agent.llm.ProviderSaveResult,
    saved: (String) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }; var url by remember { mutableStateOf(existing?.baseUrl.orEmpty()) }; var key by remember { mutableStateOf("") }; var type by remember { mutableStateOf(existing?.type ?: "OpenAI compatible") }; var headers by remember { mutableStateOf(existing?.headers?.entries?.joinToString("\n") { "${it.key}: ${it.value}" }.orEmpty()) }
    var catalogId by remember { mutableStateOf(existing?.catalogId.orEmpty()) }
    var protocol by remember { mutableStateOf(existing?.protocol ?: "OPENAI_COMPATIBLE") }
    var modelsPath by remember { mutableStateOf<String?>(existing?.modelsPath ?: "/models") }
    var chatPath by remember { mutableStateOf(existing?.chatPath ?: "/chat/completions") }
    var requiresKey by remember { mutableStateOf(existing?.requiresApiKey ?: true) }
    var templatesExpanded by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var testMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun parsedHeaders() = headers.lineSequence().mapNotNull { line -> line.substringBefore(':').trim().takeIf { it.isNotBlank() }?.let { it to line.substringAfter(':', "").trim() } }.toMap()
    fun draft() = (existing ?: ApiProviderConfig(name = name, baseUrl = url)).copy(name = name.trim(), baseUrl = url.trim().trimEnd('/'), type = type.trim(), headers = parsedHeaders(), catalogId = catalogId, protocol = protocol, modelsPath = modelsPath, chatPath = chatPath, requiresApiKey = requiresKey)
    AlertDialog(dismiss, title = { Text(if (existing == null) "Add service" else "Edit service") }, text = { Column(Modifier.verticalScroll(rememberScrollState())) {
        if (existing == null) {
            Box {
                OutlinedButton(onClick = { templatesExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text(if (catalogId.isBlank()) "Choose provider" else name) }
                DropdownMenu(templatesExpanded, { templatesExpanded = false }) {
                    KaiServiceRegistry.cloudServices.forEach { service -> DropdownMenuItem(text = { Text(service.displayName) }, onClick = {
                        catalogId = service.id; name = service.displayName; url = service.baseUrl; protocol = service.protocol.name
                        modelsPath = service.modelsPath; chatPath = service.chatPath; requiresKey = service.requiresApiKey
                        type = service.protocol.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
                        templatesExpanded = false
                    }) }
                }
            }
        }
        OutlinedTextField(name, { name = it }, label = { Text("Custom name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(url, { url = it }, label = { Text("Base URL") }, modifier = Modifier.fillMaxWidth())
        if (requiresKey) OutlinedTextField(key, { key = it }, label = { Text(if (existing == null) "API key" else "New API key (leave blank to keep)") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(type, { type = it }, label = { Text("Provider type") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(headers, { headers = it }, label = { Text("Optional headers (Name: Value)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        testMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (it.contains("successful", true)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
    } }, confirmButton = { Column(horizontalAlignment = Alignment.End) {
        OutlinedButton(onClick = { testing = true; scope.launch { testMessage = withContext(Dispatchers.IO) { runCatching { KaiProviderGateway.testConnection(draft(), key); "Connection successful" }.getOrElse { it.message ?: "Connection failed" } }; testing = false } }, enabled = !testing && name.isNotBlank() && (url.startsWith("https://") || url.startsWith("http://"))) { if (testing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Test connection") }
        Button(onClick = {
            saving = true; testMessage = null
            scope.launch {
                val result = save(draft(), key)
                saving = false
                if (result.success) saved(result.message) else testMessage = result.message
            }
        }, enabled = !saving && name.isNotBlank() && (url.startsWith("https://") || url.startsWith("http://"))) {
            if (saving) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
            Text(if (saving) "Saving…" else "Save")
        }
    } }, dismissButton = { TextButton(dismiss, enabled = !saving) { Text("Cancel") } })
}

@Composable private fun ModelDialog(existing: ApiModelConfig?, providerId: String, dismiss: () -> Unit, save: (ApiModelConfig) -> Unit) {
    var display by remember { mutableStateOf(existing?.displayName.orEmpty()) }; var api by remember { mutableStateOf(existing?.apiModelName.orEmpty()) }; var tools by remember { mutableStateOf(existing?.supportsTools ?: true) }; var vision by remember { mutableStateOf(existing?.supportsVision ?: false) }; var streaming by remember { mutableStateOf(existing?.supportsStreaming ?: true) }; var timeout by remember { mutableStateOf((existing?.timeoutSeconds ?: 120).toString()) }; var retries by remember { mutableStateOf((existing?.maxRetries ?: 1).toString()) }; var context by remember { mutableStateOf(existing?.contextLength?.toString().orEmpty()) }
    AlertDialog(dismiss, title = { Text(if (existing == null) "Add model" else "Edit model") }, text = { Column(Modifier.verticalScroll(rememberScrollState())) { OutlinedTextField(display, { display = it }, label = { Text("Display name") }); OutlinedTextField(api, { api = it }, label = { Text("API model name") }); OutlinedTextField(context, { context = it.filter(Char::isDigit) }, label = { Text("Context length") }); OutlinedTextField(timeout, { timeout = it.filter(Char::isDigit) }, label = { Text("Timeout seconds") }); OutlinedTextField(retries, { retries = it.filter(Char::isDigit) }, label = { Text("Max retries") }); Row(verticalAlignment = Alignment.CenterVertically) { Text("Tools", Modifier.weight(1f)); Switch(tools, { tools = it }) }; Row(verticalAlignment = Alignment.CenterVertically) { Text("Vision", Modifier.weight(1f)); Switch(vision, { vision = it }) }; Row(verticalAlignment = Alignment.CenterVertically) { Text("Streaming", Modifier.weight(1f)); Switch(streaming, { streaming = it }) } } }, confirmButton = { Button({ save((existing ?: ApiModelConfig(providerId = providerId, displayName = display, apiModelName = api)).copy(displayName = display.trim(), apiModelName = api.trim(), supportsTools = tools, supportsVision = vision, supportsStreaming = streaming, timeoutSeconds = timeout.toIntOrNull()?.coerceIn(5, 600) ?: 120, maxRetries = retries.toIntOrNull()?.coerceIn(0, 10) ?: 1, contextLength = context.toLongOrNull())) }, enabled = display.isNotBlank() && api.isNotBlank()) { Text("Save") } }, dismissButton = { TextButton(dismiss) { Text("Cancel") } })
}

@Composable private fun RoutingList(routing: List<String>, models: List<ApiModelConfig>, providers: List<ApiProviderConfig>, surface: Color, accent: Color, refresh: () -> Unit) {
    val ordered = routing.distinct().mapNotNull { id -> models.firstOrNull { it.id == id && it.enabled && it.isFavorite } }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val dragScope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Drag models to set automatic fallback order. Network, timeout, 429 and server errors move to the next model.", style = MaterialTheme.typography.bodySmall) }
        if (ordered.isEmpty()) item { Text("Favorite a model in Providers to add it here.", modifier = Modifier.padding(vertical = 28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        ordered.forEachIndexed { index, model -> item(key = model.id) {
            var drag by remember { mutableFloatStateOf(0f) }
            val dragState = rememberDraggableState { delta ->
                drag += delta
                if (kotlin.math.abs(drag) > 260f) dragScope.launch { listState.scrollBy(delta * 0.65f) }
            }
            Card(Modifier.fillMaxWidth().graphicsLayer { translationY = drag; scaleX = if (drag != 0f) 1.02f else 1f; scaleY = scaleX; shadowElevation = if (drag != 0f) 14f else 0f }, colors = CardDefaults.cardColors(containerColor = surface)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.DragHandle,
                        "Drag handle",
                        tint = accent,
                        modifier = Modifier.size(40.dp).draggable(
                            state = dragState,
                            orientation = Orientation.Vertical,
                            onDragStopped = {
                                val steps = (drag / 72f).toInt()
                                val target = (index + steps).coerceIn(0, ordered.lastIndex)
                                if (target != index) {
                                    val changed = ordered.map { it.id }.toMutableList()
                                    val moved = changed.removeAt(index)
                                    changed.add(target, moved)
                                    MultiProviderStore.setRouting(changed)
                                    refresh()
                                }
                                drag = 0f
                            },
                        ),
                    )
                    Spacer(Modifier.width(8.dp)); Text("${index + 1}", color = accent); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(model.displayName); Text(providers.firstOrNull { it.id == model.providerId }?.name.orEmpty(), style = MaterialTheme.typography.bodySmall) }
                    if (index == 0) AssistChip(onClick = {}, label = { Text("Active") })
                }
            }
        } }
    }
}
