package com.sikoclaw.app.ui.settings

import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
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
import com.sikoclaw.app.cron.CronJob
import com.sikoclaw.app.cron.CronManager
import com.sikoclaw.app.mcp.McpManager
import com.sikoclaw.app.mcp.McpServer
import com.sikoclaw.app.tool.ToolRegistry
import com.sikoclaw.app.tool.terminal.InternalTerminal
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
        "user_prompt" -> PromptEditorScreen("User Prompt", "Instructions you always want Siko Claw to follow", KVUtils.getUserPrompt(), KVUtils::setUserPrompt, colors, onBack)
        "soul_prompt" -> PromptEditorScreen("Soul Prompt", "Personality, tone and identity", KVUtils.getSoulPrompt(), KVUtils::setSoulPrompt, colors, onBack)
        "memory" -> PromptEditorScreen("Memory", "Stable facts and preferences learned about you", KVUtils.getUserMemoryPrompt(), KVUtils::setUserMemoryPrompt, colors, onBack)
        "skills" -> SkillsScreen(colors, onBack)
        "mcp" -> McpScreen(colors, onBack)
        "cron" -> CronScreen(colors, onBack)
        "terminal" -> TerminalScreen(colors, onBack)
        else -> ToolsScreen(colors, onBack)
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
            if (custom.isEmpty()) item { SettingsCard(colors, Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.AutoAwesome, null, tint = colors.accent); Spacer(Modifier.height(8.dp)); Text("No custom skills yet", color = colors.textPrimary, fontWeight = FontWeight.Medium); Text("Create a reusable workflow for Siko Claw.", color = colors.textSecondary, fontSize = 13.sp); TextButton({ creating = true }) { Text("Create skill", color = colors.accent) } } } }
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
    var servers by remember { mutableStateOf(McpManager.all().toList()) }; var edit by remember { mutableStateOf<McpServer?>(null) }; var showAdd by remember { mutableStateOf(false) }; var connecting by remember { mutableStateOf<String?>(null) }; var statuses by remember { mutableStateOf<Map<String, String>>(emptyMap()) }; var deleteTarget by remember { mutableStateOf<McpServer?>(null) }; val scope = rememberCoroutineScope()
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
    AppScreenScaffold("MCP Servers", colors, onBack, actions = { IconButton({ showAdd = true }) { Icon(Icons.Outlined.Add, "Add MCP server", tint = colors.accent) } }) { pad ->
        if (servers.isEmpty()) EmptyState(Icons.Outlined.Dns, "No MCP servers configured", "Connect an MCP server to extend Siko Claw with external tools and data.", "Add MCP server", colors, Modifier.padding(pad)) { showAdd = true }
        else LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { items(servers, key = { it.id }) { s ->
            SettingsCard(colors, Modifier.fillMaxWidth()) { SettingsRow(s.name, s.url, colors, trailing = { StatusBadge(statuses[s.id] ?: if (s.enabled) "Enabled" else "Disabled", if (statuses[s.id]?.startsWith("Connected") == true) Color(0xFF34D399) else colors.textSecondary, colors) }); Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { TextButton({ McpManager.upsert(s.copy(enabled = !s.enabled)); refresh() }) { Text(if (s.enabled) "Disable" else "Enable", color = colors.accent) }; TextButton({ edit = s }) { Text("Edit", color = colors.textSecondary) }; TextButton({ connecting = s.id; scope.launch { val r = withContext(Dispatchers.IO) { runCatching { McpManager.connect(s) } }; statuses = statuses + (s.id to if (r.isSuccess) "Connected · ${r.getOrDefault(0)} tools" else "Error"); connecting = null } }) { if (connecting == s.id) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Test", color = colors.textSecondary) }; Spacer(Modifier.weight(1f)); IconButton({ deleteTarget = s }) { Icon(Icons.Outlined.Delete, "Delete", tint = Color(0xFFF87171)) } } }
        } }
    }
}

@Composable
private fun McpDialog(old: McpServer?, colors: SikoClawColors, dismiss: () -> Unit, connect: suspend (McpServer) -> String?) {
    var name by remember { mutableStateOf(old?.name.orEmpty()) }; var url by remember { mutableStateOf(old?.url.orEmpty()) }; var token by remember { mutableStateOf(old?.token.orEmpty()) }; var loading by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope(); val validUrl = url.startsWith("https://") || url.startsWith("http://")
    AlertDialog(dismiss, title = { Text(if (old == null) "Add MCP Server" else "Edit MCP Server", color = colors.textPrimary) }, text = { Column(Modifier.verticalScroll(rememberScrollState())) { AppField(name, { name = it; error = null }, "Server name", colors); AppField(url, { url = it; error = null }, "Endpoint URL", colors, keyboardType = KeyboardType.Uri, error = if (url.isNotBlank() && !validUrl) "Enter a valid HTTP or HTTPS URL" else null); AppField(token, { token = it }, "Bearer token (optional)", colors, visualPassword = true); error?.let { Text(it, color = Color(0xFFF87171), fontSize = 13.sp) } } }, confirmButton = { PrimaryActionButton("Save & connect", colors, enabled = name.isNotBlank() && url.isNotBlank() && validUrl, loading = loading) { loading = true; error = null; scope.launch { val server = McpServer(old?.id ?: "m${System.currentTimeMillis()}", name.trim(), url.trim(), token, old?.enabled ?: true); error = connect(server); loading = false } } }, dismissButton = { TextButton(dismiss) { Text("Cancel", color = colors.textSecondary) } }, modifier = Modifier.imePadding(), containerColor = colors.surface)
}

@Composable
private fun CronScreen(colors: SikoClawColors, onBack: () -> Unit) {
    val context = LocalContext.current; var jobs by remember { mutableStateOf(CronManager.all().toList()) }; var edit by remember { mutableStateOf<CronJob?>(null) }; var add by remember { mutableStateOf(false) }; var deleteTarget by remember { mutableStateOf<CronJob?>(null) }; fun refresh() { jobs = CronManager.all().toList() }
    if (add || edit != null) CronDialog(edit, colors, { add = false; edit = null }, { CronManager.upsert(context, it); add = false; edit = null; refresh() })
    deleteTarget?.let { job -> AlertDialog({ deleteTarget = null }, title = { Text("Delete cron job?") }, text = { Text(job.name) }, confirmButton = { TextButton({ CronManager.delete(context, job.id); deleteTarget = null; refresh() }) { Text("Delete", color = Color(0xFFF87171)) } }, dismissButton = { TextButton({ deleteTarget = null }) { Text("Cancel") } }, containerColor = colors.surface) }
    AppScreenScaffold("Cron Jobs", colors, onBack, actions = { IconButton({ add = true }) { Icon(Icons.Outlined.Add, "Add cron job", tint = colors.accent) } }) { pad ->
        if (jobs.isEmpty()) EmptyState(Icons.Outlined.Schedule, "No scheduled jobs", "Create recurring tasks for Siko Claw to run automatically.", "Add cron job", colors, Modifier.padding(pad)) { add = true }
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

@Composable
private fun TerminalScreen(colors: SikoClawColors, onBack: () -> Unit) {
    var output by remember { mutableStateOf("Siko terminal ready\n${InternalTerminal.status()}") }; var command by remember { mutableStateOf("") }; var running by remember { mutableStateOf(false) }; var expanded by remember { mutableStateOf(false) }; var agentAccess by remember { mutableStateOf(KVUtils.isToolEnabled("internal_terminal")) }; val scope = rememberCoroutineScope(); val scroll = rememberScrollState()
    fun run(cmd: String) { if (running || cmd.isBlank()) return; output += "\n\n$ $cmd"; command = ""; running = true; scope.launch { val result = withContext(Dispatchers.IO) { InternalTerminal.run(cmd, InternalTerminal.linuxInstalled(), 120) }; val body = if (result.isSuccess) formatTerminalResult(result.data.orEmpty()) else result.error.orEmpty(); output += "\n$body"; running = false } }
    LaunchedEffect(output) { scroll.animateScrollTo(scroll.maxValue) }
    val clipboard = LocalClipboardManager.current
    AppScreenScaffold("Terminal", colors, onBack, actions = { IconButton({ clipboard.setText(AnnotatedString(output)) }) { Icon(Icons.Outlined.ContentCopy, "Copy output", tint = colors.textSecondary) }; IconButton({ output = "" }) { Icon(Icons.Outlined.DeleteSweep, "Clear terminal", tint = colors.textSecondary) } }) { pad ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp).navigationBarsPadding().imePadding()) {
            val landscape = maxWidth > maxHeight
            if (landscape) {
                Row(Modifier.fillMaxSize().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.widthIn(max = 360.dp).weight(.42f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TerminalAccessCard(agentAccess, colors) { agentAccess = it; KVUtils.setToolEnabled("internal_terminal", it) }
                        AlpineEnvironmentCard(expanded, { expanded = !expanded }, running, colors, ::run)
                    }
                    Column(Modifier.weight(.58f)) {
                        TerminalOutput(output, colors, Modifier.fillMaxWidth().weight(1f), scroll)
                        Spacer(Modifier.height(10.dp))
                        CommandComposer(command, { command = it }, running, colors, ::run)
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Spacer(Modifier.height(12.dp))
                    TerminalAccessCard(agentAccess, colors) { agentAccess = it; KVUtils.setToolEnabled("internal_terminal", it) }
                    Spacer(Modifier.height(12.dp))
                    TerminalOutput(output, colors, Modifier.fillMaxWidth().weight(1f), scroll)
                    Spacer(Modifier.height(10.dp))
                    CommandComposer(command, { command = it }, running, colors, ::run)
                    Spacer(Modifier.height(10.dp))
                    AlpineEnvironmentCard(expanded, { expanded = !expanded }, running, colors, ::run)
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun TerminalAccessCard(enabled: Boolean, colors: SikoClawColors, onToggle: (Boolean) -> Unit) {
    SettingsCard(colors, Modifier.fillMaxWidth()) { ToggleSettingsRow("Agent terminal access", "Allow the agent to run commands in its private workspace.", enabled, colors, onToggle) }
}

@Composable
private fun AlpineEnvironmentCard(expanded: Boolean, onExpand: () -> Unit, running: Boolean, colors: SikoClawColors, run: (String) -> Unit) {
    SettingsCard(colors, Modifier.fillMaxWidth()) {
        SettingsRow("Alpine Environment", InternalTerminal.status(), colors, trailing = { Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = colors.textSecondary) }, onClick = onExpand)
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ run("apk update && apk add git ca-certificates") }, enabled = !running, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, colors.accent)) { Text("Install Git", color = colors.accent) }
                    OutlinedButton({ run("apk update && apk upgrade") }, enabled = !running, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, colors.inputBorder)) { Text("Update packages", color = colors.textSecondary) }
                }
                Text("Alpine Mini RootFS is prepared automatically. Packages are installed only when needed.", color = colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 10.dp))
            }
        }
    }
}

