package com.sikoclaw.app.tool.terminal

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Persistent bash sessions adapted from Kai's Apache-2.0 Linux sandbox design. */
class AlpinePersistentShell {
    private var handle: AlpineProcessHandle? = null
    private var pending: Pending? = null
    private data class Pending(val id: String, val out: StringBuilder = StringBuilder(), val err: StringBuilder = StringBuilder(), val done: CompletableDeferred<SandboxResult> = CompletableDeferred())

    suspend fun run(command: String, timeoutSeconds: Long = 180): SandboxResult = withContext(Dispatchers.IO) {
        ensureStarted()
        val id = UUID.randomUUID().toString().replace("-", "")
        val p = Pending(id); pending = p
        val staged = File(AlpineSandboxRuntime.rootfs.parentFile, "tmp/.siko_$id").apply { parentFile?.mkdirs(); writeText(command) }
        handle?.writeInput(". /tmp/.siko_$id; s=\$?; rm -f /tmp/.siko_$id; printf '\\036SIKO:$id:%d:%s\\036\\n' \"\$s\" \"\$PWD\" >&2")
        val result = withTimeoutOrNull(timeoutSeconds * 1000) { p.done.await() }
        pending = null
        if (result != null) result else { cancel(); SandboxResult(p.out.toString(), p.err.toString() + "\nCommand timed out", -1, true) }
    }

    fun writeInput(line: String) = handle?.writeInput(line)
    fun cancel() { handle?.cancel(); handle = null; pending?.done?.complete(SandboxResult(pending?.out.toString(), "Command cancelled", -1)); pending = null }

    private fun ensureStarted() {
        if (handle != null) return
        handle = AlpineSandboxRuntime.executeStreaming("exec bash --noprofile --norc", ::stdout, ::stderr)
    }
    private fun stdout(line: String) { pending?.out?.appendLine(line) }
    private fun stderr(line: String) {
        val p = pending ?: return
        val match = Regex("\\u001eSIKO:${Regex.escape(p.id)}:(-?\\d+):(.*)\\u001e").matchEntire(line)
        if (match != null) p.done.complete(SandboxResult(p.out.toString().trimEnd(), p.err.toString().trimEnd(), match.groupValues[1].toInt()))
        else if (line.isNotEmpty()) p.err.appendLine(line)
    }
}

object AlpineShellSessions {
    private val sessions = ConcurrentHashMap<String, AlpinePersistentShell>()
    fun forId(id: String) = sessions.getOrPut(id) { AlpinePersistentShell() }
    fun close(id: String) { sessions.remove(id)?.cancel() }
}
