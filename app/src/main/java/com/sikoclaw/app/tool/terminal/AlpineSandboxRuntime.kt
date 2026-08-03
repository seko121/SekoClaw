package com.sikoclaw.app.tool.terminal

import android.os.Build
import com.sikoclaw.app.ClawApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

enum class SandboxStage { NOT_INSTALLED, DOWNLOADING, EXTRACTING, CONFIGURING, READY, FAILED }
data class SandboxStatus(val stage: SandboxStage, val message: String? = null)
data class SandboxResult(val stdout: String, val stderr: String, val exitCode: Int, val timedOut: Boolean = false)

class AlpineProcessHandle internal constructor(private val process: Process, private val cancelled: AtomicBoolean) {
    fun writeInput(line: String) { if (!cancelled.get()) runCatching { process.outputStream.write((line + "\n").toByteArray()); process.outputStream.flush() } }
    fun cancel() { cancelled.set(true); runCatching { process.outputStream.close() }; process.destroyForcibly() }
    fun awaitExit(): Int { while (!cancelled.get() && process.isAlive) runCatching { process.waitFor(200, TimeUnit.MILLISECONDS) }; return if (cancelled.get()) -1 else runCatching { process.exitValue() }.getOrDefault(-1) }
}

/** Alpine 3.22 sandbox patterned after Kai's Apache-2.0 implementation. */
object AlpineSandboxRuntime {
    const val VERSION = "3.22.5"
    const val ROOTFS_SHA256 = "3fbc6285032ed46821b511292633d7b2a6306a2e254f590e92bdafff56cf2f70"
    private val downloadUrls = listOf(
        "https://dl-cdn.alpinelinux.org/alpine/v3.22/releases/aarch64/alpine-minirootfs-3.22.5-aarch64.tar.gz",
        "https://mirrors.edge.kernel.org/alpine/v3.22/releases/aarch64/alpine-minirootfs-3.22.5-aarch64.tar.gz",
        "https://ftp.halifax.rwth-aachen.de/alpine/v3.22/releases/aarch64/alpine-minirootfs-3.22.5-aarch64.tar.gz",
        "https://alpine.ethz.ch/alpine/v3.22/releases/aarch64/alpine-minirootfs-3.22.5-aarch64.tar.gz",
    )
    private val app get() = ClawApplication.instance
    private val base get() = File(app.filesDir, "linux-sandbox")
    val rootfs get() = File(base, "rootfs")
    val home get() = (app.getExternalFilesDir(null)?.let { File(it, "sandbox-home") } ?: File(base, "home")).apply { mkdirs() }
    private val tmp get() = File(base, "tmp").apply { mkdirs() }
    private val marker get() = File(base, "install-$VERSION.ok")
    private val nativeDir get() = File(app.applicationInfo.nativeLibraryDir)
    private val proot get() = File(nativeDir, "libproot.so")
    private val loader get() = File(nativeDir, "libproot_loader.so")
    private val tallocDir get() = File(base, "runtime").apply { mkdirs() }
    private val _status = MutableStateFlow(if (healthyFiles()) SandboxStatus(SandboxStage.READY) else SandboxStatus(SandboxStage.NOT_INSTALLED))
    val status: StateFlow<SandboxStatus> = _status

    fun isReady() = _status.value.stage == SandboxStage.READY && healthyFiles()

