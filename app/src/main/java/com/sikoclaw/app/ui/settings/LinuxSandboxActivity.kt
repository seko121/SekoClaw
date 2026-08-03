/*
 * Linux Sandbox UI adapted from Kai by Simon Schubert and contributors.
 * Original project licensed under Apache License 2.0.
 */
package com.sikoclaw.app.ui.settings

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sikoclaw.app.linux.*
import com.sikoclaw.app.linux.kai.LinuxCompatibilityMode
import com.sikoclaw.app.linux.kai.LinuxCompatibilitySettings
import com.sikoclaw.app.linux.ui.*
import com.sikoclaw.app.ui.chat.SikoClawColors
import com.sikoclaw.app.ui.chat.ThemeManager
import com.sikoclaw.app.utils.KVUtils
import kotlinx.coroutines.launch

class LinuxSandboxActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply { isAppearanceLightStatusBars = !ThemeManager.isDark(); isAppearanceLightNavigationBars = !ThemeManager.isDark() }
        window.statusBarColor = ThemeManager.getColors().toolbarBg
        setContent {
            val colors = with(ThemeManager) { getColors().toComposeColors() }
            MaterialTheme(colorScheme = darkColorScheme(primary = colors.accent, background = colors.background, surface = colors.surface)) {
                LinuxSandboxScreen(colors, intent.getBooleanExtra(EXTRA_TERMINAL_SETTINGS, false)) { finish() }
            }
        }
    }
}

const val EXTRA_TERMINAL_SETTINGS = "terminal_settings"

private enum class SandboxTab { Terminal, Files, Packages }

@Composable
private fun LinuxSandboxScreen(colors: SikoClawColors, showSettings: Boolean, onBack: () -> Unit) {
    val controller = remember { createSandboxController() }
    val status by controller.status.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var tab by rememberSaveable { mutableStateOf(SandboxTab.Terminal) }
    AppScreenScaffold(
        title = if (showSettings) "Terminal Settings" else "Linux Terminal",
        colors = colors,
        onBack = onBack,
        actions = {
            if (!showSettings) IconButton({
                context.startActivity(android.content.Intent(context, LinuxSandboxActivity::class.java).putExtra(EXTRA_TERMINAL_SETTINGS, true))
            }) {
                Icon(Icons.Outlined.Settings, "Terminal settings", tint = colors.textSecondary)
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (showSettings) {
                SandboxStatusCard(controller, status, colors)
                Text("Install, remove and package controls live here. The terminal stays focused on your session.", color = colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                return@AppScreenScaffold
            }
            if (status.ready) {
                TabRow(selectedTabIndex = tab.ordinal, containerColor = colors.background, contentColor = colors.accent) {
                    SandboxTab.entries.forEach { item ->
                        Tab(selected = tab == item, onClick = { tab = item }, text = { Text(item.name) })
                    }
                }
                when (tab) {
                    SandboxTab.Terminal -> TerminalTab(controller, colors, Modifier.weight(1f))
                    SandboxTab.Files -> FilesTab(controller, colors, Modifier.weight(1f))
                    SandboxTab.Packages -> PackagesTab(controller, colors, Modifier.weight(1f))
                }
            } else if (!status.working) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
                        Icon(Icons.Outlined.Terminal, null, tint = colors.accent, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Install Alpine Linux to use Terminal, Files and Packages.", color = colors.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SandboxStatusCard(controller: SandboxController, status: SandboxStatus, colors: SikoClawColors) {
    var enabled by remember { mutableStateOf(KVUtils.getBoolean("LINUX_SANDBOX_ENABLED", true)) }
    var confirmReset by remember { mutableStateOf(false) }
    var compatibilityMenu by remember { mutableStateOf(false) }
    var compatibility by remember { mutableStateOf(LinuxCompatibilitySettings.mode()) }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(colors.surface),
        border = BorderStroke(1.dp, colors.inputBorder),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Alpine Linux", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(if (status.ready) "Ready · ${status.diskUsageMB} MB" else status.statusText.ifBlank { "Not installed" }, color = if (status.error) MaterialTheme.colorScheme.error else colors.textSecondary, fontSize = 13.sp)
                }
                if (status.ready) Switch(enabled, { enabled = it; KVUtils.putBoolean("LINUX_SANDBOX_ENABLED", it) })
            }
            if (status.working) {
                if (status.progress != null) LinearProgressIndicator({ status.progress!! }, Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(status.statusText, color = colors.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton({ controller.cancel() }) { Text("Cancel") }
                }
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!status.ready) Button({ controller.setup() }) { Text(if (status.error) "Retry" else "Install") }
                    if (status.ready && !status.packagesInstalled) OutlinedButton({ controller.installPackages() }) { Text("Install basic packages") }
                    if (status.ready) OutlinedButton({ confirmReset = true }) { Text("Uninstall") }
                }
            }
            Box {
                TextButton(onClick = { compatibilityMenu = true }, contentPadding = PaddingValues(horizontal = 2.dp)) {
                    Icon(Icons.Outlined.Security, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Compatibility: ${compatibility.label}", fontSize = 12.sp)
                }
                DropdownMenu(compatibilityMenu, { compatibilityMenu = false }) {
                    LinuxCompatibilityMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Column { Text(mode.label); Text(mode.description, fontSize = 11.sp, color = colors.textSecondary) } },
                            onClick = {
                                compatibility = mode
                                LinuxCompatibilitySettings.setMode(mode)
                                compatibilityMenu = false
                            },
                            leadingIcon = { if (mode == compatibility) Icon(Icons.Outlined.Check, null) },
                        )
                    }
                }
            }
        }
    }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Uninstall Linux Sandbox?") },
        text = { Text("The Alpine environment and all files stored inside it will be deleted.") },
        confirmButton = { TextButton({ confirmReset = false; controller.reset() }) { Text("Uninstall", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirmReset = false }) { Text("Cancel") } },
    )
}

