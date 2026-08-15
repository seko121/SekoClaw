package com.sikoclaw.app.ui.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

enum class AttachmentState { UPLOADING, PROCESSING, READY, FAILED }

data class ChatAttachment(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val state: AttachmentState = AttachmentState.READY,
    val extractedText: String = "",
    val error: String? = null,
)

data class AttachmentPayload(val visibleText: String, val attachments: List<ChatAttachment>)

object ChatAttachmentManager {
    private const val MAX_BYTES = 50L * 1024L * 1024L
    private const val MAX_EXTRACTED_CHARS = 80_000
    private val gson = Gson()
    private val marker = Regex("\\[\\[SIKO_ATTACHMENT:([A-Za-z0-9_=-]+)]]")

    fun import(context: Context, source: Uri): ChatAttachment {
        val metadata = metadata(context, source)
        require(metadata.second <= MAX_BYTES || metadata.second < 0) { "${metadata.first} exceeds the 50 MB attachment limit" }
        val directory = File(context.filesDir, "chat_attachments").apply { mkdirs() }
        cleanup(directory)
        val safeName = metadata.first.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").take(120).ifBlank { "attachment" }
        val target = File(directory, "${UUID.randomUUID()}_$safeName")
        var copied = 0L
        context.contentResolver.openInputStream(source)?.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    copied += count
                    if (copied > MAX_BYTES) error("$safeName exceeds the 50 MB attachment limit")
                    output.write(buffer, 0, count)
                }
            }
        } ?: error("The selected file could not be opened")
        val mime = context.contentResolver.getType(source).orEmpty().ifBlank { mimeFromName(safeName) }
        val exposed = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
        return runCatching {
            ChatAttachment(uri = exposed.toString(), name = safeName, mimeType = mime, sizeBytes = copied,
                extractedText = extract(context, target, mime, safeName).take(MAX_EXTRACTED_CHARS))
        }.getOrElse { error ->
            ChatAttachment(uri = exposed.toString(), name = safeName, mimeType = mime, sizeBytes = copied,
                state = AttachmentState.FAILED, error = error.message ?: "Attachment processing failed")
        }
    }

    fun fromFile(context: Context, file: File): ChatAttachment {
        require(file.isFile && file.canRead()) { "File is not readable" }
        require(file.length() <= MAX_BYTES) { "${file.name} exceeds the 50 MB attachment limit" }
        val mime = mimeFromName(file.name)
        val exposed = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return ChatAttachment(
            uri = exposed.toString(),
            name = file.name,
            mimeType = mime,
            sizeBytes = file.length(),
            extractedText = runCatching { extract(context, file, mime, file.name).take(MAX_EXTRACTED_CHARS) }.getOrDefault(""),
        )
    }

    /** Verifies that an attachment can actually be opened before it is published to chat. */
    fun validate(context: Context, attachment: ChatAttachment): Result<Unit> = runCatching {
        require(attachment.state == AttachmentState.READY) { attachment.error ?: "Attachment is not ready" }
        require(attachment.name.isNotBlank()) { "Attachment name is missing" }
        require(attachment.mimeType.isNotBlank()) { "Attachment MIME type is missing" }
        require(attachment.sizeBytes in 0..MAX_BYTES) { "Attachment size is invalid" }
        val uri = Uri.parse(attachment.uri)
        require(uri.scheme == "content" || uri.scheme == "file") { "Unsupported attachment URI" }
        context.contentResolver.openInputStream(uri)?.use { input ->
            if (attachment.sizeBytes > 0) require(input.read() >= 0) { "Attachment is empty" }
        } ?: error("Attachment cannot be opened")
    }
    fun encodePrompt(text: String, attachments: List<ChatAttachment>): String = buildString {
        append(text)
        attachments.forEach { append('\n').append(encodeMarker(it)) }
        if (attachments.isNotEmpty()) {
            append("\n\nATTACHMENT CONTENT (actual processed content; do not infer from filename):\n")
            attachments.forEach { attachment ->
                append("\n--- ${attachment.name} (${attachment.mimeType}, ${attachment.sizeBytes} bytes) ---\n")
                when {
                    attachment.state == AttachmentState.FAILED -> append("Processing failed: ${attachment.error}\n")
                    attachment.extractedText.isNotBlank() -> append(attachment.extractedText).append('\n')
                    attachment.mimeType.startsWith("image/") -> append("Image reference: ${attachment.uri}. Use vision input when supported.\n")
                    else -> append("File reference: ${attachment.uri}. Use an enabled file/document tool to inspect it.\n")
                }
            }
        }
    }

    fun decode(raw: String): AttachmentPayload {
        val attachments = marker.findAll(raw).mapNotNull { match ->
            runCatching {
                val json = String(Base64.decode(match.groupValues[1], Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
                gson.fromJson(json, ChatAttachment::class.java)
            }.getOrNull()
        }.toList()
        val visible = marker.replace(raw, "").substringBefore("\n\nATTACHMENT CONTENT (actual processed content;").trim()
        return AttachmentPayload(visible, attachments)
    }

    fun encodeMarker(attachment: ChatAttachment): String {
        val payload = Base64.encodeToString(gson.toJson(attachment).toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
        return "[[SIKO_ATTACHMENT:$payload]]"
    }

    private fun metadata(context: Context, uri: Uri): Pair<String, Long> {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return (cursor.getString(0) ?: "attachment") to if (cursor.isNull(1)) -1L else cursor.getLong(1)
        }
        return (uri.lastPathSegment ?: "attachment") to -1L
    }

    private fun extract(context: Context, file: File, mime: String, name: String): String = when {
        mime.startsWith("text/") || name.endsWith(".json", true) || name.endsWith(".md", true) || name.endsWith(".html", true) -> file.readText()
        mime == "application/pdf" || name.endsWith(".pdf", true) -> {
            PDFBoxResourceLoader.init(context.applicationContext)
            PDDocument.load(file).use { PDFTextStripper().getText(it) }
        }
        listOf(".docx", ".xlsx", ".pptx").any { name.endsWith(it, true) } -> extractOfficeXml(file)
        else -> ""
    }

    private fun extractOfficeXml(file: File): String {
        val out = StringBuilder()
        ZipInputStream(file.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val relevant = entry.name.endsWith(".xml") && (entry.name.startsWith("word/") || entry.name.startsWith("xl/sharedStrings") || entry.name.startsWith("xl/worksheets") || entry.name.startsWith("ppt/slides"))
                if (relevant) {
                    val xml = zip.readBytes().toString(Charsets.UTF_8)
                    out.append(xml.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ")).append('\n')
                    if (out.length > MAX_EXTRACTED_CHARS) break
                }
                zip.closeEntry()
            }
        }
        return out.toString().trim()
    }

    internal fun mimeFromName(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"; "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "gif" -> "image/gif"; "webp" -> "image/webp"
        "mp3" -> "audio/mpeg"; "m4a" -> "audio/mp4"; "wav" -> "audio/wav"; "ogg", "opus" -> "audio/ogg"
        "mp4" -> "video/mp4"; "webm" -> "video/webm"; "mov" -> "video/quicktime"; "mkv" -> "video/x-matroska"
        "zip" -> "application/zip"; "html", "htm" -> "text/html"; "txt", "md" -> "text/plain"
        "csv" -> "text/csv"; "json" -> "application/json"; else -> "application/octet-stream"
    }

    private fun cleanup(directory: File) {
        val cutoff = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L
        directory.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { runCatching { it.delete() } }
    }
}

