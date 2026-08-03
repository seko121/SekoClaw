package com.sikoclaw.app.ui.settings

import android.content.Intent
import android.Manifest
import android.provider.Settings
import android.net.Uri
import android.content.res.Configuration
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.sikoclaw.app.agent.skill.SkillRegistry
import com.sikoclaw.app.agent.skill.UserSkill
import com.sikoclaw.app.agent.skill.UserSkillStore
import com.sikoclaw.app.agent.memory.KaiMemoryStore
import com.sikoclaw.app.agent.services.AgentDeviceServices
import com.sikoclaw.app.service.ClawNotificationListener
import com.sikoclaw.app.cron.CronJob
import com.sikoclaw.app.cron.CronManager
import com.sikoclaw.app.heartbeat.HeartbeatConfig
import com.sikoclaw.app.heartbeat.HeartbeatManager
import com.sikoclaw.app.mcp.McpManager
import com.sikoclaw.app.mcp.McpServer
import com.sikoclaw.app.plugin.PluginCatalog
import com.sikoclaw.app.plugin.PluginSettingsViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sikoclaw.app.tool.ToolRegistry
import com.sikoclaw.app.ui.chat.SikoClawColors
import com.sikoclaw.app.ui.chat.ThemeManager
import com.sikoclaw.app.utils.KVUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

class AgentFeaturesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val colors = with(ThemeManager) { ThemeManager.getColors().toComposeColors() }
        window.statusBarColor = ThemeManager.getColors().toolbarBg
        val mode = intent.getStringExtra("mode") ?: "tools"
        setContent {
            MaterialTheme { AgentFeatureRoot(mode, colors, onBack = { finish() }) }
        }
    }
}

@Composable
private fun AgentFeatureRoot(mode: String, colors: SikoClawColors, onBack: () -> Unit) {
    when (mode) {
        "agent" -> AgentSettingsScreen(colors, onBack)
        "user_prompt" -> PromptEditorScreen("User Prompt", "Instructions you always want OctoBot to follow", KVUtils.getUserPrompt(), KVUtils::setUserPrompt, colors, onBack)
        "soul_prompt" -> PromptEditorScreen("Soul Prompt", "Personality, tone and identity", KVUtils.getSoulPrompt(), KVUtils::setSoulPrompt, colors, onBack)
        "memory" -> PromptEditorScreen("Memory", "Stable facts and preferences learned about you", KVUtils.getUserMemoryPrompt(), KVUtils::setUserMemoryPrompt, colors, onBack)
        "skills" -> SkillsScreen(colors, onBack)
        "mcp" -> McpScreen(colors, onBack)
        "cron" -> CronScreen(colors, onBack)
        "heartbeat" -> HeartbeatScreen(colors, onBack)
        "plugins" -> PluginsScreen(colors, onBack)
        "floating" -> FloatingAssistantScreen(colors, onBack)
        else -> ToolsScreen(colors, onBack)
    }
}

@Composable
private fun FloatingAssistantScreen(colors: SikoClawColors, onBack: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(com.sikoclaw.app.floating.FloatingAssistantConfig.enabled() && Settings.canDrawOverlays(context)) }
    var size by remember { mutableFloatStateOf(com.sikoclaw.app.floating.FloatingAssistantConfig.size().toFloat()) }
    var opacity by remember { mutableFloatStateOf(com.sikoclaw.app.floating.FloatingAssistantConfig.opacity().toFloat()) }
    var snap by remember { mutableStateOf(com.sikoclaw.app.floating.FloatingAssistantConfig.snap()) }
    var actions by remember { mutableStateOf(com.sikoclaw.app.floating.FloatingAssistantConfig.showActions()) }
    var previews by remember { mutableStateOf(com.sikoclaw.app.floating.FloatingAssistantConfig.showPreviews()) }
    var autoStart by remember { mutableStateOf(com.sikoclaw.app.floating.FloatingAssistantConfig.autoStart()) }
    var autoStop by remember { mutableStateOf(com.sikoclaw.app.floating.FloatingAssistantConfig.autoStop()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        enabled = Settings.canDrawOverlays(context)
        com.sikoclaw.app.floating.FloatingAssistantConfig.setEnabled(enabled)
        if (enabled) androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, com.sikoclaw.app.floating.FloatingAssistantService::class.java))
    }
    AppScreenScaffold("Floating Assistant", colors, onBack) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Keep the same OctoBot conversation available above other apps. Overlay permission is requested only when you enable it.", color = colors.textSecondary)
            SettingsCard(colors) {
                ToggleSettingsRow("Enable floating bubble", "Uses a foreground service and Android overlay window.", enabled, colors) { value ->
                    if (value && !Settings.canDrawOverlays(context)) permission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                    else { enabled = value; com.sikoclaw.app.floating.FloatingAssistantConfig.setEnabled(value); if (value) androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, com.sikoclaw.app.floating.FloatingAssistantService::class.java)) else context.stopService(Intent(context, com.sikoclaw.app.floating.FloatingAssistantService::class.java)) }
                }
            }
            Text("Bubble size: ${size.toInt()}dp", color = colors.textPrimary); Slider(size, { size = it; com.sikoclaw.app.floating.FloatingAssistantConfig.setSize(it.toInt()) }, valueRange = 44f..80f)
            Text("Opacity: ${opacity.toInt()}%", color = colors.textPrimary); Slider(opacity, { opacity = it; com.sikoclaw.app.floating.FloatingAssistantConfig.setOpacity(it.toInt()) }, valueRange = 40f..100f)
            SettingsCard(colors) {
                ToggleSettingsRow("Snap to edges", "Attach the bubble to the nearest horizontal edge.", snap, colors) { snap = it; com.sikoclaw.app.floating.FloatingAssistantConfig.setSnap(it) }
                HorizontalDivider(color = colors.divider)
                ToggleSettingsRow("Show agent actions", "Show concise tool and control status.", actions, colors) { actions = it; com.sikoclaw.app.floating.FloatingAssistantConfig.setShowActions(it) }
                HorizontalDivider(color = colors.divider)
                ToggleSettingsRow("Show message previews", "Show recent chat text in the expanded bubble.", previews, colors) { previews = it; com.sikoclaw.app.floating.FloatingAssistantConfig.setShowPreviews(it) }
                HorizontalDivider(color = colors.divider)
                ToggleSettingsRow("Start with phone control", "Show the bubble when an agent control session begins.", autoStart, colors) { autoStart = it; com.sikoclaw.app.floating.FloatingAssistantConfig.setAutoStart(it) }
                HorizontalDivider(color = colors.divider)
                ToggleSettingsRow("Stop when task finishes", "Close the bubble after the active task ends.", autoStop, colors) { autoStop = it; com.sikoclaw.app.floating.FloatingAssistantConfig.setAutoStop(it) }
            }
        }
    }
}