@Composable
private fun TerminalTab(controller: SandboxController, colors: SikoClawColors, modifier: Modifier = Modifier) {
    val vm: SandboxSessionViewModel = viewModel { SandboxSessionViewModel(controller) }
    val input by vm.inputText.collectAsStateWithLifecycle()
    val running by vm.isRunning.collectAsStateWithLifecycle()
    val lines = vm.outputLines
    val clipboard = LocalClipboardManager.current
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(lines.size, lines.lastOrNull()) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }
    Column(modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Card(Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(Color(0xFF080B10)), border = BorderStroke(1.dp, colors.inputBorder)) {
            SelectionContainer {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (lines.isEmpty()) item { Text("OctoBot Linux terminal ready\n~ $", color = Color(0xFF8BD5CA), fontFamily = FontFamily.Monospace, fontSize = 13.sp) }
                    items(lines) { line -> Text(lineText(line), color = lineColor(line), fontFamily = FontFamily.Monospace, fontSize = 13.sp) }
                }
            }
        }
        // The Activity uses adjustResize; adding IME height again here would double-consume
        // the keyboard inset and collapse the terminal viewport on small screens.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = vm::setInputText,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                placeholder = { Text(if (running) "Send input to running command" else "Type a command") },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { vm.submit() }),
            )
            FilledIconButton(onClick = { if (running) vm.cancelRunning() else vm.submit() }, enabled = running || input.isNotBlank(), modifier = Modifier.size(48.dp)) {
                Icon(if (running) Icons.Outlined.Stop else Icons.Outlined.Send, if (running) "Stop command" else "Run command")
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AssistChip(vm::previousHistory, { Text("↑ History") })
            AssistChip(vm::nextHistory, { Text("↓ History") })
            AssistChip({ vm.setInputText(input + "\t") }, { Text("Tab") })
            AssistChip({ if (running) vm.cancelRunning() }, { Text("Ctrl+C") }, enabled = running)
            AssistChip({ clipboard.setText(AnnotatedString(lines.joinToString("\n") { lineText(it) })) }, { Text("Copy") })
            AssistChip({ controller.clearTranscript(SandboxSessions.TERMINAL) }, { Text("Clear") })
        }
    }
}

