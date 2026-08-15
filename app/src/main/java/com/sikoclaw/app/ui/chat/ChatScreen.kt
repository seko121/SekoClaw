// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package com.sikoclaw.app.ui.chat

import android.app.Activity
import android.content.ContentValues
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.sikoclaw.app.R
import com.sikoclaw.app.agent.skill.Skill
import com.sikoclaw.app.agent.skill.SkillCategory
import com.sikoclaw.app.agent.skill.SkillRegistry
import com.sikoclaw.app.plugin.PluginCatalog
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import com.sikoclaw.app.utils.XLog
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * OctoBot Chat Screen â€” Jetpack Compose
 * Inspired by WhatsApp/Telegram/Slack dark theme
 */

// ======================== THEME COLORS ========================

data class SikoClawColors(
    val background: Color,
    val surface: Color,
    val userBubble: Color,
    val userText: Color,
    val aiBubble: Color,
    val aiBubbleBorder: Color,
    val aiText: Color,
    val avatar: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    val inputBorder: Color,
)

val AbyssDark = SikoClawColors(
    background = Color(0xFF0C111B),
    surface = Color(0xFF151D2E),
    userBubble = Color(0xFF2563EB),
    userText = Color.White,
    aiBubble = Color(0xFF1E2D45),
    aiBubbleBorder = Color(0xFF2A3D5A),
    aiText = Color(0xFFD0DAE8),
    avatar = Color(0xFF1D4ED8),
    accent = Color(0xFF60A5FA),
    textPrimary = Color(0xFFECECF1),
    textSecondary = Color(0xFFA3A3B5),
    textTertiary = Color(0xFF52526E),
    divider = Color(0xFF1A2234),
    inputBorder = Color(0xFF1E293B),
)

private fun Modifier.dismissKeyboardOnBackgroundTap(onDismissKeyboard: () -> Unit): Modifier =
    pointerInput(onDismissKeyboard) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            val up = waitForUpOrCancellation()
            if (up != null) {
                onDismissKeyboard()
            }
        }
    }

