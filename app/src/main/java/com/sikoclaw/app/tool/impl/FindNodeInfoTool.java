// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package com.sikoclaw.app.tool.impl;

import android.view.accessibility.AccessibilityNodeInfo;

import com.sikoclaw.app.ClawApplication;
import com.sikoclaw.app.R;
import com.sikoclaw.app.service.ClawAccessibilityService;
import com.sikoclaw.app.tool.BaseTool;
import com.sikoclaw.app.tool.ToolParameter;
import com.sikoclaw.app.tool.ToolResult;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FindNodeInfoTool extends BaseTool {

    @Override
    public String getName() {
        return "find_node_info";
    }

    @Override
    public String getDisplayName() {
        return ClawApplication.Companion.getInstance().getString(R.string.tool_name_find_node_info);
    }

    @Override
    public String getDescriptionEN() {
        return "Find elements by visible text and return their detailed information (class, bounds, properties). Useful for inspecting specific elements before interacting.";
    }

    @Override
    public String getDescriptionCN() {
        return "Find an element by visible text and return detailed information (class name, bounds, attributes). Use this to inspect a specific element before interacting with it.";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Collections.singletonList(
                new ToolParameter("text", "string", "The visible text to search for", true)
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        ClawAccessibilityService service = requireAccessibilityService();
        if (service == null) {
            return ToolResult.error("Accessibility service is not running");
        }

        String text = requireString(params, "text");
        List<AccessibilityNodeInfo> nodes = service.findNodesByText(text);

        if (nodes.isEmpty()) {
            return ToolResult.error("No elements found with text: " + text);
        }

        try {
            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(nodes.size()).append(" element(s):\n");
            for (int i = 0; i < nodes.size(); i++) {
                sb.append("[").append(i).append("] ").append(service.getNodeDetail(nodes.get(i))).append("\n");
            }
            return ToolResult.success(sb.toString());
        } finally {
            ClawAccessibilityService.recycleNodes(nodes);
        }
    }
}