    @Synchronized fun install(): Result<Unit> = runCatching {
        require(Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }) { "Linux Sandbox currently requires arm64-v8a" }
        require(proot.exists() && proot.canExecute()) { "Bundled PRoot is unavailable: ${proot.absolutePath}" }
        base.mkdirs(); tmp.mkdirs()
        File(nativeDir, "libtalloc.so.2").takeIf(File::exists)?.copyTo(File(tallocDir, "libtalloc.so.2"), true)
        val archive = File(base, "rootfs.tar.gz")
        _status.value = SandboxStatus(SandboxStage.DOWNLOADING, downloadUrls.first())
        var lastDownloadError: Throwable? = null
        for (url in downloadUrls) {
            val downloaded = runCatching {
                val part = File(base, "rootfs.tar.gz.part")
                val connection = java.net.URL(url).openConnection().apply { connectTimeout = 20_000; readTimeout = 60_000 }
                val total = connection.contentLengthLong
                connection.getInputStream().buffered().use { input ->
                    part.outputStream().buffered().use { output ->
                        val buffer = ByteArray(8192); var downloaded = 0L
                        while (true) {
                            val count = input.read(buffer); if (count < 0) break
                            output.write(buffer, 0, count); downloaded += count
                            if (total > 0) _status.value = SandboxStatus(SandboxStage.DOWNLOADING, "${downloaded * 100 / total}%")
                        }
                    }
                }
                if (archive.exists()) archive.delete()
                require(part.renameTo(archive)) { "Could not finalize Alpine download" }
            }
            if (downloaded.isSuccess) { lastDownloadError = null; break }
            lastDownloadError = downloaded.exceptionOrNull(); File(base, "rootfs.tar.gz.part").delete()
        }
        require(lastDownloadError == null && archive.exists()) { "All Alpine mirrors failed: ${lastDownloadError?.message}" }
        require(sha256(archive) == ROOTFS_SHA256) { "Downloaded Alpine checksum mismatch" }
        rootfs.deleteRecursively(); rootfs.mkdirs(); _status.value = SandboxStatus(SandboxStage.EXTRACTING)
        UbuntuRuntime.extractSafely(archive, rootfs); archive.delete()
        require(File(rootfs, "bin/sh").exists() && File(rootfs, "sbin/apk").exists()) { "Alpine extraction is incomplete" }
        _status.value = SandboxStatus(SandboxStage.CONFIGURING)
        File(rootfs, "etc/resolv.conf").writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
        File(rootfs, "etc/apk/repositories").writeText("https://dl-cdn.alpinelinux.org/alpine/v3.22/main\nhttps://dl-cdn.alpinelinux.org/alpine/v3.22/community\n")
        val check = execute("cat /etc/alpine-release && command -v apk && apk --version", 60)
        require(check.exitCode == 0 && check.stdout.contains(VERSION.substringBeforeLast('.')) && check.stdout.contains("apk")) { check.stderr.ifBlank { "Alpine health check failed" } }
        val update = execute("apk update && apk add --no-cache bash", 180)
        require(update.exitCode == 0) { update.stderr.ifBlank { "apk update failed" } }
        marker.writeText(ROOTFS_SHA256); _status.value = SandboxStatus(SandboxStage.READY)
    }.onFailure { _status.value = SandboxStatus(SandboxStage.FAILED, it.message) }

    fun execute(command: String, timeoutSeconds: Long = 60, workingDir: String = "/root"): SandboxResult {
        require(healthyFiles()) { "Linux Sandbox is not installed" }
        val process = ProcessBuilder(args(command, workingDir)).directory(base).apply {
            environment()["HOME"] = "/root"; environment()["TERM"] = "xterm-256color"
            environment()["PROOT_LOADER"] = loader.absolutePath; environment()["PROOT_TMP_DIR"] = tmp.absolutePath
            environment()["LD_LIBRARY_PATH"] = tallocDir.absolutePath
        }.start()
        val out = CompletableFuture.supplyAsync { process.inputStream.bufferedReader().readText() }
        val err = CompletableFuture.supplyAsync { process.errorStream.bufferedReader().readText() }
        val done = process.waitFor(timeoutSeconds.coerceIn(1, 180), TimeUnit.SECONDS)
        if (!done) process.destroyForcibly()
        return SandboxResult(out.get(2, TimeUnit.SECONDS).takeLast(15_000), err.get(2, TimeUnit.SECONDS).takeLast(15_000), if (done) process.exitValue() else -1, !done)
    }

    fun executeStreaming(command: String, onStdout: (String) -> Unit, onStderr: (String) -> Unit): AlpineProcessHandle {
        require(healthyFiles()) { "Linux Sandbox is not installed" }
        val process = ProcessBuilder(args(command)).directory(base).apply {
            environment()["HOME"] = "/root"; environment()["TERM"] = "xterm-256color"
            environment()["PROOT_LOADER"] = loader.absolutePath; environment()["PROOT_TMP_DIR"] = tmp.absolutePath
            environment()["LD_LIBRARY_PATH"] = tallocDir.absolutePath
        }.start()
        val cancelled = AtomicBoolean(false)
        CompletableFuture.runAsync { process.inputStream.bufferedReader().useLines { lines -> lines.forEach { if (!cancelled.get()) onStdout(it) } } }
        CompletableFuture.runAsync { process.errorStream.bufferedReader().useLines { lines -> lines.forEach { if (!cancelled.get()) onStderr(it) } } }
        return AlpineProcessHandle(process, cancelled)
    }

    fun updatePackages(): SandboxResult = execute("apk update && apk upgrade", 180)

    fun installEssentialPackages(): SandboxResult = execute(
        "apk add --no-cache curl wget git jq python3 py3-pip nodejs npm openssh-client nano less zip unzip tar gzip xz",
        180,
    )

    fun searchPackages(query: String): SandboxResult {
        require(query.matches(Regex("[A-Za-z0-9._+\\-]{1,80}"))) { "Invalid package search" }
        return execute("apk search -v '$query*' | head -100", 60)
    }

    fun installPackage(name: String): SandboxResult {
        require(name.matches(Regex("[A-Za-z0-9._+\\-]{1,80}"))) { "Invalid package name" }
        return execute("apk add --no-cache '$name'", 180)
    }

    fun uninstallPackage(name: String): SandboxResult {
        require(name != "bash") { "bash is required by the sandbox" }
        require(name.matches(Regex("[A-Za-z0-9._+\\-]{1,80}"))) { "Invalid package name" }
        return execute("apk del '$name'", 180)
    }

    fun args(command: String, workingDir: String = "/root") = listOf(
        proot.absolutePath, "--kill-on-exit", "--rootfs=${rootfs.absolutePath}", "--bind=/dev", "--bind=/proc", "--bind=/sys",
        "--bind=${home.absolutePath}:/root", "--bind=${tmp.absolutePath}:/tmp", "-0", "-w", workingDir,
        "/usr/bin/env", "-i", "HOME=/root", "USER=root", "SHELL=/bin/bash", "TERM=xterm-256color",
        "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin", "/bin/sh", "-lc", command,
    )

    private fun healthyFiles() = marker.exists() && marker.readText().trim() == ROOTFS_SHA256 && File(rootfs, "bin/sh").exists() && File(rootfs, "sbin/apk").exists() && proot.exists()
    private fun sha256(file: File): String { val d = MessageDigest.getInstance("SHA-256"); file.inputStream().use { i -> val b=ByteArray(65536); while(true){ val n=i.read(b); if(n<0) break; d.update(b,0,n) } }; return d.digest().joinToString(""){"%02x".format(it)} }
}
