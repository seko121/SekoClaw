package com.sikoclaw.app.linux.kai

import com.sikoclaw.app.linux.smartTruncate
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val MAX_OUTPUT_LENGTH = 15_000
private const val DEFAULT_TIMEOUT_SECONDS = 30L
private const val MAX_TIMEOUT_SECONDS = 180L

class ProotHandle internal constructor(
    private val process: Process,
    private val cancelled: AtomicBoolean,
    private val readerFutures: List<CompletableFuture<Void>>,
) {
    fun isCancelled(): Boolean = cancelled.get()

    fun cancel() {
        cancelled.set(true)
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
        runCatching { process.outputStream.close() }
        process.destroyForcibly()
    }

    fun writeInput(line: String) {
        if (cancelled.get()) return
        runCatching {
            val bytes = (line + "\n").toByteArray()
            process.outputStream.write(bytes)
            process.outputStream.flush()
        }
    }

    fun awaitExit(): Int {
        // Poll so a cancel() from another thread can short-circuit the wait.
        // On Linux, close(fd) does NOT unblock a thread already inside read(fd),
        // so reader futures can sit waiting on a tracee pipe even after SIGKILL.
        while (!cancelled.get() && process.isAlive) {
            runCatching { process.waitFor(200, TimeUnit.MILLISECONDS) }
        }
        if (cancelled.get()) return -1
        readerFutures.forEach { runCatching { it.get(500, TimeUnit.MILLISECONDS) } }
        return runCatching { process.exitValue() }.getOrDefault(-1)
    }
}

