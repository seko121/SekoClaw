/* Adapted from Kai SmsReader.android.kt and SmsTools.kt. Apache-2.0. */
package com.sikoclaw.app.tool.impl

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.sikoclaw.app.ClawApplication
import com.sikoclaw.app.agent.services.AgentDeviceServices
import com.sikoclaw.app.tool.BaseTool
import com.sikoclaw.app.tool.ToolParameter
import com.sikoclaw.app.tool.ToolResult

class ReadSmsTool : BaseTool() {
    override fun getName() = "read_sms"
    override fun getDisplayName() = "Read SMS"
    override fun getDescriptionEN() = "Search received SMS after the user enables access in Agent settings."
    override fun getDescriptionCN() = getDescriptionEN()
    override fun getParameters() = listOf(ToolParameter("query", "string", "Optional sender or text", false), ToolParameter("limit", "integer", "Maximum 20", false))
    override fun execute(params: Map<String, Any>): ToolResult {
        val context = ClawApplication.instance
        if (!AgentDeviceServices.readSmsEnabled()) return ToolResult.error("Read SMS is disabled in Agent settings")
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return ToolResult.error("Read SMS permission is required")
        val query = params["query"]?.toString()?.trim().orEmpty(); val limit = (params["limit"] as? Number)?.toInt()?.coerceIn(1, 20) ?: 10; val needle = "%$query%"
        val selection = if (query.isBlank()) "${Telephony.Sms.TYPE} = ?" else "${Telephony.Sms.TYPE} = ? AND (${Telephony.Sms.ADDRESS} LIKE ? OR ${Telephony.Sms.BODY} LIKE ?)"
        val args = if (query.isBlank()) arrayOf(Telephony.Sms.MESSAGE_TYPE_INBOX.toString()) else arrayOf(Telephony.Sms.MESSAGE_TYPE_INBOX.toString(), needle, needle)
        val messages = mutableListOf<Map<String, Any>>()
        context.contentResolver.query(Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.READ), selection, args, "${Telephony.Sms.DATE} DESC LIMIT $limit")?.use { cursor ->
            while (cursor.moveToNext()) messages += mapOf("id" to cursor.getLong(0), "from" to cursor.getString(1).orEmpty(), "body" to cursor.getString(2).orEmpty(), "date" to cursor.getLong(3), "read" to (cursor.getInt(4) != 0))
        }
        return ToolResult.success(Gson().toJson(mapOf("count" to messages.size, "messages" to messages)))
    }
}

class DraftSmsTool : BaseTool() {
    override fun getName() = "draft_sms"
    override fun getDisplayName() = "Draft SMS"
    override fun getDescriptionEN() = "Open an SMS draft for explicit user review; never sends automatically."
    override fun getDescriptionCN() = getDescriptionEN()
    override fun getParameters() = listOf(ToolParameter("to", "string", "Recipient", true), ToolParameter("body", "string", "Message", true))
    override fun execute(params: Map<String, Any>): ToolResult {
        if (!AgentDeviceServices.sendSmsEnabled()) return ToolResult.error("SMS drafts are disabled in Agent settings")
        val to = requireString(params, "to").trim(); val body = requireString(params, "body")
        if (to.isBlank() || body.isBlank()) return ToolResult.error("Recipient and message are required")
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(to)}")).apply { putExtra("sms_body", body); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        return runCatching { ClawApplication.instance.startActivity(intent); ToolResult.success("SMS draft opened for user review. It has not been sent.") }.getOrElse { ToolResult.error("No SMS application is available") }
    }
}
