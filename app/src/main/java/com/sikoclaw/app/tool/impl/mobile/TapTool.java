// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package com.sikoclaw.app.tool.impl.mobile;

import com.sikoclaw.app.ClawApplication;
import com.sikoclaw.app.R;
import com.sikoclaw.app.service.ClawAccessibilityService;
import com.sikoclaw.app.tool.BaseTool;
import com.sikoclaw.app.tool.ToolParameter;
import com.sikoclaw.app.tool.ToolResult;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TapTool extends BaseTool {

    @Override
    public String getName() {
        return "tap";
    }

    @Override
    public String getDisplayName() {
        return ClawApplication.Companion.getInstance().getString(R.string.tool_name_tap);
    }

    @Override
    public String getDescriptionEN() {
        return "Tap at the specified screen coordinates (x, y).";
    }

    @Override
    public String getDescriptionCN() {
        return "Tap at the specified screen coordinates (x, y).";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
                new ToolParameter("x", "integer", "X coordinate on screen", true),
                new ToolParameter("y", "integer", "Y coordinate on screen", true)
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        ClawAccessibilityService service = requireAccessibilityService();
        if (service == null) {
            return ToolResult.error("Accessibility service is not running");
        }
        int x = requireInt(params, "x");
        int y = requireInt(params, "y");
        String boundsError = validateCoordinates(x, y);
        if (boundsError != null) return ToolResult.error(boundsError);
        boolean success = service.performTap(x, y);
        return success ? ToolResult.success("Tapped at (" + x + ", " + y + ")")
                : ToolResult.error("Failed to tap at (" + x + ", " + y + ")");
    }
}