@Composable
private fun AgentSettingsScreen(colors: SikoClawColors, onBack: () -> Unit) {
    val context = LocalContext.current
    var soul by rememberSaveable { mutableStateOf(KVUtils.getSoulPrompt()) }
    var soulSaved by remember { mutableStateOf(false) }
    var memoriesEnabled by remember { mutableStateOf(KaiMemoryStore.isEnabled()) }
    var showThoughts by remember { mutableStateOf(KVUtils.getBoolean("SHOW_AGENT_THOUGHTS", false)) }
    var stuckMode by remember { mutableStateOf(com.sikoclaw.app.agent.StuckDetectionMode.current()) }
    var memoryVersion by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    var readSmsEnabled by remember { mutableStateOf(AgentDeviceServices.readSmsEnabled()) }
    var sendSmsEnabled by remember { mutableStateOf(AgentDeviceServices.sendSmsEnabled()) }
    var notificationsEnabled by remember { mutableStateOf(AgentDeviceServices.notificationsEnabled() && ClawNotificationListener.isEnabledInSettings(context)) }
    var showNotificationApps by remember { mutableStateOf(false) }
    var notificationPackages by remember { mutableStateOf(AgentDeviceServices.notificationPackages()) }
    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        readSmsEnabled = granted
        AgentDeviceServices.setReadSmsEnabled(granted)
    }
    val notificationAccess = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        notificationsEnabled = ClawNotificationListener.isEnabledInSettings(context)
        AgentDeviceServices.setNotificationsEnabled(notificationsEnabled)
    }
    val memories = remember(memoryVersion) { KaiMemoryStore.all() }
    val heartbeat = HeartbeatManager.config()
    val scheduled = CronManager.all().count { it.enabled }

    if (showNotificationApps) {
        val launchableApps = remember {
            context.packageManager.getInstalledApplications(0)
                .filter { context.packageManager.getLaunchIntentForPackage(it.packageName) != null }
                .map { it.packageName to context.packageManager.getApplicationLabel(it).toString() }
                .sortedBy { it.second.lowercase() }
        }
        AlertDialog(
            onDismissRequest = { showNotificationApps = false },
            title = { Text("Notification apps") },
            text = {
                Column {
                    Text("No selection means all apps. Choose apps to limit what the agent may read.", fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(launchableApps, key = { it.first }) { (packageName, label) ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    notificationPackages = if (packageName in notificationPackages) notificationPackages - packageName else notificationPackages + packageName
                                }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = packageName in notificationPackages,
                                    onCheckedChange = { checked ->
                                        notificationPackages = if (checked) notificationPackages + packageName else notificationPackages - packageName
                                    },
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(packageName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    AgentDeviceServices.setNotificationPackages(notificationPackages)
                    showNotificationApps = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    notificationPackages = emptySet()
                    AgentDeviceServices.setNotificationPackages(emptySet())
                    showNotificationApps = false
                }) { Text("Allow all") }
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all memories?") },
            text = { Text("This removes every learned memory. This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    KaiMemoryStore.clear()
                    memoryVersion++
                    confirmClear = false
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }

    AppScreenScaffold("Agent", colors, onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionTitle("Soul", colors) }
            item {
                Text("System instructions used by the agent in every new conversation.", color = colors.textSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = soul,
                    onValueChange = { if (it.length <= 12_000) { soul = it; soulSaved = false } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 170.dp),
                    placeholder = { Text("Describe OctoBot's personality, tone and operating rules") },
                    supportingText = { Text("${soul.length} / 12000") },
                    shape = RoundedCornerShape(12.dp),
                    colors = appTextFieldColors(colors),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                    TextButton(onClick = { soul = com.sikoclaw.app.agent.AgentPromptDefaults.soul; soulSaved = false }) { Text("Reset to default") }
                    PrimaryActionButton("Save", colors, enabled = soul != KVUtils.getSoulPrompt()) {
                        KVUtils.setSoulPrompt(soul.trim())
                        soulSaved = true
                    }
                }
                if (soulSaved) Text("Soul saved", color = colors.accent, fontSize = 13.sp)
            }

            item { SectionTitle("Response", colors) }
            item {
                SettingsCard(colors) {
                    ToggleSettingsRow("Show thoughts", "Show provider reasoning in a separate, clearly labeled message.", showThoughts, colors) {
                        showThoughts = it; KVUtils.putBoolean("SHOW_AGENT_THOUGHTS", it)
                    }
                    HorizontalDivider(color = colors.divider)
                    Column(Modifier.padding(16.dp)) {
                        Text("Stuck detection", color = colors.textPrimary, fontWeight = FontWeight.Medium)
                        Text("Balanced does not stop a task from unchanged screen text alone.", color = colors.textSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            com.sikoclaw.app.agent.StuckDetectionMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = stuckMode == mode,
                                    onClick = { stuckMode = mode; KVUtils.putString("STUCK_DETECTION_MODE", mode.name) },
                                    label = { Text(mode.name.lowercase().replaceFirstChar(Char::uppercase)) },
                                )
                            }
                        }
                    }
                }
            }

            item { SectionTitle("Memories", colors) }
            item {
                SettingsCard(colors) {
                    ToggleSettingsRow(
                        "Use memories",
                        "Include learned facts and preferences in agent context.",
                        memoriesEnabled,
                        colors,
                    ) {
                        memoriesEnabled = it
                        KaiMemoryStore.setEnabled(it)
                    }
                    if (memories.isNotEmpty()) HorizontalDivider(color = colors.divider)
                    memories.forEachIndexed { index, memory ->
                        SettingsRow(memory.key, memory.content, colors, trailing = {
                            IconButton(onClick = { KaiMemoryStore.delete(memory.key); memoryVersion++ }) {
                                Icon(Icons.Outlined.Delete, "Delete memory", tint = colors.textSecondary)
                            }
                        })
                        if (index != memories.lastIndex) HorizontalDivider(color = colors.divider)
                    }
                }
                if (memories.isEmpty()) {
                    Text("No learned memories yet", color = colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                } else {
                    TextButton(onClick = { confirmClear = true }) { Text("Clear all memories") }
                }
            }

            item { SectionTitle("Automation", colors) }
            item {
                SettingsCard(colors) {
                    SettingsRow("Scheduled tasks", "$scheduled active", colors, trailing = { Icon(Icons.Outlined.ChevronRight, null, tint = colors.textSecondary) }) {
                        context.startActivity(Intent(context, AgentFeaturesActivity::class.java).putExtra("mode", "cron"))
                    }
                    HorizontalDivider(color = colors.divider)
                    SettingsRow("Heartbeat", if (heartbeat.enabled) "Every ${heartbeat.intervalMinutes} minutes" else "Off", colors, trailing = { Icon(Icons.Outlined.ChevronRight, null, tint = colors.textSecondary) }) {
                        context.startActivity(Intent(context, AgentFeaturesActivity::class.java).putExtra("mode", "heartbeat"))
                    }
                }
            }

            item { SectionTitle("Device services", colors) }
            item {
                SettingsCard(colors) {
                    ToggleSettingsRow("Read SMS", "Allow the agent to search received SMS only when enabled.", readSmsEnabled, colors) { enabled ->
                        if (enabled) smsPermission.launch(Manifest.permission.READ_SMS) else { readSmsEnabled = false; AgentDeviceServices.setReadSmsEnabled(false) }
                    }
                    HorizontalDivider(color = colors.divider)
                    ToggleSettingsRow("Send SMS", "The agent can prepare a draft; you must review and tap Send.", sendSmsEnabled, colors) {
                        sendSmsEnabled = it; AgentDeviceServices.setSendSmsEnabled(it)
                    }
                    HorizontalDivider(color = colors.divider)
                    ToggleSettingsRow("Read notifications", "Uses Android notification access and stops when disabled.", notificationsEnabled, colors) { enabled ->
                        if (enabled) notificationAccess.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        else { notificationsEnabled = false; AgentDeviceServices.setNotificationsEnabled(false) }
                    }
                    if (notificationsEnabled) {
                        HorizontalDivider(color = colors.divider)
                        SettingsRow(
                            "Allowed notification apps",
                            if (notificationPackages.isEmpty()) "All apps" else "${notificationPackages.size} selected",
                            colors,
                            trailing = { Icon(Icons.Outlined.ChevronRight, null, tint = colors.textSecondary) },
                        ) { showNotificationApps = true }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeartbeatScreen(colors: SikoClawColors, onBack: () -> Unit) {
    val context = LocalContext.current
    var config by remember { mutableStateOf(HeartbeatManager.config()) }
    var interval by rememberSaveable { mutableStateOf(config.intervalMinutes.toString()) }
    var from by rememberSaveable { mutableStateOf(config.activeFrom.toString()) }
    var until by rememberSaveable { mutableStateOf(config.activeUntil.toString()) }
    var saved by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    AppScreenScaffold("Heartbeat", colors, onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Let OctoBot periodically check whether something needs your attention.", color = colors.textSecondary, fontSize = 14.sp)
            SettingsCard(colors) {
                ToggleSettingsRow("Agent heartbeat", "Runs quietly in the background. HEARTBEAT_OK responses stay silent.", config.enabled, colors) { config = config.copy(enabled = it); saved = false }
            }
            Text("Schedule", color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            SettingsCard(colors) {
                AppField(interval, { interval = it.filter(Char::isDigit); saved = false }, "Interval in minutes (minimum 15)", colors, keyboardType = KeyboardType.Number)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) { AppField(from, { from = it.filter(Char::isDigit); saved = false }, "Active from hour", colors, keyboardType = KeyboardType.Number) }
                    Box(Modifier.weight(1f)) { AppField(until, { until = it.filter(Char::isDigit); saved = false }, "Active until hour", colors, keyboardType = KeyboardType.Number) }
                }
            }
            Text("Instructions", color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(config.prompt, { config = config.copy(prompt = it); saved = false }, Modifier.fillMaxWidth().heightIn(min = 150.dp), placeholder = { Text("What should the heartbeat check?") }, colors = appTextFieldColors(colors), shape = RoundedCornerShape(12.dp))
            PrimaryActionButton("Save heartbeat", colors, enabled = config.prompt.isNotBlank()) {
                config = config.copy(intervalMinutes = interval.toIntOrNull()?.coerceIn(15, 59) ?: 30, activeFrom = from.toIntOrNull()?.coerceIn(0, 23) ?: 8, activeUntil = until.toIntOrNull()?.coerceIn(0, 23) ?: 23)
                HeartbeatManager.save(context, config)
                    .onSuccess { saved = true; saveError = null }
                    .onFailure { saved = false; saveError = it.message ?: "Could not schedule heartbeat" }
            }
            if (saved) Text("Heartbeat settings saved", color = colors.accent, fontSize = 13.sp)
            saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            if (HeartbeatManager.lastRunAt() > 0L) {
                Text("Last run: ${DateFormat.format("yyyy-MM-dd HH:mm", Date(HeartbeatManager.lastRunAt()))}", color = colors.textSecondary, fontSize = 13.sp)
                HeartbeatManager.lastResult().takeIf { it.isNotBlank() }?.let { Text(it, color = colors.textSecondary, fontSize = 13.sp) }
            }
        }
    }
}

@Composable
private fun PluginsScreen(colors: SikoClawColors, onBack: () -> Unit) {
    val model: PluginSettingsViewModel = viewModel()
    val ui by model.state.collectAsState()
    AppScreenScaffold("Plugins", colors, onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Text("Built-in", color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            item {
                SettingsCard(colors, Modifier.fillMaxWidth()) {
                    ui.plugins.forEachIndexed { index, item ->
                        SettingsRow(item.plugin.name, item.detail ?: item.runtimeState.name.lowercase().replace('_', ' '), colors, trailing = {
                            Switch(item.enabled, { model.togglePlugin(item.plugin.id, it) }, enabled = item.runtimeState !in setOf(com.sikoclaw.app.plugin.PluginRuntimeState.STARTING, com.sikoclaw.app.plugin.PluginRuntimeState.STOPPING))
                        }, onClick = { model.togglePlugin(item.plugin.id, !item.enabled) })
                        if (index != ui.plugins.lastIndex) HorizontalDivider(color = colors.inputBorder)
                    }
                }
            }
            item { Text("Agent control", color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            item {
                SettingsCard(colors, Modifier.fillMaxWidth()) {
                    SettingsRow("Control halo", "Show a blue border while the agent controls the screen.", colors, trailing = {
                        Switch(ui.haloEnabled, model::setHalo)
                    }, onClick = { model.setHalo(!ui.haloEnabled) })
                    HorizontalDivider(color = colors.inputBorder)
                    SettingsRow("Virtual pointer", "Show the agent pointer before taps and gestures.", colors, trailing = {
                        Switch(ui.pointerEnabled, model::setPointer)
                    }, onClick = { model.setPointer(!ui.pointerEnabled) })
                }
            }
            item { Text("Additional signed plugins can be added after you approve their permissions.", color = colors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreenScaffold(title: String, colors: SikoClawColors, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = colors.textPrimary) } },
                actions = actions,
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.surface),
                windowInsets = WindowInsets.statusBars,
            )
        },
        content = content,
    )
}

@Composable fun SettingsCard(colors: SikoClawColors, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) = Card(modifier, RoundedCornerShape(14.dp), CardDefaults.cardColors(colors.surface), border = BorderStroke(1.dp, colors.inputBorder)) { Column(content = content) }

@Composable
fun SettingsRow(title: String, description: String, colors: SikoClawColors, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null, onClick: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (description.isNotBlank()) { Spacer(Modifier.height(3.dp)); Text(description, color = colors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        }
        if (trailing != null) { Spacer(Modifier.width(12.dp)); trailing() }
    }
}

@Composable
fun ToggleSettingsRow(title: String, description: String, checked: Boolean, colors: SikoClawColors, onChecked: (Boolean) -> Unit) = SettingsRow(title, description, colors, trailing = { Switch(checked, onCheckedChange = onChecked, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = colors.accent)) }, onClick = { onChecked(!checked) })

@Composable
fun PrimaryActionButton(text: String, colors: SikoClawColors, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false, onClick: () -> Unit) {
    Button(onClick, modifier.heightIn(min = 48.dp), enabled = enabled && !loading, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = Color.White, disabledContainerColor = colors.accent.copy(alpha = .3f))) {
        if (loading) { CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
        Text(text)
    }
}

@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String, action: String, colors: SikoClawColors, modifier: Modifier = Modifier, onAction: () -> Unit) {
    Column(modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = colors.accent, modifier = Modifier.size(48.dp)); Spacer(Modifier.height(16.dp))
        Text(title, color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(8.dp))
        Text(description, color = colors.textSecondary, fontSize = 14.sp, lineHeight = 20.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center); Spacer(Modifier.height(20.dp))
        PrimaryActionButton(action, colors, onClick = onAction)
    }
}

@Composable fun SectionTitle(text: String, colors: SikoClawColors) = Text(text, color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp))

@Composable
private fun ToolsScreen(colors: SikoClawColors, onBack: () -> Unit) {
    val tools = remember { ToolRegistry.getAllRegisteredTools() }
    var query by remember { mutableStateOf("") }; var filter by remember { mutableStateOf("All") }; var version by remember { mutableIntStateOf(0) }
    val enabledCount = tools.count { KVUtils.isToolEnabled(it.getName()) || it.getName() == "finish" }
    val visible = tools.filter { t -> val enabled = KVUtils.isToolEnabled(t.getName()) || t.getName() == "finish"; (query.isBlank() || t.getDisplayName().contains(query, true) || t.getDescription().contains(query, true)) && (filter == "All" || filter == "Enabled" && enabled || filter == "Disabled" && !enabled) }
    AppScreenScaffold("Manage Tools", colors, onBack) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {
            Text("$enabledCount enabled", color = colors.accent, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search tools") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, shape = RoundedCornerShape(12.dp), colors = appTextFieldColors(colors))
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("All", "Enabled", "Disabled").forEach { item -> FilterChip(filter == item, { filter = item }, { Text(item) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.accent.copy(alpha = .18f), selectedLabelColor = colors.accent)) } }
            SettingsCard(colors, Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn { items(visible, key = { it.getName() + version }) { tool ->
                    val locked = tool.getName() == "finish"; val enabled = locked || KVUtils.isToolEnabled(tool.getName())
                    ToolToggleRow(tool.getDisplayName(), tool.getDescription(), enabled, colors) { if (!locked) { KVUtils.setToolEnabled(tool.getName(), it); version++ } }
                    if (tool != visible.lastOrNull()) HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = 16.dp))
                } }
            }; Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable fun ToolToggleRow(name: String, description: String, enabled: Boolean, colors: SikoClawColors, onToggle: (Boolean) -> Unit) = ToggleSettingsRow(name, description, enabled, colors, onToggle)
@Composable fun SkillToggleRow(name: String, description: String, enabled: Boolean, colors: SikoClawColors, onToggle: (Boolean) -> Unit, onEdit: (() -> Unit)? = null) = SettingsRow(name, description, colors, trailing = { Switch(enabled, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedTrackColor = colors.accent)) }, onClick = onEdit ?: { onToggle(!enabled) })

@Composable
private fun SkillsScreen(colors: SikoClawColors, onBack: () -> Unit) {
    var version by remember { mutableIntStateOf(0) }; var editing by remember { mutableStateOf<UserSkill?>(null) }; var creating by remember { mutableStateOf(false) }
    val builtIns = SkillRegistry.getAll(); val custom = UserSkillStore.all()
    if (creating || editing != null) SkillEditorDialog(editing, colors, { creating = false; editing = null }, { UserSkillStore.upsert(it); creating = false; editing = null; version++ }, { editing?.let { s -> UserSkillStore.delete(s.id) }; editing = null; version++ })
    AppScreenScaffold("Skills", colors, onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            item { PrimaryActionButton("Create skill", colors, Modifier.fillMaxWidth().padding(top = 16.dp), onClick = { creating = true }); SectionTitle("Built-in", colors); SettingsCard(colors, Modifier.fillMaxWidth()) {
                builtIns.forEachIndexed { i, s -> val key = "SKILL_ENABLED_${s.id}"; SkillToggleRow(s.name, s.description, KVUtils.getBoolean(key, true), colors, { KVUtils.putBoolean(key, it); version++ }); if (i < builtIns.lastIndex) HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = 16.dp)) }
            }; SectionTitle("Learned & Custom", colors) }
            if (custom.isEmpty()) item { SettingsCard(colors, Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.AutoAwesome, null, tint = colors.accent); Spacer(Modifier.height(8.dp)); Text("No custom skills yet", color = colors.textPrimary, fontWeight = FontWeight.Medium); Text("Create a reusable workflow for OctoBot.", color = colors.textSecondary, fontSize = 13.sp); TextButton({ creating = true }) { Text("Create skill", color = colors.accent) } } } }
            else item { SettingsCard(colors, Modifier.fillMaxWidth()) { custom.forEachIndexed { i, s -> SkillToggleRow(s.name, s.description, s.enabled, colors, { UserSkillStore.upsert(s.copy(enabled = it)); version++ }, { editing = s }); if (i < custom.lastIndex) HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = 16.dp)) } } }
        }
    }
}