// ======================== MAIN SCREEN ========================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    modelStatus: String,
    needsPermission: Boolean,
    isAwaitingReply: Boolean,
    isTaskRunning: Boolean,
    isDownloading: Boolean = false,
    downloadProgress: Int = 0,
    isLocalModel: Boolean = true,
    sessionTokens: Int = 0,
    sessionCost: Double = 0.0,
    onSendChat: (String) -> Unit,
    onSendTask: (String) -> Unit,
    onSteerTask: (String) -> Unit,
    onQueueTask: (String) -> Unit,
    onStartMonitor: (MonitorTargetSpec) -> Unit = {},
    onSendDirectMessage: (contact: String, app: String, message: String) -> Unit = { _, _, _ -> },
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenBrowser: () -> Unit,
    onStartVoiceCall: () -> Unit = {},
    onFixPermissions: () -> Unit,
    onAttach: () -> Unit,
    conversations: List<ChatHistoryManager.ConversationSummary>,
    onSelectConversation: (ChatHistoryManager.ConversationSummary) -> Unit,
    onSearchResult: (ChatDatabase.SearchResult) -> Unit = {},
    scrollToMessageTimestamp: Long? = null,
    onDeleteConversation: (ChatHistoryManager.ConversationSummary) -> Unit = {},
    onRenameConversation: (ChatHistoryManager.ConversationSummary, String) -> Unit = { _, _ -> },
    activeTasks: List<String> = emptyList(),
    onStopTask: (String) -> Unit = {},
    onStopAllTasks: () -> Unit = {},
    inputEnabled: Boolean = true,
    onModelSwitch: (modelId: String, displayName: String) -> Unit = { _, _ -> },
    colors: SikoClawColors = AbyssDark,
) {
    val approvalRequest by com.sikoclaw.app.agent.HumanApprovalManager.pending.collectAsState()
    val memoryCaptureRequest by com.sikoclaw.app.agent.memory.MemoryCaptureApproval.pending.collectAsState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val dismissKeyboard = {
        keyboardController?.hide()
        focusManager.clearFocus()
    }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    // Shared state for prompt chip â†’ input bar prefill
    var prefillText by remember { mutableStateOf("") }
    var prefillIsTask by remember { mutableStateOf(false) }
    // Task mode state â€” lifted here so content area can react
    var isTaskMode by remember { mutableStateOf(false) }
    // Local/Cloud tab â€” controls UI presentation AND triggers model switch.
    // Keep the tab aligned with the actual active model so returning from
    // Settings/model changes cannot leave the toolbar UI out of sync.
    var selectedTab by remember { mutableStateOf(if (isLocalModel) "local" else "cloud") }
    val isLocalUI = selectedTab == "local"
    // Skill dialog and activation states
    var showMonitorSheet by remember { mutableStateOf(false) }
    var showSendSheet by remember { mutableStateOf(false) }
    var activatingSkill by remember { mutableStateOf<String?>(null) }

    // Chat mode is always the default â€” user can switch to Task manually

    // When activating finishes (2s animation), clear state
    LaunchedEffect(activatingSkill) {
        if (activatingSkill != null) {
            kotlinx.coroutines.delay(2000)
            activatingSkill = null
        }
    }

    LaunchedEffect(isLocalModel) {
        selectedTab = if (isLocalModel) "local" else "cloud"
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = colors.surface,
            ) {
                SidebarContent(
                    conversations = conversations,
                    onNewChat = {
                        scope.launch { drawerState.close() }
                        onNewChat()
                    },
                    onSelectConversation = {
                        scope.launch { drawerState.close() }
                        onSelectConversation(it)
                    },
                    onDeleteConversation = onDeleteConversation,
                    onRenameConversation = onRenameConversation,
                    onSearchResult = {
                        scope.launch { drawerState.close() }
                        onSearchResult(it)
                    },
                    onSettings = {
                        scope.launch { drawerState.close() }
                        onOpenSettings()
                    },
                    onModels = {
                        scope.launch { drawerState.close() }
                        onOpenModels()
                    },
                    colors = colors,
                )
            }
        }
    ) {
        Scaffold(
            containerColor = colors.background,
            topBar = {
                Column(
                    modifier = Modifier.dismissKeyboardOnBackgroundTap(dismissKeyboard)
                ) {
                    ChatTopBar(
                        modelStatus = modelStatus,
                        sessionTokens = sessionTokens,
                        sessionCost = sessionCost,
                        isLocalModel = isLocalModel,
                        selectedTab = selectedTab,
                        onTabChange = { tab ->
                            selectedTab = tab
                            val kvUtils = com.sikoclaw.app.utils.KVUtils
                            if (tab == "cloud") {
                                // Check if cloud default model is configured
                                if (kvUtils.hasDefaultCloudModel()) {
                                    val modelId = kvUtils.getDefaultCloudModel()
                                    val provider = com.sikoclaw.app.agent.CloudProvider.fromName(
                                        kvUtils.getDefaultCloudProvider().ifBlank { kvUtils.getLlmProvider() }
                                    )
                                    val displayName = provider.models.find { it.id == modelId }?.displayName ?: modelId
                                    onModelSwitch(modelId, displayName)
                                } else {
                                    // No cloud model configured â€” signal "no model" state
                                    com.sikoclaw.app.utils.XLog.i("ChatScreen", "Cloud tab: no default cloud model configured")
                                    onModelSwitch("NONE", "")
                                }
                            } else {
                                // Check if local default model is configured
                                if (kvUtils.hasDefaultLocalModel()) {
                                    val localPath = kvUtils.getLocalModelPath()
                                    val name = java.io.File(localPath).nameWithoutExtension
                                        .replace("-", " ").replace("_", " ")
                                    onModelSwitch("LOCAL", name)
                                } else {
                                    // No local model configured â€” signal "no model" state
                                    com.sikoclaw.app.utils.XLog.i("ChatScreen", "Local tab: no default local model configured")
                                    onModelSwitch("NONE", "")
                                }
                            }
                        },
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onSettings = onOpenSettings,
                        onModels = onOpenModels,
                        onBrowser = onOpenBrowser,
                        onStartVoiceCall = onStartVoiceCall,
                        onModelSwitch = onModelSwitch,
                        colors = colors,
                    )
                    if (activeTasks.isNotEmpty()) {
                        ActiveTaskBar(
                            tasks = activeTasks,
                            onStopTask = onStopTask,
                            onStopAll = onStopAllTasks,
                            colors = colors,
                        )
                    }
                }
            },
            bottomBar = {
                if (!isDownloading) {
                    Column(
                        modifier = Modifier.imePadding()
                    ) {
                        // Quick Tasks collapsible panel (v9 style)
                        QuickTasksPanel(
                            isLocalModel = isLocalUI,
                            onFillTask = { text ->
                                prefillText = text
                                prefillIsTask = true
                                if (isLocalUI) isTaskMode = true
                            },
                            onMonitorClick = { showMonitorSheet = true },
                            monitorActive = activeTasks.isNotEmpty(),
                            colors = colors,
                        )

                        approvalRequest?.let { request ->
                            HumanApprovalBar(request, colors) { allowed ->
                                com.sikoclaw.app.agent.HumanApprovalManager.resolve(request.id, allowed)
                            }
                        }
                        memoryCaptureRequest?.let { request ->
                            MemoryCaptureApprovalBar(request, colors) { allowed ->
                                com.sikoclaw.app.agent.memory.MemoryCaptureApproval.resolve(request.id, allowed)
                            }
                        }

                        ChatInputBar(
                            isAwaitingReply = isAwaitingReply,
                            isTaskRunning = isTaskRunning,
                            inputEnabled = inputEnabled,
                            isTaskMode = isTaskMode,
                            isLocalModel = isLocalUI,
                            onTaskModeChange = { isTaskMode = it },
                            onSendChat = onSendChat,
                            onSendTask = onSendTask,
                            onSteerTask = onSteerTask,
                            onQueueTask = onQueueTask,
                            onStopAll = onStopAllTasks,
                            onAttach = onAttach,
                            onOpenBrowser = onOpenBrowser,
                            colors = colors,
                            prefillText = prefillText,
                            prefillIsTask = prefillIsTask,
                            onPrefillConsumed = { prefillText = "" },
                        )
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .dismissKeyboardOnBackgroundTap(dismissKeyboard)
            ) {
                if (!isDownloading) {
                    // v9: always show messages or empty state regardless of mode
                    val userMessages = messages.filter { it.role != ChatMessage.Role.SYSTEM }
                    if (userMessages.isEmpty()) {
                        EmptyStateWithPrompts(
                            isLocalModel = isLocalUI,
                            onSelectPrompt = { text, isTask ->
                                prefillText = text
                                prefillIsTask = isTask
                                if (isTask && isLocalUI) isTaskMode = true
                            },
                            colors = colors,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        MessageList(
                            messages = messages,
                            colors = colors,
                            onBackgroundTap = dismissKeyboard,
                            scrollToTimestamp = scrollToMessageTimestamp,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                // Download blocking overlay
                if (isDownloading) {
                    DownloadOverlay(progress = downloadProgress, colors = colors)
                }
            }
        }
    }

    // Monitor skill dialog
    if (showMonitorSheet) {
        MonitorDialog(
            onDismiss = { showMonitorSheet = false },
            onStart = { target ->
                showMonitorSheet = false
                activatingSkill = "monitor"
                onStartMonitor(target)
            },
            colors = colors,
        )
    }

    // Send Message skill dialog
    if (showSendSheet) {
        SendMessageDialog(
            onDismiss = { showSendSheet = false },
            onSend = { contact, app, message ->
                showSendSheet = false
                onSendDirectMessage(contact, app, message)
            },
            colors = colors,
        )
    }
}

@Composable
private fun HumanApprovalBar(
    request: com.sikoclaw.app.agent.ApprovalRequest,
    colors: SikoClawColors,
    onDecision: (Boolean) -> Unit,
) {
    Surface(
        color = colors.surface,
        border = BorderStroke(1.dp, Color(0xFFF59E0B)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Security, null, tint = Color(0xFFF59E0B))
                Spacer(Modifier.width(8.dp))
                Text("OctoBot needs your approval", color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            Text(request.action, color = colors.textSecondary, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onDecision(false) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("No") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onDecision(true) }, modifier = Modifier.heightIn(min = 48.dp), colors = ButtonDefaults.buttonColors(containerColor = colors.accent)) { Text("Yes") }
            }
        }
    }
}

@Composable
private fun MemoryCaptureApprovalBar(
    request: com.sikoclaw.app.agent.memory.MemoryCaptureRequest,
    colors: SikoClawColors,
    onDecision: (Boolean) -> Unit,
) {
    Surface(
        color = colors.surface,
        border = BorderStroke(1.dp, colors.accent.copy(alpha = .7f)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.BookmarkAdd, null, tint = colors.accent)
                Spacer(Modifier.width(8.dp))
                Text("Should OctoBot remember this?", color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            Text(request.fact, color = colors.textSecondary, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onDecision(false) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Not now") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onDecision(true) }, modifier = Modifier.heightIn(min = 48.dp), colors = ButtonDefaults.buttonColors(containerColor = colors.accent)) { Text("Remember") }
            }
        }
    }
}

// ======================== TOP BAR ========================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    modelStatus: String,
    sessionTokens: Int = 0,
    sessionCost: Double = 0.0,
    isLocalModel: Boolean = true,
    selectedTab: String,
    onTabChange: (String) -> Unit,
    onMenuClick: () -> Unit,
    onSettings: () -> Unit,
    onModels: () -> Unit,
    onBrowser: () -> Unit,
    onStartVoiceCall: () -> Unit,
    onModelSwitch: (modelId: String, displayName: String) -> Unit = { _, _ -> },
    colors: SikoClawColors,
) {
    // Token count color: grey â†’ blue â†’ amber â†’ red
    val tokenColor = when {
        sessionTokens < 5000 -> colors.textTertiary
        sessionTokens < 15000 -> Color(0xFF60A5FA) // blue
        sessionTokens < 25000 -> Color(0xFFFBBF24) // amber
        else -> Color(0xFFF87171) // soft red
    }

    Column {
        var showModelMenu by remember { mutableStateOf(false) }

        TopAppBar(
            title = {
                Text(
                    buildAnnotatedString {
                        append("Octo")
                        withStyle(SpanStyle(color = colors.accent)) { append("Bot") }
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = colors.textPrimary,
                )
            },
            navigationIcon = {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                }
            },
            actions = {
                // Local/Cloud toggle â€” two plain buttons, no container
                Surface(
                    onClick = onModels,
                    shape = RoundedCornerShape(10.dp),
                    color = colors.aiBubble,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.aiBubbleBorder),
                ) {
                    Text(
                        "Models",
                        fontSize = 12.sp,
                        color = colors.accent,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
                val context = LocalContext.current
                IconButton(onClick = onStartVoiceCall) {
                    Icon(Icons.Outlined.Call, contentDescription = "Start voice call")
                }
                var showMore by remember { mutableStateOf(false) }
                IconButton(onClick = { showMore = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                    DropdownMenuItem(text = { Text("Browser") }, leadingIcon = { Icon(Icons.Outlined.Public, null) }, onClick = { showMore = false; onBrowser() })
                    DropdownMenuItem(text = { Text("Linux Terminal") }, leadingIcon = { Icon(Icons.Outlined.Terminal, null) }, onClick = { showMore = false; context.startActivity(Intent(context, com.sikoclaw.app.ui.settings.LinuxSandboxActivity::class.java)) })
                    DropdownMenuItem(text = { Text("Settings") }, leadingIcon = { Icon(Icons.Default.Settings, null) }, onClick = { showMore = false; onSettings() })
                    DropdownMenuItem(text = { Text("Tools") }, leadingIcon = { Icon(Icons.Outlined.Build, null) }, onClick = { showMore = false; onSettings() })
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = colors.surface,
                titleContentColor = colors.textPrimary,
                navigationIconContentColor = colors.textPrimary,
                actionIconContentColor = colors.textSecondary,
            ),
        )

        // Model status + dropdown â€” filtered by selected tab
        Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                // The chevron is a quick selector: choosing an item activates it immediately.
                // The Models button above remains the full provider-management route.
                .clickable(onClick = { showModelMenu = true })
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = modelStatus,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.UnfoldMore,
                contentDescription = "Switch model",
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
            if (sessionTokens > 0 && !isLocalModel) {
                val formattedTokens = if (sessionTokens >= 1000) {
                    String.format("%.1fK", sessionTokens / 1000.0)
                } else {
                    "$sessionTokens"
                }
                val costText = if (sessionCost < 0.01) "< $0.01" else "$${String.format("%.2f", sessionCost)}"
                val tokenSuffix = if (!isLocalModel && sessionCost > 0) {
                    " Â· $formattedTokens tokens Â· $costText"
                } else {
                    " Â· $formattedTokens tokens"
                }
                Text(
                    text = tokenSuffix,
                    fontSize = 11.sp,
                    color = tokenColor,
                )
            }
        }
            // Model switcher dropdown â€” only show configured/downloaded models
            DropdownMenu(
                expanded = showModelMenu,
                onDismissRequest = { showModelMenu = false },
                modifier = Modifier.widthIn(min = 280.dp, max = 360.dp),
                containerColor = colors.surface,
            ) {
                val kvUtils = com.sikoclaw.app.utils.KVUtils
                val apiKey = kvUtils.getLlmApiKey()
                val baseUrl = kvUtils.getLlmBaseUrl()
                val currentModel = kvUtils.getLlmModelName()

                if (selectedTab == "cloud") {
                    // Cloud models: from configured provider
                    if (apiKey.isNotEmpty()) {
                        val activeProvider = com.sikoclaw.app.agent.CloudProvider.entries.find {
                            it.defaultBaseUrl == baseUrl
                        }
                        val modelsToShow = activeProvider?.models
                            ?: com.sikoclaw.app.agent.CloudProvider.OPENAI.models
                        modelsToShow.forEach { model ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            model.displayName,
                                            fontSize = 13.sp,
                                            fontWeight = if (model.id == currentModel && !isLocalModel) FontWeight.Bold else FontWeight.Normal,
                                        )
                                        if (model.id == currentModel && !isLocalModel) {
                                            Spacer(Modifier.width(6.dp))
                                            Text("âœ“", fontSize = 12.sp, color = colors.accent)
                                        }
                                    }
                                },
                                onClick = {
                                    showModelMenu = false
                                    onModelSwitch(model.id, model.displayName)
                                },
                            )
                        }
                    } else {
                        // No API key configured
                        DropdownMenuItem(
                            text = { Text("No API key configured", fontSize = 13.sp, color = colors.textTertiary) },
                            onClick = { showModelMenu = false; onSettings() },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Configure API key...", fontSize = 13.sp, color = colors.accent) },
                        onClick = { showModelMenu = false; onSettings() },
                    )
                } else {
                    // Local models: downloaded models
                    val localPath = kvUtils.getLocalModelPath()
                    if (localPath.isNotEmpty() && java.io.File(localPath).exists()) {
                        val localName = java.io.File(localPath).nameWithoutExtension
                            .replace("-", " ").replace("_", " ")
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("$localName (On-device)", fontSize = 13.sp,
                                        fontWeight = if (isLocalModel) FontWeight.Bold else FontWeight.Normal)
                                    if (isLocalModel) {
                                        Spacer(Modifier.width(6.dp))
                                        Text("âœ“", fontSize = 12.sp, color = colors.accent)
                                    }
                                }
                            },
                            onClick = {
                                showModelMenu = false
                                onModelSwitch("LOCAL", localName)
                            },
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("No local model downloaded", fontSize = 13.sp, color = colors.textTertiary) },
                            onClick = { showModelMenu = false; onSettings() },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Download models...", fontSize = 13.sp, color = colors.accent) },
                        onClick = { showModelMenu = false; onSettings() },
                    )
                }
            }
        }
        HorizontalDivider(color = colors.divider, thickness = 0.5.dp)
    }
}

