package com.sikoclaw.app.files

import android.content.Context
import com.sikoclaw.app.tool.terminal.AlpineSandboxRuntime
import com.sikoclaw.app.ui.chat.ChatAttachment
import com.sikoclaw.app.ui.chat.ChatAttachmentManager
import com.sikoclaw.app.utils.XLog
import java.io.File
import java.util.UUID

/**
 * Single, auditable bridge from an agent path to a chat attachment.
 * Agent output is never exposed directly from a Linux rootfs or shared storage:
 * it is copied into the app's attachment cache first, then served through FileProvider.
 */
object AgentFileManager {
    private const val TAG = "AgentFileManager"
    private const val MAX_BYTES = 50L * 1024L * 1024L

    fun createChatAttachment(context: Context, requestedPath: String): Result<ChatAttachment> = runCatching {
        val source = resolve(context, requestedPath)
        require(source.isFile && source.canRead()) { "File is missing or unreadable: $requestedPath" }
        require(source.length() in 1..MAX_BYTES) { "File size must be between 1 byte and 50 MB" }

        val cache = File(context.cacheDir, "agent_attachments").apply { mkdirs() }
        val safeName = source.name.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").take(120).ifBlank { "attachment" }
        val exported = File(cache, "${UUID.randomUUID()}_$safeName")
        source.inputStream().buffered().use { input -> exported.outputStream().buffered().use { input.copyTo(it) } }
        require(exported.isFile && exported.length() == source.length()) { "Attachment export verification failed" }

        val attachment = ChatAttachmentManager.fromFile(context, exported)
        ChatAttachmentManager.validate(context, attachment).getOrThrow()
        XLog.i(TAG, "attachment source=${source.absolutePath} -> export=${exported.absolutePath} -> uri=${attachment.uri} -> ${attachment.name}")
        attachment
    }.onFailure { XLog.w(TAG, "attachment export failed for '$requestedPath': ${it.message}") }

    /** Java tool bridge. Kotlin Result is intentionally not exposed to Java callers. */
    @JvmStatic
    @Throws(IllegalArgumentException::class, java.io.IOException::class)
    fun createChatAttachmentOrThrow(context: Context, requestedPath: String): ChatAttachment =
        createChatAttachment(context, requestedPath).getOrElse { throw java.io.IOException(it.message, it) }

    private fun resolve(context: Context, requestedPath: String): File {
        val raw = File(requestedPath)
        if (raw.isAbsolute && raw.exists()) return raw
        val normalized = requestedPath.replace('\\', '/').removePrefix("/")
        val candidates = listOf(
            File(context.filesDir, normalized),
            File(context.cacheDir, normalized),
            File(AlpineSandboxRuntime.rootfs, normalized.removePrefix("root/")),
            File(AlpineSandboxRuntime.rootfs, "root/$normalized"),
        )
        return candidates.firstOrNull { it.exists() } ?: raw
    }
}