@Composable fun TerminalOutput(output: String, colors: SikoClawColors, modifier: Modifier, scroll: androidx.compose.foundation.ScrollState) { Surface(modifier, RoundedCornerShape(14.dp), color = Color(0xFF050914), border = BorderStroke(1.dp, colors.inputBorder)) { SelectionContainer { Text(output.ifBlank { "Terminal cleared" }, color = Color(0xFFB9D6FF), fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(12.dp)) } } }

@Composable fun CommandComposer(text: String, onText: (String) -> Unit, running: Boolean, colors: SikoClawColors, run: (String) -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(text, onText, Modifier.weight(1f).heightIn(min = 52.dp), placeholder = { Text("Type a command") }, singleLine = true, shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { run(text) }), colors = appTextFieldColors(colors)); Spacer(Modifier.width(8.dp)); AsyncActionButton("Run", running, text.isNotBlank(), colors) { run(text) } } }

@Composable fun AsyncActionButton(text: String, loading: Boolean, enabled: Boolean, colors: SikoClawColors, onClick: () -> Unit) = PrimaryActionButton(text, colors, Modifier, enabled, loading, onClick)

@Composable fun StatusBadge(text: String, color: Color, colors: SikoClawColors) { Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = .14f)) { Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), maxLines = 1) } }