class ProotExecutor(
    private val prootPath: String,
    private val libDir: String,
    private val rootfsPath: String,
    private val homePath: String,
    private val tmpPath: String,
    private val sharedBinds: List<Pair<String, String>> = emptyList(),
) {

    fun execute(
        command: String,
        timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
        workingDir: String = "/root",
        extraEnv: Map<String, String> = emptyMap(),
    ): Map<String, Any> {
        val effectiveTimeout = timeoutSeconds.coerceIn(1, MAX_TIMEOUT_SECONDS)

        val mode = LinuxCompatibilityMode.current()
        if (mode == LinuxCompatibilityMode.DISABLED) return mapOf("success" to false, "error" to "Linux runtime is disabled in compatibility settings")
        val firstNoSeccomp = mode == LinuxCompatibilityMode.NO_SECCOMP ||
            (mode == LinuxCompatibilityMode.AUTOMATIC && LinuxCompatibilityMode.emulatorLikely())
        val first = executeOnce(command, effectiveTimeout, workingDir, extraEnv, firstNoSeccomp)
        val diagnostic = listOf(first["stderr"], first["error"]).joinToString(" ").lowercase()
        return if (mode == LinuxCompatibilityMode.AUTOMATIC && !firstNoSeccomp &&
            (diagnostic.contains("ptrace") || diagnostic.contains("seccomp") || diagnostic.contains("operation not permitted"))) {
            com.sikoclaw.app.utils.XLog.w("ProotExecutor", "PRoot restricted by kernel; retrying once with PROOT_NO_SECCOMP=1")
            executeOnce(command, effectiveTimeout, workingDir, extraEnv, true) +
                ("compatibility_retry" to true) + ("compatibility_message" to "This device restricts ptrace/seccomp. Compatibility mode was used.")
        } else first
    }

    private fun executeOnce(
        command: String,
        effectiveTimeout: Long,
        workingDir: String,
        extraEnv: Map<String, String>,
        noSeccomp: Boolean,
    ): Map<String, Any> {
        return try {
            val process = Runtime.getRuntime().exec(
                buildProcessArgs(command, workingDir),
                buildEnvVars(extraEnv, noSeccomp),
                File(rootfsPath).parentFile,
            )

            // Drain stdout/stderr concurrently to avoid pipe buffer deadlock
            val stdoutFuture = CompletableFuture.supplyAsync {
                readBounded(process.inputStream.bufferedReader())
            }
            val stderrFuture = CompletableFuture.supplyAsync {
                readBounded(process.errorStream.bufferedReader())
            }

            val completed = process.waitFor(effectiveTimeout, TimeUnit.SECONDS)

            if (!completed) {
                process.destroyForcibly()
                return mapOf(
                    "success" to false,
                    "stdout" to stdoutFuture.get(1, TimeUnit.SECONDS).smartTruncate(MAX_OUTPUT_LENGTH),
                    "stderr" to stderrFuture.get(1, TimeUnit.SECONDS).smartTruncate(MAX_OUTPUT_LENGTH),
                    "exit_code" to -1,
                    "timed_out" to true,
                )
            }

            mapOf(
                "success" to (process.exitValue() == 0),
                "stdout" to stdoutFuture.get().smartTruncate(MAX_OUTPUT_LENGTH),
                "stderr" to stderrFuture.get().smartTruncate(MAX_OUTPUT_LENGTH),
                "exit_code" to process.exitValue(),
                "timed_out" to false,
            )
        } catch (e: Exception) {
            mapOf(
                "success" to false,
                "error" to (e.message ?: "Failed to execute command in sandbox"),
            )
        }
    }

    fun executeStreaming(
        command: String,
        workingDir: String = "/root",
        extraEnv: Map<String, String> = emptyMap(),
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
    ): ProotHandle {
        val process = Runtime.getRuntime().exec(
            buildProcessArgs(command, workingDir),
            buildEnvVars(extraEnv, LinuxCompatibilityMode.current() == LinuxCompatibilityMode.NO_SECCOMP ||
                (LinuxCompatibilityMode.current() == LinuxCompatibilityMode.AUTOMATIC && LinuxCompatibilityMode.emulatorLikely())),
            File(rootfsPath).parentFile,
        )
        val cancelled = AtomicBoolean(false)
        val stdoutFuture = CompletableFuture.runAsync {
            streamLines(process.inputStream.bufferedReader(), cancelled, onStdout)
        }
        val stderrFuture = CompletableFuture.runAsync {
            streamLines(process.errorStream.bufferedReader(), cancelled, onStderr)
        }
        return ProotHandle(process, cancelled, listOf(stdoutFuture, stderrFuture))
    }

    private fun buildProcessArgs(command: String, workingDir: String): Array<String> = buildList {
        add(prootPath)
        add("--rootfs=$rootfsPath")
        add("--bind=/dev")
        add("--bind=/proc")
        add("--bind=/sys")
        add("--bind=$homePath:/root")
        add("--bind=$tmpPath:/tmp")
        sharedBinds.forEach { (host, guest) -> add("--bind=$host:$guest") }
        add("-0")
        add("-w"); add(workingDir)
        add("/bin/sh"); add("-c"); add(command)
    }.toTypedArray()

    private fun buildEnvVars(extraEnv: Map<String, String>, noSeccomp: Boolean): Array<String> {
        val loaderPath = File(prootPath).parent.orEmpty() + "/libproot-loader.so"
        val baseEnv = arrayOf(
            "HOME=/root",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            "LD_LIBRARY_PATH=$libDir",
            "PROOT_TMP_DIR=$tmpPath",
            "PROOT_LOADER=$loaderPath",
        )
        val compatibility = if (noSeccomp) arrayOf("PROOT_NO_SECCOMP=1") else emptyArray()
        return baseEnv + compatibility + extraEnv.map { (k, v) -> "$k=$v" }.toTypedArray()
    }

    private fun readBounded(reader: BufferedReader): String {
        val sb = StringBuilder()
        val buf = CharArray(8192)
        try {
            var read: Int
            while (reader.read(buf).also { read = it } != -1) {
                sb.append(buf, 0, read)
                if (sb.length >= MAX_OUTPUT_LENGTH) break
            }
            if (sb.length >= MAX_OUTPUT_LENGTH) {
                while (reader.read(buf) != -1) { /* discard */ }
            }
        } catch (_: IOException) {
            // Stream closed under us (typically destroyForcibly on timeout).
            // Return what we have so the timed_out path can surface a clean result.
        }
        return sb.toString()
    }

    private fun streamLines(
        reader: BufferedReader,
        cancelled: AtomicBoolean,
        onLine: (String) -> Unit,
    ) {
        try {
            while (!cancelled.get()) {
                val line = try {
                    reader.readLine()
                } catch (e: IOException) {
                    if (cancelled.get()) break
                    throw e
                } ?: break
                onLine(line)
            }
        } finally {
            runCatching { reader.close() }
        }
    }
}