private fun lineText(line: TerminalLine): String = when (line) {
    is TerminalLine.Command -> "~ $ ${line.text}"
    is TerminalLine.Output -> line.text
    is TerminalLine.Error -> line.text
}
private fun lineColor(line: TerminalLine): Color = when (line) {
    is TerminalLine.Command -> Color(0xFF8BD5CA)
    is TerminalLine.Error -> Color(0xFFFF8A80)
    is TerminalLine.Output -> Color(0xFFE3EAF4)
}

@Composable
private fun FilesTab(controller: SandboxController, colors: SikoClawColors, modifier: Modifier = Modifier) {
    val vm: SandboxFileBrowserViewModel = viewModel { SandboxFileBrowserViewModel(controller) }
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showHidden by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }
    var createFolder by remember { mutableStateOf<Boolean?>(null) }
    var exportPath by remember { mutableStateOf<String?>(null) }
    var transferEntry by remember { mutableStateOf<SandboxFileEntry?>(null) }
    var transferMove by remember { mutableStateOf(false) }
    var transferDestination by rememberSaveable { mutableStateOf("") }
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { controller.importFile(uri.toString(), state.currentPath.trimEnd('/') + "/" + (uri.lastPathSegment?.substringAfterLast('/') ?: "imported-file")); vm.refresh() }
    }
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val path = exportPath
        if (uri != null && path != null) scope.launch { controller.exportFile(path, uri.toString()) }
    }
    LaunchedEffect(Unit) { vm.start("/") }
    Column(modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(state.currentPath, color = colors.textSecondary, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton({ showHidden = !showHidden }) { Icon(if (showHidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, "Show hidden files") }
            IconButton({ importLauncher.launch(arrayOf("*/*")) }) { Icon(Icons.Outlined.FileDownload, "Import file") }
            IconButton({ createFolder = true }) { Icon(Icons.Outlined.CreateNewFolder, "New folder") }
            IconButton({ createFolder = false }) { Icon(Icons.Outlined.NoteAdd, "New file") }
        }
        if (state.currentPath != "/") TextButton({ vm.navigateTo(state.currentPath.substringBeforeLast('/').ifBlank { "/" }) }) { Text("..  Parent folder") }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.entries.filter { showHidden || !it.name.startsWith('.') }, key = { it.path }) { entry ->
                ListItem(
                    headlineContent = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(if (entry.isDirectory) "Folder" else "${entry.sizeBytes} bytes") },
                    leadingContent = { Icon(if (entry.isDirectory) Icons.Outlined.Folder else Icons.Outlined.InsertDriveFile, null, tint = colors.accent) },
                    trailingContent = {
                        Row {
                            if (!entry.isDirectory) IconButton({ exportPath = entry.path; exportLauncher.launch(entry.name) }) { Icon(Icons.Outlined.FileUpload, "Export") }
                            IconButton({ transferEntry = entry; transferMove = false; transferDestination = state.currentPath.trimEnd('/') + "/${entry.name}-copy" }) { Icon(Icons.Outlined.ContentCopy, "Copy") }
                            IconButton({ transferEntry = entry; transferMove = true; transferDestination = entry.path }) { Icon(Icons.Outlined.DriveFileMove, "Move") }
                            IconButton({ vm.requestRename(entry) }) { Icon(Icons.Outlined.Edit, "Rename") }
                            IconButton({ vm.requestDelete(entry) }) { Icon(Icons.Outlined.Delete, "Delete") }
                        }
                    },
                    modifier = Modifier.clickable { vm.openEntry(entry) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider(color = colors.divider)
            }
        }
    }
    val editor = state.editor
    if (editor is EditorState.Loaded) AlertDialog(
        onDismissRequest = { vm.navigateTo(state.currentPath) },
        title = { Text(editor.path.substringAfterLast('/')) },
        text = { OutlinedTextField(editor.current, vm::updateEditorContent, Modifier.fillMaxWidth().heightIn(min = 260.dp), textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)) },
        confirmButton = { TextButton({ vm.save() }, enabled = editor.dirty) { Text("Save") } },
        dismissButton = { TextButton({ vm.navigateTo(state.currentPath) }) { Text("Close") } },
    )
    state.pendingDelete?.let { entry -> AlertDialog(onDismissRequest = vm::cancelDelete, title = { Text("Delete ${entry.name}?") }, confirmButton = { TextButton(vm::confirmDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(vm::cancelDelete) { Text("Cancel") } }) }
    state.renaming?.let { rename -> AlertDialog(onDismissRequest = vm::cancelRename, title = { Text("Rename") }, text = { OutlinedTextField(rename.input, vm::updateRenameInput, isError = rename.error != null, supportingText = { rename.error?.let { Text(it) } }) }, confirmButton = { TextButton(vm::confirmRename) { Text("Rename") } }, dismissButton = { TextButton(vm::cancelRename) { Text("Cancel") } }) }
    if (createFolder != null) AlertDialog(onDismissRequest = { createFolder = null }, title = { Text(if (createFolder == true) "New folder" else "New file") }, text = { OutlinedTextField(newName, { newName = it }, singleLine = true) }, confirmButton = { TextButton({ val path = state.currentPath.trimEnd('/') + "/" + newName; scope.launch { if (createFolder == true) controller.createDirectory(path) else controller.writeTextFile(path, ""); vm.refresh() }; newName = ""; createFolder = null }, enabled = newName.isNotBlank()) { Text("Create") } }, dismissButton = { TextButton({ createFolder = null }) { Text("Cancel") } })
    transferEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { transferEntry = null },
            title = { Text(if (transferMove) "Move ${entry.name}" else "Copy ${entry.name}") },
            text = { OutlinedTextField(transferDestination, { transferDestination = it }, label = { Text("Destination path") }, singleLine = true) },
            confirmButton = { TextButton({ scope.launch { if (transferMove) controller.moveEntry(entry.path, transferDestination) else controller.copyEntry(entry.path, transferDestination); vm.refresh() }; transferEntry = null }, enabled = transferDestination.startsWith('/')) { Text(if (transferMove) "Move" else "Copy") } },
            dismissButton = { TextButton({ transferEntry = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PackagesTab(controller: SandboxController, colors: SikoClawColors, modifier: Modifier = Modifier) {
    val vm: SandboxPackagesViewModel = viewModel { SandboxPackagesViewModel(controller) }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.start() }
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(state.searchQuery, vm::updateSearchQuery, Modifier.weight(1f), label = { Text("Search Alpine packages") }, singleLine = true)
            IconButton(vm::refreshInstalled) { Icon(Icons.Outlined.Refresh, "Refresh installed packages") }
            TextButton(vm::upgradePackages, enabled = !state.upgrading) { Text("Upgrade all") }
        }
        if (state.searching || state.loadingInstalled || state.upgrading) LinearProgressIndicator(Modifier.fillMaxWidth())
        val rows = if (state.searchQuery.isBlank()) state.installed else state.searchResults
        Text(if (state.searchQuery.isBlank()) "Installed (${state.installed.size})" else "Results (${rows.size})", color = colors.textSecondary, fontSize = 13.sp)
        LazyColumn(Modifier.fillMaxSize()) {
            items(rows, key = { it.name }) { pkg ->
                val installed = pkg.name in state.installedNames
                ListItem(
                    headlineContent = { Text(pkg.name) },
                    supportingContent = { Text(pkg.description ?: pkg.version, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    trailingContent = {
                        if (pkg.name in state.mutating) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        else TextButton({ if (installed) vm.requestUninstall(pkg) else vm.install(pkg) }, enabled = !installed || pkg.name !in SandboxRequiredPackages.NAMES) { Text(if (installed) "Remove" else "Install") }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider(color = colors.divider)
            }
        }
    }
    state.pendingUninstall?.let { pkg -> AlertDialog(onDismissRequest = vm::cancelUninstall, title = { Text("Remove ${pkg.name}?") }, confirmButton = { TextButton(vm::confirmUninstall) { Text("Remove", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(vm::cancelUninstall) { Text("Cancel") } }) }
}