@Composable
private fun SkillEditorDialog(old: UserSkill?, colors: SikoClawColors, dismiss: () -> Unit, save: (UserSkill) -> Unit, delete: () -> Unit) {
    var name by remember { mutableStateOf(old?.name.orEmpty()) }; var description by remember { mutableStateOf(old?.description.orEmpty()) }; var triggers by remember { mutableStateOf(old?.triggers.orEmpty()) }; var instructions by remember { mutableStateOf(old?.instructions.orEmpty()) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (old == null) "Create skill" else "Edit skill", color = colors.textPrimary) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { AppField(name, { name = it }, "Name", colors); AppField(description, { description = it }, "Description", colors); AppField(triggers, { triggers = it }, "Trigger phrases", colors); AppField(instructions, { instructions = it }, "Instructions", colors, false) } },
        confirmButton = { PrimaryActionButton("Save", colors, enabled = name.isNotBlank(), onClick = { val id = old?.id ?: name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_'); save(UserSkill(id, name.trim(), description, instructions, triggers, old?.enabled ?: true)) }) },
        dismissButton = { Row { TextButton(dismiss) { Text("Cancel", color = colors.textSecondary) }; if (old != null) TextButton(delete) { Text("Delete", color = Color(0xFFF87171)) } } },
        modifier = Modifier.imePadding(),
        containerColor = colors.surface
    )
}

@Composable
private fun McpScreen(colors: SikoClawColors, onBack: () -> Unit) {
    var servers by remember { mutableStateOf(McpManager.all().toList()) }; var edit by remember { mutableStateOf<McpServer?>(null) }; var showAdd by remember { mutableStateOf(false) }; var connecting by remember { mutableStateOf<String?>(null) }; var statuses by remember { mutableStateOf<Map<String, String>>(emptyMap()) }; var deleteTarget by remember { mutableStateOf<McpServer?>(null) }; val scope = rememberCoroutineScope(); val clipboard = LocalClipboardManager.current
    fun refresh() { servers = McpManager.all().toList() }
    if (showAdd || edit != null) McpDialog(edit, colors, { showAdd = false; edit = null }) { server ->
        connecting = server.id
        val result = withContext(Dispatchers.IO) { runCatching { McpManager.connect(server) } }
        if (result.isSuccess) {
            McpManager.upsert(server)
            statuses = statuses + (server.id to "Connected - ${result.getOrDefault(0)} tools")
            showAdd = false
            edit = null
            refresh()
        }
        connecting = null
        result.exceptionOrNull()?.message
    }
    deleteTarget?.let { s -> AlertDialog({ deleteTarget = null }, title = { Text("Delete MCP server?") }, text = { Text(s.name) }, confirmButton = { TextButton({ McpManager.delete(s.id); deleteTarget = null; refresh() }) { Text("Delete", color = Color(0xFFF87171)) } }, dismissButton = { TextButton({ deleteTarget = null }) { Text("Cancel") } }, containerColor = colors.surface) }
    AppScreenScaffold("MCP Servers", colors, onBack, actions = { IconButton({ clipboard.setText(AnnotatedString(McpManager.exportConfiguration())); statuses = statuses + ("system" to "Configuration copied") }) { Icon(Icons.Outlined.Upload, "Export configuration", tint = colors.textSecondary) }; IconButton({ runCatching { McpManager.importConfiguration(clipboard.getText()?.text.orEmpty()) }.onSuccess { refresh(); statuses = statuses + ("system" to "Imported $it servers") }.onFailure { statuses = statuses + ("system" to (it.message ?: "Import failed")) } }) { Icon(Icons.Outlined.Download, "Import configuration", tint = colors.textSecondary) }; IconButton({ showAdd = true }) { Icon(Icons.Outlined.Add, "Add MCP server", tint = colors.accent) } }) { pad ->
        if (servers.isEmpty()) LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { EmptyState(Icons.Outlined.Dns, "No MCP servers configured", "Connect an MCP server to extend OctoBot with external tools and data.", "Add MCP server", colors) { showAdd = true } }
            item { Text("Suggested", color = colors.textSecondary, fontWeight = FontWeight.SemiBold) }
            items(com.sikoclaw.app.mcp.kaiPopularMcpServers) { preset -> SettingsCard(colors) { SettingsRow(preset.name, preset.description, colors, trailing = { Icon(Icons.Outlined.Add, "Review suggestion", tint = colors.accent) }) { edit = McpServer("m${System.currentTimeMillis()}", preset.name, preset.url, enabled = false) } } }
        }
        else LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { items(servers, key = { it.id }) { s ->
            SettingsCard(colors, Modifier.fillMaxWidth()) { SettingsRow(s.name, s.url, colors, trailing = { StatusBadge(statuses[s.id] ?: if (s.enabled) "Enabled" else "Disabled", if (statuses[s.id]?.startsWith("Connected") == true) Color(0xFF34D399) else colors.textSecondary, colors) }); Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { TextButton({ McpManager.upsert(s.copy(enabled = !s.enabled)); refresh() }) { Text(if (s.enabled) "Disable" else "Enable", color = colors.accent) }; TextButton({ edit = s }) { Text("Edit", color = colors.textSecondary) }; TextButton({ connecting = s.id; scope.launch { val r = withContext(Dispatchers.IO) { runCatching { McpManager.connect(s) } }; statuses = statuses + (s.id to if (r.isSuccess) "Connected · ${r.getOrDefault(0)} tools" else "Error"); connecting = null } }) { if (connecting == s.id) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Test", color = colors.textSecondary) }; Spacer(Modifier.weight(1f)); IconButton({ deleteTarget = s }) { Icon(Icons.Outlined.Delete, "Delete", tint = Color(0xFFF87171)) } } }
        } }
    }
}

@Composable
private fun McpDialog(old: McpServer?, colors: SikoClawColors, dismiss: () -> Unit, connect: suspend (McpServer) -> String?) {
    var name by remember { mutableStateOf(old?.name.orEmpty()) }; var url by remember { mutableStateOf(old?.url.orEmpty()) }; var token by remember { mutableStateOf(old?.token.orEmpty()) }; var headers by remember { mutableStateOf(old?.headers?.entries?.joinToString("\n") { "${it.key}: ${it.value}" }.orEmpty()) }; var loading by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope(); val validUrl = url.startsWith("https://") || url.startsWith("http://")
    AlertDialog(dismiss, title = { Text(if (old == null) "Add MCP Server" else "Edit MCP Server", color = colors.textPrimary) }, text = { Column(Modifier.verticalScroll(rememberScrollState())) { AppField(name, { name = it; error = null }, "Server name", colors); AppField(url, { url = it; error = null }, "Endpoint URL", colors, keyboardType = KeyboardType.Uri, error = if (url.isNotBlank() && !validUrl) "Enter a valid HTTP or HTTPS URL" else null); AppField(token, { token = it }, "Bearer token (optional)", colors, visualPassword = true); AppField(headers, { headers = it }, "Headers (Name: Value)", colors, singleLine = false); error?.let { Text(it, color = Color(0xFFF87171), fontSize = 13.sp) } } }, confirmButton = { PrimaryActionButton("Save & connect", colors, enabled = name.isNotBlank() && url.isNotBlank() && validUrl, loading = loading) { loading = true; error = null; scope.launch { val parsed = headers.lineSequence().mapNotNull { line -> line.substringBefore(':').trim().takeIf(String::isNotBlank)?.let { it to line.substringAfter(':').trim() } }.toMap(); val server = McpServer(old?.id ?: "m${System.currentTimeMillis()}", name.trim(), url.trim(), token, old?.enabled ?: true, parsed); error = connect(server); loading = false } } }, dismissButton = { TextButton(dismiss) { Text("Cancel", color = colors.textSecondary) } }, modifier = Modifier.imePadding(), containerColor = colors.surface)
}

@Composable
private fun CronScreen(colors: SikoClawColors, onBack: () -> Unit) {
    val context = LocalContext.current; var jobs by remember { mutableStateOf(CronManager.all().toList()) }; var edit by remember { mutableStateOf<CronJob?>(null) }; var add by remember { mutableStateOf(false) }; var deleteTarget by remember { mutableStateOf<CronJob?>(null) }; fun refresh() { jobs = CronManager.all().toList() }
    if (add || edit != null) CronDialog(edit, colors, { add = false; edit = null }, { CronManager.upsert(context, it); add = false; edit = null; refresh() })
    deleteTarget?.let { job -> AlertDialog({ deleteTarget = null }, title = { Text("Delete cron job?") }, text = { Text(job.name) }, confirmButton = { TextButton({ CronManager.delete(context, job.id); deleteTarget = null; refresh() }) { Text("Delete", color = Color(0xFFF87171)) } }, dismissButton = { TextButton({ deleteTarget = null }) { Text("Cancel") } }, containerColor = colors.surface) }
    AppScreenScaffold("Cron Jobs", colors, onBack, actions = { IconButton({ add = true }) { Icon(Icons.Outlined.Add, "Add cron job", tint = colors.accent) } }) { pad ->
        if (jobs.isEmpty()) EmptyState(Icons.Outlined.Schedule, "No scheduled jobs", "Create recurring tasks for OctoBot to run automatically.", "Add cron job", colors, Modifier.padding(pad)) { add = true }
        else LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { items(jobs, key = { it.id }) { j -> SettingsCard(colors, Modifier.fillMaxWidth()) { SettingsRow(j.name, "${j.expression}\nNext run: ${if (j.nextRun > 0) DateFormat.getMediumDateFormat(context).format(Date(j.nextRun)) + " " + DateFormat.getTimeFormat(context).format(Date(j.nextRun)) else "Paused"}\nLast run: Not available", colors, trailing = { Switch(j.enabled, { CronManager.upsert(context, j.copy(enabled = it)); refresh() }, colors = SwitchDefaults.colors(checkedTrackColor = colors.accent)) }); Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) { TextButton({ context.startActivity(Intent(context, com.sikoclaw.app.ui.chat.ComposeChatActivity::class.java).putExtra("task", j.prompt)) }) { Text("Run now", color = colors.accent) }; TextButton({ edit = j }) { Text("Edit", color = colors.textSecondary) }; Spacer(Modifier.weight(1f)); IconButton({ deleteTarget = j }) { Icon(Icons.Outlined.Delete, "Delete", tint = Color(0xFFF87171)) } } } } }
    }
}

@Composable private fun CronDialog(old: CronJob?, colors: SikoClawColors, dismiss: () -> Unit, save: (CronJob) -> Unit) { var name by remember { mutableStateOf(old?.name.orEmpty()) }; var expression by remember { mutableStateOf(old?.expression ?: "0 9 * * *") }; var prompt by remember { mutableStateOf(old?.prompt.orEmpty()) }; var error by remember { mutableStateOf<String?>(null) }; AlertDialog(dismiss, title = { Text(if (old == null) "Add Cron Job" else "Edit Cron Job") }, text = { Column(Modifier.verticalScroll(rememberScrollState())) { AppField(name, { name = it }, "Name", colors); AppField(expression, { expression = it }, "Cron schedule", colors); AppField(prompt, { prompt = it }, "Agent task prompt", colors, false); error?.let { Text(it, color = Color(0xFFF87171), fontSize = 13.sp) } } }, confirmButton = { PrimaryActionButton("Save", colors, enabled = name.isNotBlank() && prompt.isNotBlank()) { runCatching { CronManager.next(expression); save(CronJob(old?.id ?: "c${System.currentTimeMillis()}", name.trim(), expression.trim(), prompt, old?.enabled ?: true)) }.onFailure { error = it.message } } }, dismissButton = { TextButton(dismiss) { Text("Cancel") } }, modifier = Modifier.imePadding(), containerColor = colors.surface) }

@Composable
fun PromptEditorScreen(title: String, subtitle: String, original: String, save: (String) -> Unit, colors: SikoClawColors, onBack: () -> Unit) {
    var savedText by remember { mutableStateOf(original) }; var text by remember { mutableStateOf(original) }; var discard by remember { mutableStateOf(false) }; val snackbar = remember { SnackbarHostState() }; val scope = rememberCoroutineScope(); val changed = text != savedText
    val requestBack = { if (changed) discard = true else onBack() }; BackHandler(onBack = requestBack)
    if (discard) AlertDialog({ discard = false }, title = { Text("Discard unsaved changes?") }, text = { Text("Your edits have not been saved.") }, confirmButton = { TextButton(onClick = onBack) { Text("Discard", color = Color(0xFFF87171)) } }, dismissButton = { TextButton({ discard = false }) { Text("Keep editing") } }, containerColor = colors.surface)
    AppScreenScaffold(title, colors, requestBack) { pad ->
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = Color.Transparent) { inner -> Column(Modifier.fillMaxSize().padding(pad).padding(inner).padding(horizontal = 16.dp).imePadding()) { Text(subtitle, color = colors.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 18.dp, bottom = 12.dp)); OutlinedTextField(text, { if (it.length <= 8000) text = it }, Modifier.fillMaxWidth().weight(1f).heightIn(min = 180.dp), placeholder = { Text("Write your instructions here...") }, shape = RoundedCornerShape(14.dp), minLines = 8, colors = appTextFieldColors(colors), supportingText = { Text("${text.length} / 8000", color = colors.textSecondary) }); Spacer(Modifier.height(16.dp)); PrimaryActionButton("Save changes", colors, Modifier.fillMaxWidth(), enabled = changed) { save(text); savedText = text; scope.launch { snackbar.showSnackbar("Changes saved") } }; Spacer(Modifier.height(16.dp)) } }
    }
}


@Composable private fun StatusBadge(text: String, color: Color, colors: SikoClawColors) {
    Surface(color = color.copy(alpha = .14f), shape = RoundedCornerShape(999.dp)) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
    }
}

@Composable private fun AppField(value: String, onValue: (String) -> Unit, label: String, colors: SikoClawColors, singleLine: Boolean = true, keyboardType: KeyboardType = KeyboardType.Text, visualPassword: Boolean = false, error: String? = null) { OutlinedTextField(value, onValue, Modifier.fillMaxWidth().padding(bottom = 10.dp), label = { Text(label) }, singleLine = singleLine, minLines = if (singleLine) 1 else 4, isError = error != null, supportingText = error?.let { { Text(it) } }, keyboardOptions = KeyboardOptions(keyboardType = keyboardType), visualTransformation = if (visualPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None, shape = RoundedCornerShape(12.dp), colors = appTextFieldColors(colors)) }

@Composable private fun appTextFieldColors(colors: SikoClawColors) = OutlinedTextFieldDefaults.colors(focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary, focusedBorderColor = colors.accent, unfocusedBorderColor = colors.inputBorder, focusedLabelColor = colors.accent, unfocusedLabelColor = colors.textSecondary, cursorColor = colors.accent, focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
