package com.sikoclaw.app.tool.document

import androidx.core.content.FileProvider
import com.sikoclaw.app.ClawApplication
import com.sikoclaw.app.tool.BaseTool
import com.sikoclaw.app.tool.ToolParameter
import com.sikoclaw.app.tool.ToolResult
import com.sikoclaw.app.ui.chat.ChatAttachment
import com.sikoclaw.app.ui.chat.ChatAttachmentManager
import java.io.File

class CreateArtifactTool : BaseTool() {
    override fun getName() = "create_artifact"
    override fun getDisplayName() = "Create interactive artifact"
    override fun getDescriptionEN() = "Create an HTML/CSS/JavaScript interactive artifact that the user can preview in a sandboxed viewer, save, and share. Never embed secrets."
    override fun getDescriptionCN() = getDescriptionEN()
    override fun getParameters() = listOf(
        ToolParameter("title", "string", "Artifact title", true),
        ToolParameter("html", "string", "Complete self-contained HTML document", true),
    )
    override fun execute(params: Map<String, Any>): ToolResult = runCatching {
        val title = requireString(params, "title").replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").take(80).ifBlank { "artifact" }
        val html = requireString(params, "html")
        require(html.length <= 2_000_000) { "Artifact exceeds the 2 MB source limit" }
        val dir = File(ClawApplication.instance.filesDir, "generated_files").apply { mkdirs() }
        val file = File(dir, "${System.currentTimeMillis()}_$title.html").apply { writeText(html) }
        val uri = FileProvider.getUriForFile(ClawApplication.instance, "${ClawApplication.instance.packageName}.fileprovider", file)
        val attachment = ChatAttachment(uri = uri.toString(), name = file.name, mimeType = "text/html", sizeBytes = file.length())
        ToolResult.success("Artifact created. Include this exact marker in your reply: ${ChatAttachmentManager.encodeMarker(attachment)}")
    }.getOrElse { ToolResult.error("Artifact creation failed: ${it.message}") }
}