private fun formatTerminalResult(raw: String): String { val exit = Regex("exit=(\\d+)").find(raw)?.groupValues?.get(1); val out = raw.substringAfter("stdout:\n", "").substringBefore("\nstderr:").trim(); val err = raw.substringAfter("stderr:\n", "").trim(); return buildString { if (out.isNotBlank()) append(out); if (err.isNotBlank()) { if (isNotEmpty()) append('\n'); append(err) }; exit?.let { if (isNotEmpty()) append('\n'); append("exit code: $it") } }.ifBlank { "Done" } }

@Composable private fun AppField(value: String, onValue: (String) -> Unit, label: String, colors: SikoClawColors, singleLine: Boolean = true, keyboardType: KeyboardType = KeyboardType.Text, visualPassword: Boolean = false, error: String? = null) { OutlinedTextField(value, onValue, Modifier.fillMaxWidth().padding(bottom = 10.dp), label = { Text(label) }, singleLine = singleLine, minLines = if (singleLine) 1 else 4, isError = error != null, supportingText = error?.let { { Text(it) } }, keyboardOptions = KeyboardOptions(keyboardType = keyboardType), visualTransformation = if (visualPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None, shape = RoundedCornerShape(12.dp), colors = appTextFieldColors(colors)) }

@Composable private fun appTextFieldColors(colors: SikoClawColors) = OutlinedTextFieldDefaults.colors(focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary, focusedBorderColor = colors.accent, unfocusedBorderColor = colors.inputBorder, focusedLabelColor = colors.accent, unfocusedLabelColor = colors.textSecondary, cursorColor = colors.accent, focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