// ======================== PERMISSION BANNER ========================

@Composable
private fun PermissionBanner(onClick: () -> Unit, colors: SikoClawColors) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.accent.copy(alpha = 0.12f),
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Shield, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Permissions needed. Tap to fix.",
                color = colors.accent,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
        }
    }
}

// ======================== MESSAGE LIST ========================

@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    colors: SikoClawColors,
    onBackgroundTap: () -> Unit = {},
    scrollToTimestamp: Long? = null,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(scrollToTimestamp, messages.size) {
        if (scrollToTimestamp != null) {
            val index = messages.indexOfFirst { it.timestamp == scrollToTimestamp }
            if (index >= 0) listState.animateScrollToItem(index)
        }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(messages.size - 1) }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(onBackgroundTap) {
                detectTapGestures(onTap = { onBackgroundTap() })
            },
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(messages.size) { index ->
            val message = messages[index]
            when (message.role) {
                ChatMessage.Role.USER -> UserBubble(message, colors)
                ChatMessage.Role.ASSISTANT -> AssistantBubble(message, colors)
                ChatMessage.Role.REASONING -> ReasoningBubble(message, colors)
                ChatMessage.Role.SYSTEM -> SystemMessage(message.content, colors)
                ChatMessage.Role.TOOL_GROUP -> ToolActivityGroup(message, colors)
            }
        }
    }
}

// ======================== BUBBLES ========================

@Composable
private fun UserBubble(message: ChatMessage, colors: SikoClawColors) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var showActions by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 64.dp, end = 14.dp, top = 3.dp, bottom = 3.dp),
    ) {
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            Surface(
                color = colors.userBubble,
                shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
                modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { showActions = true }),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    PluginBadges(message.pluginIds, colors, onUserBubble = true)
                    Text(message.content, color = colors.userText, fontSize = 15.sp, lineHeight = 21.sp)
                    if (message.attachments.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp)); AttachmentCards(message.attachments, colors, compact = true)
                    }
                }
            }
        }
        Text(
            text = formatBubbleTimestamp(message.timestamp),
            fontSize = 9.sp,
            color = colors.textTertiary,
            modifier = Modifier
                .align(Alignment.End)
                .padding(end = 6.dp, top = 1.dp, bottom = 2.dp),
        )
    }
    if (showActions) MessageActionsDialog(message.content, { showActions = false }, colors)
}

@Composable
private fun AssistantBubble(message: ChatMessage, colors: SikoClawColors) {
    val text = message.content
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var showActions by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 64.dp, top = 3.dp, bottom = 3.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Bottom,
        ) {
            // Avatar
            OctoBotAvatar(size = 32.dp)
            Spacer(Modifier.width(8.dp))

            // Bubble
            if (text == "...") {
                Surface(
                    color = colors.aiBubble,
                    shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.aiBubbleBorder),
                    modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { showActions = true }),
                ) {
                    TypingIndicator(
                        color = colors.textTertiary,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    )
                }
            } else {
                Surface(
                    color = colors.aiBubble,
                    shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.aiBubbleBorder),
                    modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { showActions = true }),
                ) {
                    Column {
                        if (text.isNotBlank()) AssistantContent(text, colors, message.isStreaming)
                        if (message.attachments.isNotEmpty()) {
                            AttachmentCards(
                                attachments = message.attachments,
                                colors = colors,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
        if (text != "...") {
            Row(Modifier.padding(start = 40.dp, top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(finalAssistantText(text)))
                        Toast.makeText(context, "Message copied", Toast.LENGTH_SHORT).show()
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.heightIn(min = 40.dp),
                ) {
                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Copy", fontSize = 11.sp)
                }
                TextButton(
                    onClick = { shareMessageText(context, finalAssistantText(text)) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.heightIn(min = 40.dp),
                ) {
                    Icon(Icons.Outlined.Share, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Share", fontSize = 11.sp)
                }
                TextButton(
                    onClick = { com.sikoclaw.app.voice.MessageSpeech.speak(context, finalAssistantText(text)) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.heightIn(min = 40.dp),
                ) {
                    Icon(Icons.Outlined.VolumeUp, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Listen", fontSize = 11.sp)
                }
            }
            val footer = listOfNotNull(
                message.modelName?.takeIf { it.isNotBlank() },
                if (message.isStreaming) "typingâ€¦" else formatBubbleTimestamp(message.timestamp)
            ).joinToString(" Â· ")
            Text(
                text = footer,
                fontSize = 9.sp,
                color = colors.textTertiary,
                modifier = Modifier.padding(start = 40.dp, top = 1.dp, bottom = 2.dp),
            )
        }
    }
    if (showActions) MessageActionsDialog(finalAssistantText(text), { showActions = false }, colors)
}

@Composable
private fun ReasoningBubble(message: ChatMessage, colors: SikoClawColors) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var showActions by remember { mutableStateOf(false) }
    Surface(
        color = colors.surface,
        border = BorderStroke(1.dp, colors.inputBorder),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.padding(start = 54.dp, end = 64.dp, top = 4.dp, bottom = 4.dp)
            .combinedClickable(onClick = {}, onLongClick = { showActions = true }),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Psychology, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Thoughts", color = colors.textSecondary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                if (message.isStreaming) { Spacer(Modifier.width(6.dp)); TypingIndicator(colors.textTertiary) }
            }
            Spacer(Modifier.height(6.dp))
            MarkdownMessage(message.content, colors)
        }
    }
    if (showActions) MessageActionsDialog(message.content, { showActions = false }, colors)
}

@Composable
private fun MessageActionsDialog(text: String, dismiss: () -> Unit, colors: SikoClawColors) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var selecting by remember { mutableStateOf(false) }
    if (selecting) {
        AlertDialog(
            onDismissRequest = { selecting = false },
            title = { Text("Select text") },
            text = { androidx.compose.foundation.text.selection.SelectionContainer { Text(text, color = colors.textPrimary) } },
            confirmButton = { TextButton(onClick = { selecting = false; dismiss() }) { Text("Done") } },
            containerColor = colors.surface,
        )
        return
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Message actions") },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text("Copy text") },
                    leadingContent = { Icon(Icons.Outlined.ContentCopy, null) },
                    modifier = Modifier.clickable {
                        clipboard.setText(AnnotatedString(text))
                        Toast.makeText(context, "Message copied", Toast.LENGTH_SHORT).show()
                        dismiss()
                    },
                )
                ListItem(
                    headlineContent = { Text("Share") },
                    leadingContent = { Icon(Icons.Outlined.Share, null) },
                    modifier = Modifier.clickable { shareMessageText(context, text); dismiss() },
                )
                ListItem(
                    headlineContent = { Text("Select text") },
                    leadingContent = { Icon(Icons.Outlined.TextFields, null) },
                    modifier = Modifier.clickable { selecting = true },
                )
            }
        },
        confirmButton = { TextButton(dismiss) { Text("Close") } },
        containerColor = colors.surface,
    )
}

@Composable
private fun PluginBadges(ids: List<String>, colors: SikoClawColors, onUserBubble: Boolean = false) {
    if (ids.isEmpty()) return
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ids.forEach { id ->
            val plugin = PluginCatalog.all().firstOrNull { it.id == id }
            Surface(color = (if (onUserBubble) Color.White else colors.accent).copy(alpha = 0.18f), shape = RoundedCornerShape(50)) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(pluginIcon(id), null, tint = if (onUserBubble) Color.White else colors.accent, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp)); Text(plugin?.name ?: id, color = if (onUserBubble) Color.White else colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

private fun pluginIcon(id: String) = when (id) {
    "browser" -> Icons.Outlined.Public
    "phone_control" -> Icons.Outlined.TouchApp
    "web_search" -> Icons.Outlined.Search
    "image_generation" -> Icons.Outlined.Image
    "upload", "upload_files" -> Icons.Outlined.AttachFile
    "terminal", "linux_sandbox" -> Icons.Outlined.Terminal
    "mcp" -> Icons.Outlined.AccountTree
    "documents", "document_studio", "docx" -> Icons.Outlined.Description
    "pdf" -> Icons.Outlined.PictureAsPdf
    "spreadsheet", "xlsx" -> Icons.Outlined.TableChart
    "presentation", "pptx" -> Icons.Outlined.Slideshow
    "audio", "tts" -> Icons.Outlined.GraphicEq
    "video" -> Icons.Outlined.Movie
    else -> Icons.Outlined.Extension
}

private val sikoImageMarker = Regex("\\[\\[SIKO_IMAGE:(content://[^]]+)]]")

@Composable
private fun AssistantContent(text: String, colors: SikoClawColors, streaming: Boolean = false) {
    val match = sikoImageMarker.find(text)
    val uri = match?.groupValues?.getOrNull(1)?.let(Uri::parse)
    val attachmentPayload = ChatAttachmentManager.decode(sikoImageMarker.replace(text, ""))
    val caption = finalAssistantText(attachmentPayload.visibleText).trim()
    val context = LocalContext.current
    Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
        if (caption.isNotBlank()) {
            MarkdownMessage(caption, colors)
        }
        if (streaming) StreamingCursor(colors.accent)
        if (attachmentPayload.attachments.isNotEmpty()) {
            Spacer(Modifier.height(8.dp)); AttachmentCards(attachmentPayload.attachments, colors)
        }
        if (uri != null) {
            Spacer(Modifier.height(if (caption.isBlank()) 0.dp else 8.dp))
            AndroidView(
                factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP; adjustViewBounds = true } },
                update = { Glide.with(it).load(uri).into(it) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 360.dp).clip(RoundedCornerShape(14.dp)),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    runCatching {
                        val name = "siko_image_${System.currentTimeMillis()}.png"
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, name)
                            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OctoBot")
                        }
                        val target = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Cannot create image")
                        context.contentResolver.openInputStream(uri)!!.use { input -> context.contentResolver.openOutputStream(target)!!.use { output -> input.copyTo(output) } }
                    }.onSuccess { Toast.makeText(context, "Saved to Pictures/OctoBot", Toast.LENGTH_SHORT).show() }
                        .onFailure { Toast.makeText(context, "Could not save image: ${it.message}", Toast.LENGTH_LONG).show() }
                },
                modifier = Modifier.align(Alignment.End),
            ) { Icon(Icons.Outlined.Download, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Save") }
        }
    }
}

