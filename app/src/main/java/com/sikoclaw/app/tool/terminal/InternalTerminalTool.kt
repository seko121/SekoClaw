package com.sikoclaw.app.tool.terminal

import com.sikoclaw.app.tool.BaseTool
import com.sikoclaw.app.tool.ToolParameter
import com.sikoclaw.app.tool.ToolResult
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import java.io.File
import kotlinx.coroutines.runBlocking

object InternalTerminal {
    private val lock = ReentrantLock()
    private val activeProcess = AtomicReference<Process?>(null)
    private val workingDirectory = AtomicReference<File?>(null)
    private val linuxWorkingDirectory = AtomicReference("/root")
    private val agentLinuxShell by lazy { AlpineShellSessions.forId("agent-default") }

    fun run(command: String, timeout: Int): ToolResult {
        if (command.isBlank()) return ToolResult.error("Command is empty")
        if (!lock.tryLock()) return ToolResult.error("Another agent command is already running")
        return try {
            if (AlpineSandboxRuntime.isReady()) {
                val result = runBlocking { agentLinuxShell.run(command, timeout.toLong()) }
                val out = result.stdout.trimEnd()
                val formatted = listOf(out, result.stderr.trimEnd()).filter(String::isNotBlank).joinToString("\n") +
                    if (result.exitCode != 0) "\nexit code: ${result.exitCode}" else ""
                return if (result.exitCode == 0) ToolResult.success(formatted.ifBlank { "Done" }) else ToolResult.error(formatted.trim())
            }
            AndroidTerminalRuntime.prepare()
            val cwd = workingDirectory.get()?.takeIf { it.isDirectory } ?: AndroidTerminalRuntime.homeDir
            val wrapped = "$command\ncode=\$?\nprintf '\\n__SIKO_CWD__%s\\n' \"\$PWD\"\nexit \$code"
            val builder = ProcessBuilder(AndroidTerminalRuntime.shell.absolutePath, "-c", wrapped).directory(cwd)
            AndroidTerminalRuntime.environment().forEach { value ->
                val split = value.indexOf('=')
                builder.environment()[value.substring(0, split)] = value.substring(split + 1)
            }
            val process = builder.redirectErrorStream(false).start()
            activeProcess.set(process)
            val readers = Executors.newFixedThreadPool(2)
            try {
                val stdout = readers.submit<String> { process.inputStream.bufferedReader().use { it.readText() } }
                val stderr = readers.submit<String> { process.errorStream.bufferedReader().use { it.readText() } }
                if (!process.waitFor(timeout.coerceIn(1, 600).toLong(), TimeUnit.SECONDS)) {
                    process.destroy(); if (process.isAlive) process.destroyForcibly()
                    return ToolResult.error("Command timed out")
                }
                val rawOut = stdout.get(5, TimeUnit.SECONDS).trimEnd()
                val cwdMarker = Regex("(?:^|\\n)__SIKO_CWD__(.+)$").find(rawOut)
                cwdMarker?.groupValues?.getOrNull(1)?.let { path -> File(path).takeIf { it.isDirectory }?.let(workingDirectory::set) }
                val out = rawOut.replace(Regex("(?:^|\\n)__SIKO_CWD__.+$"), "").trimEnd()
                val err = stderr.get(5, TimeUnit.SECONDS).trimEnd()
                val formatted = buildString {
                    if (out.isNotBlank()) append(out)
                    if (err.isNotBlank()) { if (isNotEmpty()) append('\n'); append(err) }
                    if (process.exitValue() != 0) { if (isNotEmpty()) append('\n'); append("exit code: ${process.exitValue()}") }
                }.ifBlank { "Done" }
                if (process.exitValue() == 0) ToolResult.success(formatted) else ToolResult.error(formatted)
            } finally { readers.shutdownNow(); activeProcess.compareAndSet(process, null) }
        } catch (error: Exception) {
            ToolResult.error("Terminal error: ${error.message}")
        } finally { lock.unlock() }
    }

    @Deprecated("The active terminal runtime is selected automatically")
    fun run(command: String, environment: TerminalEnvironment, timeout: Int): ToolResult = run(command, timeout)

    fun stop(): Boolean {
        if (AlpineSandboxRuntime.isReady()) { agentLinuxShell.cancel(); return true }
        return activeProcess.get()?.let { it.destroy(); if (it.isAlive) it.destroyForcibly(); true } ?: false
    }
}

class InternalTerminalTool : BaseTool() {
    override fun getName() = "internal_terminal"
    override fun getDisplayName() = "Terminal"
    override fun getDescriptionEN() = "Run a command in OctoBot's private workspace. Uses Alpine Linux when the sandbox is installed, otherwise the app-private Android shell. Sensitive operations require user approval."
    override fun getDescriptionCN() = getDescriptionEN()
    override fun getParameters() = listOf(
        ToolParameter("command", "string", "Command to run in the active terminal environment", true),
        ToolParameter("timeout_seconds", "integer", "1-600", false),
    )
    override fun execute(params: Map<String, Any>) = InternalTerminal.run(requireString(params, "command"), optionalInt(params, "timeout_seconds", 120))
}
