// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package com.sikoclaw.app.tool

import com.sikoclaw.app.agent.knowledge.*
import com.sikoclaw.app.tool.impl.*
import com.sikoclaw.app.tool.impl.mobile.*
import com.sikoclaw.app.tool.impl.tv.*

object ToolRegistry {

    enum class DeviceType { TV, MOBILE }

    private val tools = LinkedHashMap<String, BaseTool>()
    var deviceType: DeviceType = DeviceType.TV
        private set

    @JvmStatic
    fun getInstance(): ToolRegistry = this

    fun registerAllTools(type: DeviceType = DeviceType.TV) {
        deviceType = type
        tools.clear()
        registerCommonTools()
        when (type) {
            DeviceType.TV -> registerTvTools()
            DeviceType.MOBILE -> registerMobileTools()
        }
    }

    private fun registerCommonTools() {
        register(GetScreenInfoTool())
        register(FindNodeInfoTool())
        register(InputTextTool())
        register(SystemKeyTool())
        register(OpenAppTool())
        register(GetInstalledAppsTool())
        register(TakeScreenshotTool())
        register(WaitTool())
        register(RepeatActionsTool())
        register(ClipboardTool())
        register(SendFileTool())
        register(GetDeviceInfoTool())
        register(GetNotificationsTool())
        register(ReadSmsTool())
        register(DraftSmsTool())
        register(MakeCallTool())
        register(FinishTool())
        // Knowledge Base tools — shared vault available in all modes
        register(KbWriteTool())
        register(KbReadTool())
        register(KbSearchTool())
        register(KbAppendTool())
        register(KbAddTodoTool())
        register(com.sikoclaw.app.agent.memory.AgentMemoryTool())
        register(AgentWorkspaceTool())
        register(com.sikoclaw.app.agent.skill.CreateSkillTool())
        register(com.sikoclaw.app.cron.CronTool())
        register(com.sikoclaw.app.tool.web.WebSearchTool())
        register(com.sikoclaw.app.tool.web.DownloadFileTool())
        register(com.sikoclaw.app.tool.web.ImageGenerationTool())
        register(com.sikoclaw.app.tool.web.SpeechGenerationTool())
        register(com.sikoclaw.app.tool.web.VisionTool())
        register(com.sikoclaw.app.tool.document.CreateArtifactTool())
        com.sikoclaw.app.tool.document.documentStudioTools().forEach(::register)
        register(com.sikoclaw.app.tool.web.BrowserTool())
        register(LinuxSandboxTool())
    }

    private fun registerTvTools() {
        register(DpadUpTool())
        register(DpadDownTool())
        register(DpadLeftTool())
        register(DpadRightTool())
        register(DpadCenterTool())
        register(VolumeUpTool())
        register(VolumeDownTool())
        register(PressMenuTool())
        register(PressPowerTool())
    }

    private fun registerMobileTools() {
        register(VirtualPointerTool())
        register(TapTool())
        register(TapNodeTool())
        register(LongPressTool())
        register(SwipeTool())
        register(ScrollToFindTool())
        register(FindAndTapTool())
        register(SendMessageTool())
        register(AutoReplyTool())
    }

    fun register(tool: BaseTool) {
        tools[tool.getName()] = tool
    }
    fun unregisterPrefix(prefix: String) { tools.keys.filter { it.startsWith(prefix) }.forEach(tools::remove) }

    fun getTool(name: String): BaseTool? = tools[name]

    fun getDisplayName(name: String): String = tools[name]?.getDisplayName() ?: name

    fun getAllRegisteredTools(): List<BaseTool> = tools.values.toList()

    fun getAllTools(): List<BaseTool> = tools.values.filter {
        it.getName() == "finish" || (com.sikoclaw.app.utils.KVUtils.isToolEnabled(it.getName()) && pluginAllows(it.getName()))
    }

    private fun pluginAllows(toolName: String): Boolean {
        val pluginId = when (toolName) {
            "web_search", "download_file" -> "web_search"
            "generate_image" -> "image_generation"
            "generate_speech" -> "tts"
            "create_pdf", "read_pdf", "edit_pdf", "create_docx", "read_docx", "edit_docx",
            "create_xlsx", "read_xlsx", "edit_xlsx", "create_pptx", "read_pptx", "edit_pptx" -> "document_studio"
            "open_browser" -> "browser"
            "tap", "tap_node", "long_press", "swipe", "virtual_pointer", "input_text", "system_key", "get_screen_info", "take_screenshot" -> "phone_control"
            else -> return true
        }
        return com.sikoclaw.app.plugin.PluginCatalog.isEnabled(pluginId)
    }

    fun executeTool(name: String, params: Map<String, Any>): ToolResult {
        val tool = tools[name] ?: return ToolResult.error("Unknown tool: $name")
        if (com.sikoclaw.app.agent.HumanApprovalManager.requiresApproval(name, params) &&
            !com.sikoclaw.app.agent.HumanApprovalManager.requestBlocking(name, params)
        ) return ToolResult.error("User denied or did not answer the approval request")
        return try {
            tool.executeWithWaitAfter(params)
        } catch (e: Exception) {
            com.sikoclaw.app.utils.XLog.e("ToolRegistry", "Tool '$name' execution failed with params=$params", e)
            ToolResult.error("Tool execution failed: ${e.message}")
        }
    }
}