@Composable
private fun StreamingCursor(color: Color) {
    val transition = rememberInfiniteTransition(label = "stream-cursor")
    val alpha by transition.animateFloat(0.18f, 1f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "cursor-alpha")
    Text("â–", color = color.copy(alpha = alpha), fontFamily = FontFamily.Monospace, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun AttachmentCards(
    attachments: List<ChatAttachment>,
    colors: SikoClawColors,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        attachments.forEach { attachment ->
            Surface(
                color = colors.surface,
                border = BorderStroke(1.dp, colors.divider),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    MediaAttachmentPreview(
                        attachment = attachment,
                        colors = colors,
                        onOpen = { openAttachment(context, attachment) },
                    )
                    Row(
                        modifier = Modifier.padding(if (compact) 10.dp else 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                    Icon(
                        imageVector = attachmentIcon(attachment.mimeType),
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(attachment.name, color = colors.textPrimary, fontSize = 13.sp,
                            fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${attachment.mimeType.ifBlank { "File" }} Â· ${formatFileSize(attachment.sizeBytes)}",
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (attachment.state == AttachmentState.FAILED) {
                            Text(attachment.error ?: "Processing failed", color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    IconButton(
                        onClick = { openAttachment(context, attachment) },
                        enabled = attachment.state == AttachmentState.READY,
                        modifier = Modifier.size(48.dp),
                    ) { Icon(Icons.Outlined.OpenInNew, "Open file", tint = colors.textSecondary) }
                    IconButton(
                        onClick = { shareAttachment(context, attachment) },
                        enabled = attachment.state == AttachmentState.READY,
                        modifier = Modifier.size(48.dp),
                    ) { Icon(Icons.Outlined.Share, "Share file", tint = colors.textSecondary) }
                    IconButton(
                        onClick = { saveAttachment(context, attachment) },
                        enabled = attachment.state == AttachmentState.READY,
                        modifier = Modifier.size(48.dp),
                    ) { Icon(Icons.Outlined.Download, "Save to Downloads", tint = colors.textSecondary) }
                    }
                    if (attachment.mimeType.startsWith("audio/") && attachment.state == AttachmentState.READY) {
                        AudioAttachmentPlayer(attachment, colors)
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioAttachmentPlayer(attachment: ChatAttachment, colors: SikoClawColors) {
    val context = LocalContext.current
    val player = remember(attachment.uri) { runCatching { android.media.MediaPlayer.create(context, Uri.parse(attachment.uri)) }.getOrNull() }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableIntStateOf(0) }
    val duration = player?.duration?.coerceAtLeast(1) ?: 1
    DisposableEffect(player) { onDispose { runCatching { player?.release() } } }
    LaunchedEffect(playing, player) {
        while (playing && player != null) {
            position = runCatching { player.currentPosition }.getOrDefault(position)
            if (!player.isPlaying) playing = false
            kotlinx.coroutines.delay(250)
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
            player ?: return@IconButton
            if (player.isPlaying) { player.pause(); playing = false } else { player.start(); playing = true }
        }, enabled = player != null, modifier = Modifier.size(48.dp)) {
            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause audio" else "Play audio", tint = colors.accent)
        }
        Slider(
            value = position.toFloat().coerceIn(0f, duration.toFloat()),
            onValueChange = { position = it.toInt(); runCatching { player?.seekTo(position) } },
            valueRange = 0f..duration.toFloat(),
            modifier = Modifier.weight(1f),
        )
        Text("${formatAudioTime(position)} / ${formatAudioTime(duration)}", color = colors.textSecondary, fontSize = 10.sp)
    }
}

private fun formatAudioTime(milliseconds: Int): String {
    val seconds = (milliseconds / 1000).coerceAtLeast(0)
    return "%d:%02d".format(Locale.US, seconds / 60, seconds % 60)
}

private fun attachmentIcon(mime: String) = when {
    mime.startsWith("image/") -> Icons.Outlined.Image
    mime.startsWith("video/") -> Icons.Outlined.Movie
    mime.startsWith("audio/") -> Icons.Outlined.GraphicEq
    mime == "application/pdf" -> Icons.Outlined.PictureAsPdf
    mime.contains("spreadsheet") -> Icons.Outlined.TableChart
    mime.contains("presentation") -> Icons.Outlined.Slideshow
    mime.contains("wordprocessing") -> Icons.Outlined.Description
    mime == "text/html" -> Icons.Outlined.Language
    mime.contains("zip") -> Icons.Outlined.FolderZip
    else -> Icons.Outlined.InsertDriveFile
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 0 -> "Unknown size"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(Locale.US, bytes / 1024.0)
    else -> "%.1f MB".format(Locale.US, bytes / (1024.0 * 1024.0))
}

private fun openAttachment(context: android.content.Context, attachment: ChatAttachment) {
    runCatching {
        if (attachment.mimeType == "text/html") {
            context.startActivity(Intent(context, com.sikoclaw.app.ui.artifact.ArtifactActivity::class.java).apply {
                data = Uri.parse(attachment.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            return@runCatching
        }
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(attachment.uri), attachment.mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }.onFailure { Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show() }
}

private fun shareAttachment(context: android.content.Context, attachment: ChatAttachment) {
    runCatching {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = attachment.mimeType.ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, Uri.parse(attachment.uri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share file").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Toast.makeText(context, "Could not share this file", Toast.LENGTH_SHORT).show() }
}

private fun saveAttachment(context: android.content.Context, attachment: ChatAttachment) {
    runCatching {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: error("Downloads directory is unavailable")
            val target = java.io.File(dir, attachment.name)
            context.contentResolver.openInputStream(Uri.parse(attachment.uri))!!.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
            return@runCatching
        }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, attachment.name)
            put(MediaStore.Downloads.MIME_TYPE, attachment.mimeType.ifBlank { "application/octet-stream" })
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/OctoBot")
        }
        val target = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Could not create download")
        context.contentResolver.openInputStream(Uri.parse(attachment.uri))!!.use { input ->
            context.contentResolver.openOutputStream(target)!!.use { output -> input.copyTo(output) }
        }
    }.onSuccess { Toast.makeText(context, "Saved to Downloads/OctoBot", Toast.LENGTH_SHORT).show() }
        .onFailure { Toast.makeText(context, "Could not save file: ${it.message}", Toast.LENGTH_LONG).show() }
}

private fun shareMessageText(context: android.content.Context, text: String) {
    runCatching {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }, "Share message").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Toast.makeText(context, "Could not share message", Toast.LENGTH_SHORT).show() }
}

private fun formatBubbleTimestamp(timestamp: Long): String {
    val pattern = if (DateUtils.isToday(timestamp)) "h:mm a" else "MMM d, h:mm a"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
}

@Composable
private fun TypingIndicator(color: Color, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val dots = listOf(0, 1, 2)

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        dots.forEach { index ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 200),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alpha)),
            )
        }
    }
}

@Composable
private fun SystemMessage(text: String, colors: SikoClawColors) {
    Text(
        text = text,
        color = colors.textTertiary,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 6.dp),
    )
}

@Composable
private fun ToolGroup(message: ChatMessage, colors: SikoClawColors) {
    Column(
        modifier = Modifier.padding(start = 54.dp, end = 64.dp, top = 2.dp, bottom = 2.dp),
    ) {
        message.toolSteps?.forEach { step ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 1.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (step.success) colors.accent else colors.textTertiary),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "${step.toolName} â†’ ${step.summary}",
                    fontSize = 12.sp,
                    color = colors.textTertiary,
                )
            }
        }
    }
}

