// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package com.sikoclaw.app.ui.chat

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.sikoclaw.app.AppCapabilityCoordinator
import com.sikoclaw.app.AppViewModel
import com.sikoclaw.app.ServiceBindingState
import com.sikoclaw.app.TaskEvent
import com.sikoclaw.app.agent.DirectDeviceDataGuard
import com.sikoclaw.app.agent.PipelineRouter
import com.sikoclaw.app.agent.TaskPromptEnvelope
import com.sikoclaw.app.agent.llm.ModelConfigRepository
import com.sikoclaw.app.service.ClawAccessibilityService
import com.sikoclaw.app.service.ForegroundService
import com.sikoclaw.app.service.AutoReplyManager
import com.sikoclaw.app.tool.ToolRegistry
import com.sikoclaw.app.ui.settings.SettingsActivity
import com.sikoclaw.app.utils.KVUtils
import com.sikoclaw.app.utils.XLog
import java.util.concurrent.ExecutorService

data class TaskFlowUiState(
    val messages: SnapshotStateList<ChatMessage>,
    val modelStatus: MutableState<String>,
    val isAwaitingReply: MutableState<Boolean>,
    val isTaskRunning: MutableState<Boolean>,
)

/**
 * Owns task-mode send flow, typed TaskEvent rendering, and monitor start wiring.
 *
 * ComposeChatActivity keeps the shell; this controller keeps task-specific behavior.
 */
