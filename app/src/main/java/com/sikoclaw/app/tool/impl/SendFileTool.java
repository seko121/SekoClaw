// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package com.sikoclaw.app.tool.impl;

import android.os.Build;
import android.os.Environment;

import androidx.core.content.ContextCompat;

import com.sikoclaw.app.ClawApplication;
import com.sikoclaw.app.ClawApplicationKt;
import com.sikoclaw.app.R;
import com.sikoclaw.app.channel.Channel;
import com.sikoclaw.app.channel.ChannelManager;
import com.sikoclaw.app.tool.BaseTool;
import com.sikoclaw.app.tool.ToolParameter;
import com.sikoclaw.app.tool.ToolResult;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SendFileTool extends BaseTool {

    @Override
    public String getName() {
        return "send_file";
    }

    @Override
    public String getDisplayName() {
        return ClawApplication.Companion.getInstance().getString(R.string.tool_name_send_file);
    }

    @Override
    public String getDescriptionEN() {
        return "Send a file from the device to the user through the current message channel. Provide the absolute file path on the device.";
    }

    @Override
    public String getDescriptionCN() {
        return "Send a file on the device to the user. The absolute path of the file must be provided.";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Collections.singletonList(
                new ToolParameter("file_path", "string", "Absolute path of the file to send", true)
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        android.content.Context ctx = ClawApplication.Companion.getInstance();
        String filePath = requireString(params, "file_path");
        // Files may originate in Android storage, OctoBot's workspace, or the Linux
        // sandbox. Export them to the attachment cache before exposing a URI.
        // This is synchronous: success is returned only after the chat rendered it.
        try {
            com.sikoclaw.app.ui.chat.ChatAttachment attachment =
                    com.sikoclaw.app.files.AgentFileManager.createChatAttachmentOrThrow(ctx, filePath);
            if (com.sikoclaw.app.floating.SharedChatBus.INSTANCE.offerAttachment(attachment)) {
                return ToolResult.success(ctx.getString(R.string.tool_file_sent, attachment.getName()));
            }
            return ToolResult.error("File was prepared but the active chat did not attach it.");
        } catch (Exception error) {
            return ToolResult.error("Could not prepare file attachment: " + error.getMessage());
        }

        /* External-channel fallback intentionally remains below for legacy channel
           tasks; it is only reached by code paths which do not have an active chat. */
        /*

        if (!hasStoragePermission()) {
            return ToolResult.error(ctx.getString(R.string.tool_no_storage_permission));
        }

        // Get current task channel and message ID
        Channel channel = ClawApplicationKt.getAppViewModel().getInProgressTaskChannel();
        String messageId = ClawApplicationKt.getAppViewModel().getInProgressTaskMessageId();

        if (channel == null || messageId.isEmpty()) {
            return ToolResult.error(ctx.getString(R.string.tool_no_task_channel));
        }

        try {
            ChannelManager.sendFile(channel, file, messageId);
            return ToolResult.success(ctx.getString(R.string.tool_file_sent, file.getName()));
        } catch (Exception e) {
            return ToolResult.error(ctx.getString(R.string.tool_file_send_failed, e.getMessage()));
        }
        */
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(
                    ClawApplication.Companion.getInstance(),
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
    }
}