@Composable
private fun ToolActivityGroup(message: ChatMessage, colors: SikoClawColors) {
    Column(
        modifier = Modifier.padding(start = 54.dp, end = 28.dp, top = 4.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val restoredSteps = message.toolSteps.orEmpty().ifEmpty {
            message.content.lineSequence().filter { it.startsWith("-") }.map { line ->
                ToolStep("Tool activity", line.removePrefix("-").trim(), success = line.contains("âœ“"), status = if (line.contains("âœ“")) ToolActivityStatus.COMPLETED else ToolActivityStatus.FAILED)
            }.toList()
        }
        restoredSteps.forEach { step ->
            var expanded by rememberSaveable(step.callId) { mutableStateOf(false) }
            val statusColor = when (step.status) {
                ToolActivityStatus.RUNNING -> colors.accent
                ToolActivityStatus.COMPLETED -> Color(0xFF22C55E)
                ToolActivityStatus.FAILED -> MaterialTheme.colorScheme.error
            }
            Card(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = statusColor.copy(alpha = 0.14f), shape = CircleShape) {
                            Icon(toolActivityIcon(step.rawToolName), null, tint = statusColor, modifier = Modifier.padding(8.dp).size(20.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(step.toolName, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(step.summary, color = colors.textSecondary, fontSize = 12.sp, maxLines = if (expanded) 5 else 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (step.status == ToolActivityStatus.RUNNING) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = statusColor)
                        else Icon(if (step.status == ToolActivityStatus.COMPLETED) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, step.status.name, tint = statusColor, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(4.dp))
                        Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (expanded) "Hide details" else "Show details", tint = colors.textSecondary)
                    }
                    Text(step.status.name.lowercase().replaceFirstChar { it.uppercase() }, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 46.dp, top = 4.dp))
                    if (expanded && step.details.isNotBlank()) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = colors.divider)
                        Text(step.details, color = colors.textSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}

private fun toolActivityIcon(name: String) = when {
    name.contains("terminal", true) || name.contains("shell", true) || name.contains("code", true) -> Icons.Outlined.Terminal
    name.contains("file", true) || name.contains("document", true) -> Icons.Outlined.FolderOpen
    name.contains("search", true) -> Icons.Outlined.Search
    name.contains("browser", true) || name.contains("web", true) -> Icons.Outlined.Public
    name.contains("mcp", true) -> Icons.Outlined.AccountTree
    name.contains("memory", true) -> Icons.Outlined.Psychology
    name.contains("cron", true) || name.contains("schedule", true) || name.contains("timer", true) -> Icons.Outlined.Schedule
    else -> Icons.Outlined.Build
}

// ======================== INPUT BAR ========================

@Composable
private fun ChatInputBar(
    isAwaitingReply: Boolean,
    isTaskRunning: Boolean,
    inputEnabled: Boolean = true,
    isTaskMode: Boolean,
    isLocalModel: Boolean,
    onTaskModeChange: (Boolean) -> Unit,
    onSendChat: (String) -> Unit,
    onSendTask: (String) -> Unit,
    onSteerTask: (String) -> Unit,
    onQueueTask: (String) -> Unit,
    onStopAll: () -> Unit = {},
    onAttach: () -> Unit,
    onOpenBrowser: () -> Unit,
    colors: SikoClawColors,
    prefillText: String = "",
    prefillIsTask: Boolean = false,
    onPrefillConsumed: () -> Unit = {},
) {
    var text by remember { mutableStateOf("") }
    var showPluginMenu by remember { mutableStateOf(false) }
    var sendDuringTaskAsSteering by rememberSaveable { mutableStateOf(true) }
    val selectedPlugins = remember { mutableStateListOf<String>() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    // Voice input â€” Android system RecognizerIntent, no RECORD_AUDIO needed (system dialog
    // handles its own permission). Appends transcript to current text instead of replacing,
    // so users can prefix with typed context.
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        XLog.d("VoiceInput", "voiceLauncher result: resultCode=${result.resultCode}")
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
            if (spokenText != null) {
                XLog.i("VoiceInput", "transcript received: ${spokenText.length} chars, currentText.len=${text.length}")
                val prefix = when {
                    text.isBlank() -> ""
                    text.endsWith(" ") -> text
                    else -> "$text "
                }
                text = prefix + spokenText
            } else {
                XLog.w("VoiceInput", "transcript empty or missing from result data")
                Toast.makeText(context, R.string.voice_input_error, Toast.LENGTH_SHORT).show()
            }
        } else {
            XLog.d("VoiceInput", "voice input cancelled by user (resultCode != OK)")
        }
    }

    // Consume prefill from prompt chips
    LaunchedEffect(prefillText) {
        if (prefillText.isNotEmpty()) {
            text = prefillText
            if (isLocalModel) onTaskModeChange(prefillIsTask)
            onPrefillConsumed()
        }
    }

    val taskBg = Color(0xFF1A1410)
    val taskBorder = colors.accent.copy(alpha = 0.25f)

    Column(
        modifier = Modifier
            .background(if (isTaskMode && isLocalModel) taskBg else colors.surface)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(
            color = if (isTaskMode && isLocalModel) taskBorder else colors.divider,
            thickness = 1.dp,
        )

        // Segmented Chat/Task toggle â€” Local LLM only
        if (isLocalModel) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Chat button
                Surface(
                    onClick = { onTaskModeChange(false) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (!isTaskMode) colors.aiBubble else Color.Transparent,
                    border = if (!isTaskMode) androidx.compose.foundation.BorderStroke(1.dp, colors.aiBubbleBorder) else null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        "ðŸ’¬ Chat",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (!isTaskMode) colors.textPrimary else colors.textTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 9.dp),
                    )
                }
                // Task button
                Surface(
                    onClick = { onTaskModeChange(true) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isTaskMode) colors.accent else Color.Transparent,
                    border = if (isTaskMode) androidx.compose.foundation.BorderStroke(1.dp, colors.accent) else null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        "ðŸ¤– Task",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isTaskMode) Color.White else colors.textTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 9.dp),
                    )
                }
            }
        }

        // Input bar â€” always visible, style changes in Task mode
        if (selectedPlugins.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                selectedPlugins.forEach { id ->
                    val plugin = PluginCatalog.all().firstOrNull { it.id == id }
                    InputChip(
                        selected = true,
                        onClick = {},
                        label = { Text(plugin?.name ?: id) },
                        leadingIcon = { Icon(pluginIcon(id), null, Modifier.size(17.dp)) },
                        trailingIcon = {
                            IconButton({ selectedPlugins.remove(id) }, Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, "Remove ${plugin?.name ?: id}", Modifier.size(15.dp))
                            }
                        },
                    )
                }
            }
        }

        if (isTaskRunning) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = sendDuringTaskAsSteering,
                    onClick = { sendDuringTaskAsSteering = true },
                    label = { Text("Steer current task", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Outlined.CallSplit, null, Modifier.size(15.dp)) },
                )
                FilterChip(
                    selected = !sendDuringTaskAsSteering,
                    onClick = { sendDuringTaskAsSteering = false },
                    label = { Text("Add to queue", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Outlined.Queue, null, Modifier.size(15.dp)) },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                FloatingActionButton(
                    onClick = { showPluginMenu = true },
                    modifier = Modifier.size(34.dp),
                    containerColor = colors.background,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Files and plugins", tint = colors.textSecondary, modifier = Modifier.size(19.dp))
                }
                DropdownMenu(
                    expanded = showPluginMenu,
                    onDismissRequest = { showPluginMenu = false },
                    containerColor = colors.surface,
                ) {
                    DropdownMenuItem(
                        text = { Text("Upload files", color = colors.textPrimary) },
                        leadingIcon = { Icon(Icons.Default.AttachFile, null, tint = colors.accent) },
                        onClick = { showPluginMenu = false; onAttach() },
                    )
                    HorizontalDivider(color = colors.divider)
                    PluginCatalog.all().filter { PluginCatalog.isEnabled(it.id) }.forEach { plugin ->
                        DropdownMenuItem(
                            text = { Column { Text(plugin.name, color = colors.textPrimary); Text(plugin.description, color = colors.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                            leadingIcon = { Icon(pluginIcon(plugin.id), null, tint = colors.accent) },
                            onClick = {
                                showPluginMenu = false
                                if (plugin.id !in selectedPlugins) selectedPlugins.add(plugin.id)
                            },
                        )
                    }
                    if (isTaskRunning) {
                        Row(
                            Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { com.sikoclaw.app.agent.StuckControl.continueAnyway() }) {
                                Icon(Icons.Outlined.PlayArrow, null, Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp)); Text("Continue anyway")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.width(6.dp))

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = {
                    Text(
                        when {
                            isLocalModel && isTaskMode -> "Describe a phone task..."
                            !isLocalModel -> "Chat or give a task..."
                            else -> "Chat with local AI..."
                        },
                        color = if (isTaskMode && isLocalModel) colors.accent.copy(alpha = 0.5f) else colors.textTertiary,
                        fontSize = 14.sp,
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp, max = 100.dp),
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (isTaskMode && isLocalModel) colors.accent else colors.accent.copy(alpha = 0.4f),
                    unfocusedBorderColor = if (isTaskMode && isLocalModel) colors.accent.copy(alpha = 0.6f) else colors.inputBorder,
                    cursorColor = if (isTaskMode && isLocalModel) colors.accent else colors.accent,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    focusedContainerColor = if (isTaskMode && isLocalModel) taskBg else Color.Transparent,
                    unfocusedContainerColor = if (isTaskMode && isLocalModel) taskBg else Color.Transparent,
                ),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                maxLines = 4,
            )

            Spacer(Modifier.width(6.dp))

            // Voice input mic button (Issue #44) â€” launches Android system speech dialog.
            // Available whenever input is enabled (including while a task runs, so user
            // can queue next prompt with voice without waiting).
            val micEnabled = inputEnabled
            FloatingActionButton(
                onClick = {
                    XLog.i("VoiceInput", "mic tapped: text.len=${text.length}, isTaskMode=$isTaskMode, isLocalModel=$isLocalModel")
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                        )
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                        putExtra(
                            RecognizerIntent.EXTRA_PROMPT,
                            context.getString(R.string.voice_input_prompt)
                        )
                    }
                    try {
                        voiceLauncher.launch(intent)
                    } catch (e: ActivityNotFoundException) {
                        XLog.e("VoiceInput", "no speech recognition service installed", e)
                        Toast.makeText(
                            context,
                            R.string.voice_input_unavailable,
                            Toast.LENGTH_SHORT
                        ).show()
                    } catch (e: Exception) {
                        XLog.e("VoiceInput", "voice launch failed unexpectedly", e)
                        Toast.makeText(
                            context,
                            R.string.voice_input_error,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier
                    .size(34.dp)
                    .alpha(if (micEnabled) 1f else 0.35f),
                containerColor = colors.background,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp),
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = stringResource(R.string.voice_input_button_cd),
                    tint = colors.textTertiary,
                    modifier = Modifier.size(16.dp),
                )
            }

            Spacer(Modifier.width(6.dp))

            FloatingActionButton(
                onClick = {
                    if (isTaskRunning && text.isNotBlank()) {
                        val value = PluginMessageCodec.encode(text.trim(), selectedPlugins)
                        if (sendDuringTaskAsSteering) onSteerTask(value) else onQueueTask(value)
                        selectedPlugins.clear()
                        text = ""
                    } else if (isTaskRunning) {
                        onStopAll()
                    } else if (!isAwaitingReply && inputEnabled && text.isNotBlank()) {
                        if (!isLocalModel || isTaskMode) {
                            onSendTask(PluginMessageCodec.encode(text.trim(), selectedPlugins))
                            selectedPlugins.clear()
                            text = ""
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        } else {
                            onSendChat(PluginMessageCodec.encode(text.trim(), selectedPlugins))
                            selectedPlugins.clear()
                            text = ""
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
                    }
                },
                modifier = Modifier
                    .size(34.dp)
                    .alpha(if ((text.isBlank() || !inputEnabled || isAwaitingReply) && !isTaskRunning) 0.35f else 1f),
                containerColor = when {
                    isTaskRunning && text.isBlank() -> Color(0xFFF44336)
                    isTaskRunning -> colors.accent
                    isAwaitingReply -> colors.background
                    text.isBlank() -> colors.background
                    isTaskMode && isLocalModel -> colors.accent
                    else -> colors.userBubble
                },
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp),
            ) {
                Icon(
                    when {
                        isTaskRunning && text.isBlank() -> Icons.Default.Close
                        isTaskRunning -> Icons.Default.ArrowUpward
                        isAwaitingReply -> Icons.Default.MoreHoriz
                        else -> Icons.Default.ArrowUpward
                    },
                    contentDescription = when {
                        isTaskRunning && text.isBlank() -> "Stop"
                        isTaskRunning && sendDuringTaskAsSteering -> "Steer current task"
                        isTaskRunning -> "Add to queue"
                        isAwaitingReply -> "Waiting for reply"
                        else -> "Send"
                    },
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

// ======================== SKILL SHORTCUT BAR ========================

@Composable
private fun SkillShortcutBar(
    skills: List<Skill>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSkillTap: (Skill) -> Unit,
    colors: SikoClawColors,
) {
    val categoryIcons = mapOf(
        SkillCategory.INPUT to Icons.Outlined.Keyboard,
        SkillCategory.DISMISS to Icons.Outlined.Close,
        SkillCategory.NAVIGATION to Icons.Outlined.Navigation,
        SkillCategory.MESSAGING to Icons.Outlined.Chat,
        SkillCategory.MEDIA to Icons.Outlined.CameraAlt,
        SkillCategory.GENERAL to Icons.Outlined.AutoAwesome,
    )

    Column {
        // Toggle row
        Surface(
            onClick = onToggle,
            color = Color.Transparent,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Skills",
                    fontSize = 12.sp,
                    color = colors.textTertiary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = colors.textTertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        // Expanded skill chips
        if (expanded) {
            // Two rows of chips using FlowRow-style layout
            val rows = skills.chunked((skills.size + 1) / 2)
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (row in rows) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for (skill in row) {
                            val icon = categoryIcons[skill.category] ?: Icons.Outlined.AutoAwesome
                            Surface(
                                onClick = { onSkillTap(skill) },
                                shape = RoundedCornerShape(20.dp),
                                color = colors.accent.copy(alpha = 0.1f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        icon,
                                        contentDescription = null,
                                        tint = colors.accent,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        skill.name,
                                        fontSize = 11.sp,
                                        color = colors.accent,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

// ======================== DOWNLOAD OVERLAY ========================

@Composable
private fun DownloadOverlay(progress: Int, colors: SikoClawColors) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.octobot_avatar),
                    contentDescription = "OctoBot",
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Downloading your AI brain",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "This only happens once",
                    fontSize = 13.sp,
                    color = colors.textTertiary,
                )
                Spacer(Modifier.height(24.dp))
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = colors.accent,
                    trackColor = colors.inputBorder,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "$progress%",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent,
                )
            }
        }
    }
}

// ======================== EMPTY STATE ========================

@Composable
private fun EmptyStateWithPrompts(
    isLocalModel: Boolean,
    onSelectPrompt: (String, Boolean) -> Unit,
    colors: SikoClawColors,
    modifier: Modifier = Modifier,
) {
    data class Prompt(val text: String, val isTask: Boolean)

    // Cloud: show task examples (user can give tasks from chat)
    // Local: show chat examples (chat only, tasks go to Workflows tab)
    val prompts = if (!isLocalModel) {
        listOf(
            Prompt("What time is it in Tokyo?", false),
            Prompt("Help me write a birthday message", false),
            Prompt("ðŸ’¬ Send hi to Mom on WhatsApp", true),
        )
    } else {
        listOf(
            Prompt("Tell me a joke", false),
            Prompt("What can you do?", false),
            Prompt("Help me draft an email", false),
        )
    }

    val headerText = if (!isLocalModel) "Cloud AI" else "Local AI"

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.octobot_avatar),
            contentDescription = "OctoBot",
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "OctoBot",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            headerText,
            fontSize = 12.sp,
            color = colors.accent,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        // Hint text â€” Local has styled bold parts, Cloud is plain
        if (isLocalModel) {
            Text(
                buildAnnotatedString {
                    append("Chat in ")
                    withStyle(SpanStyle(color = colors.accent, fontWeight = FontWeight.Bold)) {
                        append("ðŸ’¬ Chat")
                    }
                    append(" mode, or switch to ")
                    withStyle(SpanStyle(color = colors.accent, fontWeight = FontWeight.Bold)) {
                        append("ðŸ¤– Task")
                    }
                    append(" to control your phone")
                },
                fontSize = 11.sp,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                modifier = Modifier.widthIn(max = 260.dp),
            )
        } else {
            Text(
                "Chat and tasks work together \u2014 just type anything",
                fontSize = 11.sp,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                modifier = Modifier.widthIn(max = 260.dp),
            )
        }
        Spacer(Modifier.height(12.dp))

        // Suggested prompt chips â€” same style as Quick Tasks items
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            prompts.forEach { prompt ->
                val barAlpha = if (prompt.isTask) 1f else 0.5f
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = colors.background,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.inputBorder),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectPrompt(prompt.text, prompt.isTask) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(38.dp)
                                .background(
                                    colors.accent.copy(alpha = barAlpha),
                                    RoundedCornerShape(topStart = 9.dp, bottomStart = 9.dp),
                                ),
                        )
                        Text(
                            prompt.text,
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        }
    }
}

// ======================== QUICK TASKS PANEL (v9) ========================

@Composable
private fun QuickTasksPanel(
    isLocalModel: Boolean,
    onFillTask: (String) -> Unit,
    onMonitorClick: () -> Unit,
    monitorActive: Boolean,
    colors: SikoClawColors,
) {
    var expanded by remember { mutableStateOf(true) }

    // Cloud-only tasks at the top (multi-step, Siri/GA can't do these)
    // Cloud-only tasks (multi-step, Siri can't do)
    val cloudOnlyTasks = listOf(
        "ðŸ¦ž Open Reddit and search for sikoclaw",
        "ðŸŽ¬ Search YouTube for funny cat fails",
        "ðŸ“¦ Install Telegram from Play Store",
        "ðŸ¦ Check what's trending on Twitter and tell me",
        "ðŸ’¬ Check my latest WhatsApp chat and summarize it",
        "ðŸ“‹ Copy the latest email subject and Google it",
        "ðŸ“§ Write an email saying I'll be late today",
    )
    // Reasoning tasks (1-2 tool calls + LLM analysis) â€” impressive, work on both
    val reasoningTasks = listOf(
        "ðŸ“µ Check my notifications â€” anything important?",
        "ðŸ“‹ Read my clipboard and explain what it says",
        "ðŸ§¹ Check my storage and apps â€” what can I delete?",
        "ðŸ”” Read my notifications and summarize",
        "ðŸ”‹ Check my battery and tell me if I need to charge",
    )
    // Simple deterministic tasks (1 tool, no reasoning)
    val deterministicTasks = listOf(
        "ðŸ’¬ Send hi to Mom on WhatsApp",
        "ðŸ“± What apps do I have?",
        "ðŸŒ¡ï¸ How hot is my phone?",
        "ðŸ”µ Is bluetooth on?",
        "ðŸ”‹ How much battery left?",
        "ðŸ“ž Call Mom",
        "ðŸ’¾ How much storage do I have?",
        "ðŸ“² What Android version am I running?",
    )
    // Cloud: cloud-only â†’ reasoning â†’ deterministic
    // Local: reasoning first (impressive) â†’ deterministic
    val quickTasks = if (isLocalModel) {
        reasoningTasks + deterministicTasks
    } else {
        cloudOnlyTasks + reasoningTasks + deterministicTasks
    }

    Column(
        modifier = Modifier.background(colors.surface),
    ) {
        HorizontalDivider(color = colors.divider, thickness = 1.dp)

        // Handle bar â€” â–² Quick Tasks â–²
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = "Toggle",
                tint = colors.accent,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Quick Task Templates",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = colors.accent,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = "Toggle",
                tint = colors.accent,
                modifier = Modifier.size(12.dp),
            )
        }

        // Collapsible content
        if (expanded) {
            // Quick task items â€” scrollable
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                quickTasks.forEach { task ->
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = colors.background,
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.inputBorder),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onFillTask(task.substringAfter(" ")) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(38.dp)
                                    .background(colors.accent, RoundedCornerShape(topStart = 9.dp, bottomStart = 9.dp)),
                            )
                            Text(
                                task,
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            )
                        }
                    }
                }
            }

            // Background section â€” always visible, NOT inside scroll
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(
                    "BACKGROUND",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textTertiary,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )

                // Monitor card
                val monitorBorderColor = if (monitorActive) colors.accent else colors.inputBorder
                Surface(
                    onClick = {
                        if (!monitorActive) onMonitorClick()
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = colors.background,
                    border = androidx.compose.foundation.BorderStroke(
                        if (monitorActive) 1.dp else 0.5.dp,
                        monitorBorderColor,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    colors.accent.copy(alpha = 0.12f),
                                    RoundedCornerShape(9.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("ðŸ‘ï¸", fontSize = 15.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (monitorActive) "Active" else "Monitor & Auto-Reply",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary,
                            )
                            Text(
                                if (monitorActive) "Monitoring active â€” use the top bar to stop" else "Watch messages and reply automatically",
                                fontSize = 9.sp,
                                color = colors.textTertiary,
                            )
                        }
                        if (!monitorActive) {
                            Text("â€º", color = colors.textTertiary, fontSize = 14.sp)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            } // end Background Column
        }
    }
}