class TaskFlowController(
    private val activity: ComponentActivity,
    private val executor: ExecutorService,
    private val appViewModel: AppViewModel,
    private val chatSessionController: ChatSessionController,
    private val currentConversationId: () -> String,
    private val uiState: TaskFlowUiState,
    private val onPersistConversation: () -> Unit,
    private val onTaskSettled: (() -> Unit)? = null,
    private val onTaskTerminal: ((TaskEvent) -> Unit)? = null,
) {

    companion object {
        private const val TAG = "TaskFlowController"
    }

    private var sendTaskRetryCount = 0
    private var lastMonitorStatusNote: String? = null
    private val liveRawContent = StringBuilder()
    private val pipelineRouter = PipelineRouter(activity)

    fun sendTask(text: String) {
        if (com.sikoclaw.app.floating.FloatingAssistantConfig.enabled() && com.sikoclaw.app.floating.FloatingAssistantConfig.autoStart()) {
            runCatching { androidx.core.content.ContextCompat.startForegroundService(activity, Intent(activity, com.sikoclaw.app.floating.FloatingAssistantService::class.java)) }
        }
        if (appViewModel.isTaskRunning()) {
            addSystem("Another task is still running. Stop it first.")
            onTaskTerminal?.invoke(TaskEvent.Failed("Another task is still running. Stop it first."))
            return
        }

        if (ModelConfigRepository.snapshot().isLocalActive() && isLikelyMonitorRequest(text)) {
            addUser(text)
            addSystem("Local mode starts monitoring from the Background card. Open Background, choose the app/contact, then tap Start Monitoring.")
            onTaskTerminal?.invoke(TaskEvent.Failed("Local mode starts monitoring from the Background card."))
            return
        }

        DirectDeviceDataGuard.deterministicToolCall(text)?.let { directTool ->
            XLog.i(TAG, "sendTask: executing deterministic direct tool before LLM/accessibility gates")
            executeDirectToolTask(text, directTool)
            return
        }

        when (AppCapabilityCoordinator.accessibilityState(activity)) {
            ServiceBindingState.DISABLED -> {
                val directTool = DirectDeviceDataGuard.deterministicToolCall(text)
                if (directTool != null) {
                    XLog.i(TAG, "sendTask: executing non-interactive direct tool without Accessibility")
                    executeDirectToolTask(text, directTool)
                    return
                }
                if (canRunWithoutAccessibility(text)) {
                    XLog.i(TAG, "sendTask: allowing non-interactive task without Accessibility")
                } else {
                Toast.makeText(activity, "Enable Accessibility Service to run tasks", Toast.LENGTH_LONG).show()
                addSystem("âš ï¸ Task mode needs Accessibility Service enabled. Opening Settings...")
                openSettings()
                sendTaskRetryCount = 0
                onTaskTerminal?.invoke(TaskEvent.Failed("Accessibility Service is required for this task."))
                return
                }
            }
            ServiceBindingState.CONNECTING -> {
                val directTool = DirectDeviceDataGuard.deterministicToolCall(text)
                if (directTool != null) {
                    XLog.i(TAG, "sendTask: executing non-interactive direct tool while Accessibility connects")
                    executeDirectToolTask(text, directTool)
                    return
                }
                if (canRunWithoutAccessibility(text)) {
                    XLog.i(TAG, "sendTask: allowing non-interactive task while Accessibility connects")
                } else {
                if (sendTaskRetryCount >= 1) {
                    Toast.makeText(activity, "Accessibility service not connected. Try toggling it off and on.", Toast.LENGTH_LONG).show()
                    addSystem("Accessibility service didn't connect. Try toggling it off and on in Settings.")
                    openSettings()
                    sendTaskRetryCount = 0
                    onTaskTerminal?.invoke(TaskEvent.Failed("Accessibility service did not connect."))
                    return
                }
                sendTaskRetryCount++
                addSystem("Accessibility service connecting, please wait...")
                executor.submit {
                    val connected = ClawAccessibilityService.awaitRunning(5000)
                    activity.runOnUiThread {
                        if (connected) {
                            sendTask(text)
                        } else {
                            Toast.makeText(activity, "Accessibility service didn't connect", Toast.LENGTH_LONG).show()
                            addSystem("Accessibility service didn't connect. Go to Settings and toggle it off then on.")
                            sendTaskRetryCount = 0
                            onTaskTerminal?.invoke(TaskEvent.Failed("Accessibility service did not connect."))
                        }
                    }
                }
                return
                }
            }
            ServiceBindingState.DEGRADED -> {
                val directTool = DirectDeviceDataGuard.deterministicToolCall(text)
                if (directTool != null) {
                    XLog.i(TAG, "sendTask: executing non-interactive direct tool while Accessibility is degraded")
                    executeDirectToolTask(text, directTool)
                    return
                }
                if (canRunWithoutAccessibility(text)) {
                    XLog.i(TAG, "sendTask: allowing non-interactive task while Accessibility is degraded")
                } else {
                    Toast.makeText(activity, "Accessibility service disconnected. Open Settings and toggle it back on.", Toast.LENGTH_LONG).show()
                    addSystem("Accessibility service disconnected. Open Settings and toggle it off then on.")
                    openSettings()
                    sendTaskRetryCount = 0
                    onTaskTerminal?.invoke(TaskEvent.Failed("Accessibility service is disconnected."))
                    return
                }
            }
            ServiceBindingState.READY -> Unit
        }
        sendTaskRetryCount = 0

        ensureNotificationPermission()
        uiState.isAwaitingReply.value = false
        uiState.isTaskRunning.value = false

        if (!KVUtils.hasLlmConfig()) {
            Toast.makeText(activity, "Configure LLM in Settings first", Toast.LENGTH_LONG).show()
            onTaskTerminal?.invoke(TaskEvent.Failed("Configure LLM in Settings first."))
            return
        }

        val agentPromptOverride = buildAgentPromptOverride(text)
        liveRawContent.clear()
        com.sikoclaw.app.agent.memory.ExplicitMemoryCapture.capture(text)
        addUser(text)
        uiState.isAwaitingReply.value = true
        uiState.isTaskRunning.value = false
        XLog.i(TAG, "sendTask: isProcessing=TRUE")
        uiState.messages.add(ChatMessage(ChatMessage.Role.ASSISTANT, "..."))

        val taskId = "task_${System.currentTimeMillis()}"

        executor.submit {
            chatSessionController.prepareForTaskStart()

            activity.runOnUiThread {
                try {
                    appViewModel.startTask(text, taskId, agentPromptOverride = agentPromptOverride) { event ->
                        activity.runOnUiThread { handleTaskEvent(event) }
                    }
                } catch (e: Exception) {
                    XLog.e(TAG, "sendTask failed: ${e.message}", e)
                    addSystem("Error: ${e.message}")
                    cleanupAfterTask()
                }
            }
        }
    }

    private fun executeDirectToolTask(text: String, toolCall: DirectDeviceDataGuard.DeterministicToolCall) {
        ensureNotificationPermission()
        addUser(text)
        uiState.isAwaitingReply.value = true
        uiState.isTaskRunning.value = false
        uiState.messages.add(ChatMessage(ChatMessage.Role.ASSISTANT, "..."))

        executor.submit {
            try {
                val result = ToolRegistry.getInstance().executeTool(toolCall.toolName, toolCall.params)
                activity.runOnUiThread {
                    val answer = result.data ?: result.error ?: "Done."
                    replaceTypingIndicator(answer)
                    onTaskTerminal?.invoke(TaskEvent.Completed(answer))
                    cleanupAfterTask()
                }
            } catch (e: Exception) {
                XLog.e(TAG, "executeDirectToolTask failed: ${e.message}", e)
                activity.runOnUiThread {
                    replaceTypingIndicator("Error: ${e.message}")
                    onTaskTerminal?.invoke(TaskEvent.Failed(e.message ?: "Direct tool failed"))
                    cleanupAfterTask()
                }
            }
        }
    }

    private fun canRunWithoutAccessibility(text: String): Boolean {
        if (DirectDeviceDataGuard.matchesNonInteractiveDeviceDataTask(text)) {
            return true
        }
        return when (pipelineRouter.route(text)) {
            is PipelineRouter.Route.DirectIntent -> true
            else -> false
        }
    }

    fun handleMonitorTask(text: String) {
        val target = MonitorTargetParser.fromTaskText(text)
        if (target == null) {
            addUser(text)
            addSystem("Could not figure out who to monitor. Try: \"Monitor Mom on WhatsApp\"")
            return
        }

        startMonitor(target, typedInput = text)
    }

    fun startMonitor(target: MonitorTargetSpec, typedInput: String? = null) {
        val trimmedLabel = target.label.trim()
        if (trimmedLabel.isEmpty()) {
            addSystem("Could not figure out who to monitor. Try: \"Monitor Mom on WhatsApp\"")
            return
        }

        typedInput?.let { addUser(it) }
        val missing = AppCapabilityCoordinator.missingMonitorRequirements(activity)
        if (missing.isNotEmpty()) {
            Toast.makeText(
                activity,
                "Enable ${missing.joinToString(" & ") { it.label }} in Settings first",
                Toast.LENGTH_LONG
            ).show()
            openSettings()
            onTaskTerminal?.invoke(TaskEvent.Failed("Missing required permissions for monitoring."))
            return
        }

        val contact = trimmedLabel
        val app = target.app
        uiState.isAwaitingReply.value = false
        uiState.isTaskRunning.value = false
        addSystem("Setting up auto-reply for $contact on $app...")

        val autoReplyManager = AutoReplyManager.getInstance()
        autoReplyManager.addTarget(contact, app)
        autoReplyManager.setEnabled(true)
        XLog.i(TAG, "startMonitor: enabled auto-reply for '${target.displayLabel}'")

        Handler(Looper.getMainLooper()).postDelayed({
            uiState.isAwaitingReply.value = false
            uiState.isTaskRunning.value = false
            addSystem("âœ“ Auto-reply is now active for ${target.displayLabel}.\nMonitoring in background â€” you can stop anytime from the bar above.")
            XLog.i(TAG, "startMonitor: monitor active, staying in OctoBot")
        }, 1500)
    }

    private fun handleTaskEvent(event: TaskEvent) {
        try {
            when (event) {
                is TaskEvent.Completed -> {
                    replaceTypingIndicator(event.answer, event.modelName)
                    onTaskTerminal?.invoke(event)
                    cleanupAfterTask()
                    checkAutoReplyConfirmation()
                }
                is TaskEvent.Failed -> {
                    replaceTypingIndicator("Error: ${event.error}")
                    onTaskTerminal?.invoke(event)
                    cleanupAfterTask()
                }
                is TaskEvent.Cancelled -> {
                    removeTypingIndicator()
                    onTaskTerminal?.invoke(event)
                    cleanupAfterTask()
                }
                is TaskEvent.Blocked -> {
                    replaceTypingIndicator("Blocked by system dialog.")
                    onTaskTerminal?.invoke(event)
                    cleanupAfterTask()
                }
                is TaskEvent.ToolAction -> {
                    if (event.toolName.contains("tap", true) || event.toolName.contains("swipe", true) || event.toolName.contains("input", true) || event.toolName.contains("pointer", true) || event.toolName.contains("screen", true)) {
                        com.sikoclaw.app.service.AgentControlSession.start()
                    }
                    uiState.isAwaitingReply.value = false
                    uiState.isTaskRunning.value = true
                    if (!event.toolName.contains("Finish", ignoreCase = true)) {
                        removeTypingIndicator()
                        val summary = toolSummary(event.rawToolName, event.parameters)
                        ToolActivityReducer.start(
                            uiState.messages,
                            ToolStep(
                                toolName = event.toolName,
                                summary = summary,
                                callId = event.callId,
                                details = event.parameters.take(4_000),
                                status = ToolActivityStatus.RUNNING,
                                rawToolName = event.rawToolName,
                            )
                        )
                    }
                }
                is TaskEvent.ToolResult -> {
                    uiState.isAwaitingReply.value = false
                    uiState.isTaskRunning.value = true
                    ToolActivityReducer.finish(
                        uiState.messages,
                        event.callId,
                        event.success,
                        event.detail.take(4_000),
                    )
                }
                is TaskEvent.Response -> {
                    uiState.isAwaitingReply.value = false
                    replaceTypingIndicator(event.text)
                }
                is TaskEvent.Progress -> {
                    uiState.isAwaitingReply.value = false
                    uiState.isTaskRunning.value = true
                    addSystem(event.description)
                }
                is TaskEvent.LoopStart -> {
                    uiState.isAwaitingReply.value = false
                    uiState.isTaskRunning.value = true
                }
                is TaskEvent.ContentDelta -> {
                    uiState.isAwaitingReply.value = false
                    uiState.isTaskRunning.value = true
                    appendLiveContent(event.content)
                }
                is TaskEvent.ReasoningDelta -> appendLiveReasoning(event.content)
                is TaskEvent.TokenUpdate -> Unit
            }
        } catch (e: Exception) {
            XLog.w(TAG, "handleTaskEvent error", e)
            com.sikoclaw.app.service.AgentControlSession.stop(true)
        }
    }

    private fun replaceTypingIndicator(text: String, actualModelName: String? = null) {
        val modelTag = actualModelName
            ?: uiState.modelStatus.value.removePrefix("â— ").split(" Â·").firstOrNull()?.trim()
            ?: ""
        val cleanText = finalAssistantText(text).ifBlank { "I couldn't produce a final response. Please try again." }
        val parts = cleanText.split("<message-break>").map(String::trim).filter(String::isNotBlank).ifEmpty { listOf(cleanText) }
        val idx = uiState.messages.indexOfLast { it.role == ChatMessage.Role.ASSISTANT && (it.content == "..." || it.isStreaming) }
        if (idx >= 0) {
            uiState.messages[idx] = ChatMessage(ChatMessage.Role.ASSISTANT, parts.first(), modelName = modelTag)
            parts.drop(1).forEachIndexed { offset, part -> uiState.messages.add(idx + offset + 1, ChatMessage(ChatMessage.Role.ASSISTANT, part, modelName = modelTag)) }
        } else {
            parts.forEach { uiState.messages.add(ChatMessage(ChatMessage.Role.ASSISTANT, it, modelName = modelTag)) }
        }
        val reasoningIndex = uiState.messages.indexOfLast { it.role == ChatMessage.Role.REASONING && it.isStreaming }
        if (reasoningIndex >= 0) uiState.messages[reasoningIndex] = uiState.messages[reasoningIndex].copy(isStreaming = false)
        liveRawContent.clear()
        onPersistConversation()
    }

    private fun appendLiveContent(token: String) {
        liveRawContent.append(token)
        val parsed = AssistantStreamParser.parse(liveRawContent.toString())
        if (KVUtils.getBoolean("SHOW_AGENT_THOUGHTS", false) && parsed.reasoning.isNotBlank()) {
            updateReasoningMessage(parsed.reasoning)
        }
        if (parsed.visible.isBlank()) return
        val idx = uiState.messages.indexOfLast { it.role == ChatMessage.Role.ASSISTANT && (it.content == "..." || it.isStreaming) }
        val message = ChatMessage(ChatMessage.Role.ASSISTANT, parsed.visible, isStreaming = true)
        if (idx >= 0) uiState.messages[idx] = message else uiState.messages.add(message)
    }

    private fun appendLiveReasoning(token: String) {
        if (!KVUtils.getBoolean("SHOW_AGENT_THOUGHTS", false)) return
        val previous = uiState.messages.lastOrNull { it.role == ChatMessage.Role.REASONING && it.isStreaming }?.content.orEmpty()
        updateReasoningMessage(previous + token)
    }

    private fun updateReasoningMessage(text: String) {
        val idx = uiState.messages.indexOfLast { it.role == ChatMessage.Role.REASONING && it.isStreaming }
        val message = ChatMessage(ChatMessage.Role.REASONING, text, isStreaming = true)
        if (idx >= 0) uiState.messages[idx] = message else {
            val typing = uiState.messages.indexOfLast { it.role == ChatMessage.Role.ASSISTANT && it.content == "..." }
            if (typing >= 0) uiState.messages.add(typing, message) else uiState.messages.add(message)
        }
    }

    private fun removeTypingIndicator() {
        val idx = uiState.messages.indexOfLast { it.role == ChatMessage.Role.ASSISTANT && it.content == "..." }
        if (idx >= 0) uiState.messages.removeAt(idx)
    }

    private fun cleanupAfterTask() {
        com.sikoclaw.app.service.AgentControlSession.stop()
        XLog.i(TAG, "cleanupAfterTask: isProcessing=FALSE")
        uiState.isAwaitingReply.value = false
        uiState.isTaskRunning.value = false
        appViewModel.clearTaskCallback()
        onTaskSettled?.invoke()
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                chatSessionController.loadModelIfReady(
                    conversationId = currentConversationId(),
                    visibleMessages = uiState.messages.toList(),
                )
            } catch (e: Exception) {
                XLog.e(TAG, "cleanupAfterTask: loadModel error", e)
            }
        }, 500)
    }

    private fun checkAutoReplyConfirmation() {
        val autoReplyManager = AutoReplyManager.getInstance()
        if (!autoReplyManager.isEnabled) {
            lastMonitorStatusNote = null
            return
        }
        val contacts = autoReplyManager.monitoredContacts.joinToString(", ")
        if (contacts.isBlank()) {
            lastMonitorStatusNote = null
            return
        }
        val note = "âœ“ Auto-reply active for $contacts.\nMonitoring in background â€” stop from bar above."
        if (note == lastMonitorStatusNote) return
        addSystem(note)
        lastMonitorStatusNote = note
        XLog.i(TAG, "checkAutoReplyConfirmation: monitor active, staying in OctoBot")
    }

    private fun ensureNotificationPermission() {
        if (!AppCapabilityCoordinator.isNotificationPermissionGranted(activity)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun addUser(text: String) {
        val payload = PluginMessageCodec.decode(text)
        val attachmentPayload = ChatAttachmentManager.decode(payload.visibleText)
        uiState.messages.add(ChatMessage(ChatMessage.Role.USER, attachmentPayload.visibleText, pluginIds = payload.pluginIds, attachments = attachmentPayload.attachments))
    }

    private fun addSystem(text: String) {
        uiState.messages.add(ChatMessage(ChatMessage.Role.SYSTEM, text))
    }

    private fun toolSummary(rawName: String, parameters: String): String {
        val compact = parameters.replace(Regex("\\s+"), " ").trim().take(180)
        val verb = when {
            rawName.contains("terminal", true) || rawName.contains("shell", true) -> "Running"
            rawName.contains("search", true) -> "Searching"
            rawName.contains("file", true) || rawName.contains("document", true) -> "Accessing file"
            rawName.contains("mcp", true) -> "Using connection"
            rawName.contains("browser", true) || rawName.contains("web", true) -> "Browsing"
            else -> "Working"
        }
        return if (compact.isBlank() || compact == "{}") verb else "$verb: $compact"
    }

    private fun openSettings() {
        activity.startActivity(Intent(activity, SettingsActivity::class.java))
    }

    private fun buildAgentPromptOverride(rawTask: String): String? {
        if (ModelConfigRepository.snapshot().isLocalActive()) {
            return null
        }

        val historyLines = CloudContextHandoffFormatter.conversationLines(uiState.messages)
        val backgroundStatus = buildBackgroundStatusContext()

        return TaskPromptEnvelope.build(
            chatHistoryLines = historyLines,
            currentRequest = rawTask,
            backgroundState = backgroundStatus,
        )
    }

    private fun buildBackgroundStatusContext(): String? {
        val autoReplyManager = AutoReplyManager.getInstance()
        if (!autoReplyManager.isEnabled) return null

        val contacts = autoReplyManager.monitoredContacts.toList()
        if (contacts.isEmpty()) return null

        return buildString {
            append("Background monitor active for: ")
            append(contacts.joinToString(", "))
            append('.')
        }
    }

    private fun isLikelyMonitorRequest(text: String): Boolean {
        val lower = text.lowercase()
        val mentionsMonitor = lower.contains("monitor") ||
            lower.contains("auto-reply") ||
            lower.contains("auto reply") ||
            lower.contains("autoreply")
        val looksLikeWatchMessages = lower.contains("watch") &&
            (lower.contains("message") || lower.contains("messages") || lower.contains("reply"))
        return mentionsMonitor || looksLikeWatchMessages
    }
}

