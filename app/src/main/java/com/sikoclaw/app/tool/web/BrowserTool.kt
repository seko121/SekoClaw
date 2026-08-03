package com.sikoclaw.app.tool.web

import com.sikoclaw.app.ClawApplication
import com.sikoclaw.app.plugin.PluginCatalog
import com.sikoclaw.app.tool.BaseTool
import com.sikoclaw.app.tool.ToolParameter
import com.sikoclaw.app.tool.ToolResult
import com.sikoclaw.app.ui.web.WebActivity
import java.net.URLEncoder

class BrowserTool : BaseTool() {
    override fun getName() = "open_browser"
    override fun getDisplayName() = "Firefox Browser"
    override fun getDescriptionEN() = "Open the visible Firefox-based OctoBot browser at a URL or search query. Continue with phone-control tools to inspect and interact while the user watches."
    override fun getDescriptionCN() = getDescriptionEN()
    override fun getParameters() = listOf(ToolParameter("url", "string", "HTTPS URL or search words", true))
    override fun execute(params: Map<String, Any>): ToolResult {
        if (!PluginCatalog.isEnabled("browser")) return ToolResult.error("Browser plugin is disabled")
        val raw = requireString(params, "url").trim()
        val target = if (raw.startsWith("https://")) raw else "https://www.google.com/search?q=${URLEncoder.encode(raw, "UTF-8")}"
        return runCatching { WebActivity.start(ClawApplication.instance, target); ToolResult.success("Opened the visible OctoBot browser: $target") }
            .getOrElse { ToolResult.error("Could not open browser: ${it.message}") }
    }
}