// ======================== SIDEBAR ========================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SidebarContent(
    conversations: List<ChatHistoryManager.ConversationSummary>,
    onNewChat: () -> Unit,
    onSelectConversation: (ChatHistoryManager.ConversationSummary) -> Unit,
    onDeleteConversation: (ChatHistoryManager.ConversationSummary) -> Unit,
    onRenameConversation: (ChatHistoryManager.ConversationSummary, String) -> Unit,
    onSearchResult: (ChatDatabase.SearchResult) -> Unit,
    onSettings: () -> Unit,
    onModels: () -> Unit,
    colors: SikoClawColors,
) {
    var actionTarget by remember { mutableStateOf<ChatHistoryManager.ConversationSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<ChatHistoryManager.ConversationSummary?>(null) }
    var renameTarget by remember { mutableStateOf<ChatHistoryManager.ConversationSummary?>(null) }
var renameText by remember { mutableStateOf("") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ChatDatabase.SearchResult>>(emptyList()) }
    val context = LocalContext.current
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) searchResults = emptyList()
        else {
            kotlinx.coroutines.delay(180)
            searchResults = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ChatDatabase(context).search(searchQuery) }
        }
    }

    // Long-press action menu: Rename / Delete
    if (actionTarget != null) {
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            title = { Text(actionTarget!!.title, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            renameTarget = actionTarget
                            renameText = actionTarget!!.title
                            actionTarget = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Rename", color = colors.textPrimary)
                        }
                    }
                    TextButton(
                        onClick = {
                            deleteTarget = actionTarget
                            actionTarget = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Delete", color = Color(0xFFF87171))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { actionTarget = null }) { Text("Cancel", color = colors.textSecondary) }
            },
            containerColor = colors.surface,
        )
    }

    // Delete confirmation
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete conversation?", color = colors.textPrimary) },
            text = { Text(deleteTarget!!.title, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteConversation(deleteTarget!!)
                    deleteTarget = null
                }) { Text("Delete", color = Color(0xFFF87171)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel", color = colors.textSecondary) }
            },
            containerColor = colors.surface,
        )
    }

    // Rename dialog
    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename conversation", color = colors.textPrimary) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.inputBorder,
                        cursorColor = colors.accent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val newName = renameText.trim()
                    if (newName.isNotEmpty() && renameTarget != null) {
                        onRenameConversation(renameTarget!!, newName)
                    }
                    renameTarget = null
                }) { Text("Save", color = colors.accent) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel", color = colors.textSecondary) }
            },
            containerColor = colors.surface,
        )
    }
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(top = 48.dp),
    ) {
        // Title with logo
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.octobot_avatar),
                contentDescription = null,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(7.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "OctoBot",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
        }

        // New Chat button
        Button(
            onClick = onNewChat,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("New Chat")
        }

Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search conversations", color = colors.textTertiary) },
            leadingIcon = { Icon(Icons.Outlined.Search, "Search conversations", tint = colors.textSecondary) },
            trailingIcon = if (searchQuery.isNotBlank()) {{ IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Outlined.Close, "Clear search", tint = colors.textSecondary) } }} else null,
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary, focusedBorderColor = colors.accent, unfocusedBorderColor = colors.inputBorder, cursorColor = colors.accent),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        )
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = 14.dp))
        Spacer(Modifier.height(8.dp))

        // Recent label
        Text(
            if (searchQuery.isBlank()) "Recent" else "${searchResults.size} matches",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textTertiary,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        )

        // Conversations
        LazyColumn(modifier = Modifier.weight(1f)) {
            if (searchQuery.isNotBlank()) {
                if (searchResults.isEmpty()) item { Text("No matching conversations", fontSize = 13.sp, color = colors.textTertiary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) }
                items(searchResults.size, key = { "${searchResults[it].conversationId}:${searchResults[it].messageTimestamp}" }) { index ->
                    val result = searchResults[index]
                    Column(Modifier.fillMaxWidth().clickable { onSearchResult(result) }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                        Text(result.conversationTitle, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(highlightSearchSnippet(result.content.ifBlank { result.conversationTitle }, searchQuery, colors.accent, colors.textSecondary), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            if (searchQuery.isBlank() && conversations.isEmpty()) {
                item {
                    Text(
                        "No conversations yet",
                        fontSize = 13.sp,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
            }
            items(if (searchQuery.isBlank()) conversations.size else 0) { index ->
                val conv = conversations[index]
                // Per BACKLOG P3 "Rename chat session" â€” long-press still opens the
                // Rename/Delete action menu (kept for power users), AND a tappable
                // pencil icon now sits at the trailing edge for discoverability.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onSelectConversation(conv) },
                            onLongClick = { actionTarget = conv },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = conv.title,
                        fontSize = 14.sp,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 20.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                    )
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Rename conversation",
                        tint = colors.textTertiary,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(18.dp)
                            .clickable {
                                renameText = conv.title
                                renameTarget = conv
                            },
                    )
                }
            }
        }

        HorizontalDivider(color = colors.divider)

        // Bottom nav
        TextButton(
            onClick = onSettings,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Settings, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Settings", color = colors.textSecondary)
            }
        }
        TextButton(
            onClick = onModels,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 0.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.SmartToy, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Models", color = colors.textSecondary)
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ======================== TASK SKILLS PANEL ========================

@Composable
private fun TaskSkillsPanel(
    isLocalModel: Boolean,
    taskMessages: List<ChatMessage>,
    onMonitorClick: () -> Unit,
    onSendClick: () -> Unit,
    onSkillTap: (String) -> Unit,
    activatingSkill: String?,
    monitorActive: Boolean,
    colors: SikoClawColors,
    modifier: Modifier = Modifier,
) {
    val builtInSkills = remember { SkillRegistry.getUserFacing() }
    val categoryIcons = mapOf(
        SkillCategory.INPUT to Icons.Outlined.Keyboard,
        SkillCategory.DISMISS to Icons.Outlined.Close,
        SkillCategory.NAVIGATION to Icons.Outlined.Navigation,
        SkillCategory.MESSAGING to Icons.Outlined.Chat,
        SkillCategory.GENERAL to Icons.Outlined.AutoAwesome,
    )

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Workflows",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
            Text(
                "Background tasks powered by AI â€” things a single prompt can't do.",
                fontSize = 12.sp,
                color = colors.textTertiary,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
            )
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = colors.accent.copy(alpha = 0.12f),
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            ) {
                Text(
                    "Experimental â€” more workflows coming soon",
                    fontSize = 11.sp,
                    color = colors.accent,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }

        // Monitor Messages â€” always shown (background workflow, both modes need it)
        item {
            SkillCard(
                icon = Icons.Outlined.Visibility,
                title = "Monitor Messages",
                description = "Auto-reply to someone's messages in background",
                onClick = onMonitorClick,
                isActivating = activatingSkill == "monitor",
                isActive = monitorActive,
                colors = colors,
            )
        }

        // Send Message â€” available on both (workflow card shortcut)
        item {
            SkillCard(
                icon = Icons.Outlined.Send,
                title = "Send Message",
                description = "Send a message to someone via any messaging app",
                onClick = onSendClick,
                colors = colors,
            )
        }

        // Built-in user-facing skills from SkillRegistry
        if (builtInSkills.isNotEmpty()) {
            items(builtInSkills.size) { index ->
                val skill = builtInSkills[index]
                val example = skill.triggerPatterns.firstOrNull()
                    ?.replace(Regex("\\{\\w+\\}"), "...")
                    ?.replace(".+", "...")
                    ?: skill.name
                SkillCard(
                    icon = categoryIcons[skill.category] ?: Icons.Outlined.AutoAwesome,
                    title = skill.name,
                    description = skill.description,
                    onClick = { onSkillTap(example) },
                    colors = colors,
                )
            }
        }

        // Task progress messages (if any)
        if (taskMessages.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Task progress",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textTertiary,
                )
            }
            items(taskMessages.size) { index ->
                val msg = taskMessages[index]
                if (msg.role == ChatMessage.Role.USER) {
                    UserBubble(msg, colors)
                } else {
                    SystemMessage(msg.content, colors)
                }
            }
        }
    }
}

@Composable
private fun SkillCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    isActivating: Boolean = false,
    isActive: Boolean = false,
    colors: SikoClawColors,
) {
    val activeBlue = Color(0xFF2F80ED)
    val borderColor = when {
        isActive -> activeBlue
        isActivating -> colors.accent
        else -> colors.inputBorder
    }
    val cardBg = when {
        isActive -> activeBlue.copy(alpha = 0.08f)
        else -> colors.surface
    }
    val iconBg = when {
        isActive -> activeBlue.copy(alpha = 0.15f)
        else -> colors.accent.copy(alpha = 0.12f)
    }
    val iconTint = if (isActive) activeBlue else colors.accent

    // Progress animation
    val progress by animateFloatAsState(
        targetValue = if (isActivating) 1f else 0f,
        animationSpec = if (isActivating) tween(2000, easing = LinearEasing) else snap(),
        label = "skillProgress",
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = androidx.compose.foundation.BorderStroke(if (isActive) 1.dp else 0.5.dp, borderColor),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(iconBg, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isActive) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = activeBlue, modifier = Modifier.size(22.dp))
                    } else {
                        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (isActive) activeBlue else colors.textPrimary)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (isActive) "Running in background" else description,
                        fontSize = 12.sp,
                        color = if (isActive) activeBlue.copy(alpha = 0.7f) else colors.textTertiary,
                        lineHeight = 16.sp,
                    )
                }
                if (!isActive && !isActivating) {
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(20.dp))
                }
            }

            // Progress bar during activation
            if (isActivating) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = activeBlue,
                    trackColor = colors.inputBorder,
                )
            }
        }
    }
}

