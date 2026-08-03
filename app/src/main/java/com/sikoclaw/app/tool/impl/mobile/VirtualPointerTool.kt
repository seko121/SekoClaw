package com.sikoclaw.app.tool.impl.mobile

import com.sikoclaw.app.plugin.PluginCatalog
import com.sikoclaw.app.service.AgentControlOverlay
import com.sikoclaw.app.tool.BaseTool
import com.sikoclaw.app.tool.ToolParameter
import com.sikoclaw.app.tool.ToolResult

class VirtualPointerTool : BaseTool() {
    override fun getName() = "virtual_pointer"
    override fun getDisplayName() = "Virtual Pointer"
    override fun getDescriptionEN() = "Move the visible agent pointer, optionally clicking at the destination. Coordinates are physical screen pixels."
    override fun getDescriptionCN() = getDescriptionEN()
    override fun getParameters() = listOf(
        ToolParameter("x", "integer", "Horizontal screen coordinate", true),
        ToolParameter("y", "integer", "Vertical screen coordinate", true),
        ToolParameter("click", "boolean", "Click after moving, default false", false),
    )
    override fun execute(params: Map<String, Any>): ToolResult {
        if (!PluginCatalog.isEnabled("phone_control")) return ToolResult.error("Phone control plugin is disabled")
        val service = requireAccessibilityService() ?: return ToolResult.error("Accessibility service is not connected")
        val x = requireInt(params, "x"); val y = requireInt(params, "y")
        AgentControlOverlay.movePointer(x, y)
        val click = params["click"]?.toString()?.toBooleanStrictOrNull() ?: false
        if (click && !service.performTap(x, y)) return ToolResult.error("Pointer moved but click failed")
        return ToolResult.success(if (click) "Pointer clicked at ($x, $y)" else "Pointer moved to ($x, $y)")
    }
}