// ======================== SKILL DIALOGS ========================

@Composable
private fun MonitorDialog(
    onDismiss: () -> Unit,
    onStart: (MonitorTargetSpec) -> Unit,
    colors: SikoClawColors,
) {
    var contact by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf("WhatsApp") }
    var appMenuExpanded by remember { mutableStateOf(false) }
    var selectedTone by remember { mutableStateOf("Casual") }
    val apps = MonitorTargetSpec.supportedApps
    val tones = listOf("Casual", "Formal", "Funny")

    // Centered modal overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.44f))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        // Centered card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                ) { /* block clicks from dismissing */ },
            shape = RoundedCornerShape(16.dp),
            color = colors.surface,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(3.dp)
                        .align(Alignment.CenterHorizontally)
                        .background(colors.textTertiary, RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.height(14.dp))

                // Title
                Text(
                    "\uD83D\uDC41\uFE0F Monitor & Auto-Reply",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(12.dp))

                // Contact row: label + input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Target",
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.width(50.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = contact,
                        onValueChange = { contact = it },
                        modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 12.sp,
                            color = colors.textPrimary,
                        ),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier
                                    .background(colors.background, RoundedCornerShape(8.dp))
                                    .then(
                                        Modifier.border(1.dp, colors.inputBorder, RoundedCornerShape(8.dp))
                                    )
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                if (contact.isEmpty()) {
                                    Text("e.g. Mom, +1 555 123 4567", fontSize = 12.sp, color = colors.textTertiary)
                                }
                                innerTextField()
                            }
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))

                // App row: label + dropdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "App",
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.width(50.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.background,
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.inputBorder),
                        ) {
                            Row(
                                modifier = Modifier
                                    .clickable { appMenuExpanded = true }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(selectedApp, fontSize = 12.sp, color = colors.textPrimary)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(14.dp))
                            }
                        }
                        DropdownMenu(
                            expanded = appMenuExpanded,
                            onDismissRequest = { appMenuExpanded = false },
                        ) {
                            apps.forEach { app ->
                                DropdownMenuItem(
                                    text = { Text(app, fontSize = 12.sp) },
                                    onClick = { selectedApp = app; appMenuExpanded = false },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                // Tone row: label + pill chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Tone",
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.width(50.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tones.forEach { tone ->
                            val isOn = tone == selectedTone
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isOn) colors.userBubble.copy(alpha = 0.1f) else colors.background,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isOn) colors.userBubble else colors.inputBorder,
                                ),
                            ) {
                                Text(
                                    tone,
                                    fontSize = 11.sp,
                                    color = if (isOn) colors.accent else colors.textSecondary,
                                    modifier = Modifier
                                        .clickable { selectedTone = tone }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))

                // Start Monitoring button
                Surface(
                    onClick = {
                        val trimmed = contact.trim()
                        if (trimmed.isNotBlank()) {
                            onStart(
                                MonitorTargetSpec(
                                    label = trimmed,
                                    app = selectedApp,
                                    tone = selectedTone,
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = colors.userBubble,
                ) {
                    Text(
                        "Start Monitoring",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 11.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SendMessageDialog(
    onDismiss: () -> Unit,
    onSend: (contact: String, app: String, message: String) -> Unit,
    colors: SikoClawColors,
) {
    var contact by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf("WhatsApp") }
    var appMenuExpanded by remember { mutableStateOf(false) }
    val apps = listOf("WhatsApp", "Telegram", "Messages")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text("Send Message", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
        },
        text = {
            Column {
                Text("With a smarter LLM, you can just type:", fontSize = 11.sp, color = colors.textTertiary)
                Spacer(Modifier.height(2.dp))
                Text("\"send hi to Mom on WhatsApp\"", fontSize = 11.sp, color = colors.accent.copy(alpha = 0.7f))
                Spacer(Modifier.height(16.dp))

                // Fill-in-the-blank: "Send [___] to [___] on [WhatsApp â–¾]"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Send ", fontSize = 15.sp, color = colors.textPrimary)
                    Text("\"", fontSize = 15.sp, color = colors.textTertiary)
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        placeholder = { Text("message", color = colors.textTertiary, fontSize = 14.sp) },
                        modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.inputBorder,
                            cursorColor = colors.accent,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                        ),
                    )
                    Text("\"", fontSize = 15.sp, color = colors.textTertiary)
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("to ", fontSize = 15.sp, color = colors.textPrimary)
                    OutlinedTextField(
                        value = contact,
                        onValueChange = { contact = it },
                        placeholder = { Text("name", color = colors.textTertiary, fontSize = 14.sp) },
                        modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.inputBorder,
                            cursorColor = colors.accent,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                        ),
                    )
                    Text(" on ", fontSize = 15.sp, color = colors.textPrimary)
                    Box {
                        Surface(
                            onClick = { appMenuExpanded = true },
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Transparent,
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.inputBorder),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(selectedApp, fontSize = 13.sp, color = colors.textPrimary)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(16.dp))
                            }
                        }
                        DropdownMenu(
                            expanded = appMenuExpanded,
                            onDismissRequest = { appMenuExpanded = false },
                        ) {
                            apps.forEach { app ->
                                DropdownMenuItem(
                                    text = { Text(app) },
                                    onClick = { selectedApp = app; appMenuExpanded = false },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (contact.isNotBlank() && message.isNotBlank()) onSend(contact.trim(), selectedApp, message.trim()) },
                enabled = contact.isNotBlank() && message.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Send")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        },
    )
}

// ======================== ACTIVE TASK BAR ========================

@Composable
private fun ActiveTaskBar(
    tasks: List<String>,
    onStopTask: (String) -> Unit,
    onStopAll: () -> Unit,
    colors: SikoClawColors,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
    ) {
        // Monitor tasks bar
        if (tasks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = androidx.compose.ui.graphics.Color(0xFF4CAF50),
                            shape = androidx.compose.foundation.shape.CircleShape,
                        )
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (tasks.size == 1) "Monitoring: ${tasks[0]}" else "${tasks.size} monitoring",
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (expanded) "â–´" else "â–¾",
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                )
            }
        }

        // Expanded â€” show each task with stop button
        if (expanded) {
            Divider(color = colors.textSecondary.copy(alpha = 0.2f), thickness = 0.5.dp)
            tasks.forEach { task ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                color = androidx.compose.ui.graphics.Color(0xFF4CAF50),
                                shape = androidx.compose.foundation.shape.CircleShape,
                            )
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = task,
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "Stop",
                        color = androidx.compose.ui.graphics.Color(0xFFF44336),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable { onStopTask(task) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            if (tasks.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        text = "Stop All",
                        color = androidx.compose.ui.graphics.Color(0xFFF44336),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable { onStopAll() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}






private fun highlightSearchSnippet(text: String, query: String, highlight: Color, normal: Color): AnnotatedString {
    val clean = text.replace(Regex("\\s+"), " ").trim()
    val match = clean.indexOf(query, ignoreCase = true)
    val start = if (match < 0) 0 else (match - 42).coerceAtLeast(0)
    val end = if (match < 0) clean.length.coerceAtMost(110) else (match + query.length + 68).coerceAtMost(clean.length)
    val snippet = (if (start > 0) "…" else "") + clean.substring(start, end) + (if (end < clean.length) "…" else "")
    return buildAnnotatedString {
        append(snippet)
        if (query.isNotBlank()) {
            var cursor = 0
            while (true) {
                val found = snippet.indexOf(query, cursor, ignoreCase = true)
                if (found < 0) break
                addStyle(SpanStyle(color = highlight, fontWeight = FontWeight.SemiBold), found, found + query.length)
                cursor = found + query.length
            }
        }
        addStyle(SpanStyle(color = normal), 0, length)
    }
}
