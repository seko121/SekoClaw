package com.sikoclaw.app.tool.terminal

import android.os.Build
import android.system.Os
import android.util.Log
import com.sikoclaw.app.BuildConfig
import com.sikoclaw.app.ClawApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

data class UbuntuManifest(
    val version: String,
    val architecture: String,
    val androidAbi: String,
    val officialUrl: String,
    val sha256: String,
    val downloadSize: Long,
    val minimumAppVersion: Int,
)

enum class UbuntuSetupStage { NOT_INSTALLED, WAITING_FOR_APPROVAL, DOWNLOADING, VERIFYING, EXTRACTING, CONFIGURING, HEALTH_CHECKING, READY, FAILED }

data class UbuntuSetupStatus(
    val stage: UbuntuSetupStage,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val bytesPerSecond: Long = 0,
    val message: String? = null,
)

data class UbuntuInstallState(val version: String, val architecture: String, val checksum: String, val installedAt: Long, val healthCheckPassed: Boolean, val rootfsPath: String)
data class UbuntuDiagnostics(
    val abi: String,
    val rootfsPath: String,
    val rootfsExists: Boolean,
    val shellExists: Boolean,
    val bashExists: Boolean,
    val aptExists: Boolean,
    val osReleaseExists: Boolean,
    val prootPath: String,
    val prootExists: Boolean,
    val prootExecutable: Boolean,
    val fullCommand: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int?,
    val exceptionType: String? = null,
    val exceptionMessage: String? = null,
) {
    fun display(): String = buildString {
        appendLine("ABI: $abi"); appendLine("RootFS: $rootfsPath (exists=$rootfsExists)")
        appendLine("/bin/sh=$shellExists, /bin/bash=$bashExists, /usr/bin/apt=$aptExists, /etc/os-release=$osReleaseExists")
        appendLine("PRoot: $prootPath (exists=$prootExists, executable=$prootExecutable)")
        if (fullCommand.isNotBlank()) appendLine("Command: $fullCommand")
        exitCode?.let { appendLine("Exit code: $it") }
        if (stdout.isNotBlank()) appendLine("stdout:\n$stdout")
        if (stderr.isNotBlank()) appendLine("stderr:\n$stderr")
        if (exceptionType != null) appendLine("Exception: $exceptionType: ${exceptionMessage.orEmpty()}")
    }.trim()
}

object UbuntuRuntime {
    private const val TAG = "SikoUbuntu"
    val manifest = UbuntuManifest(
        version = "24.04.4 LTS",
        architecture = "arm64",
        androidAbi = "arm64-v8a",
        officialUrl = "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.4-base-arm64.tar.gz",
        sha256 = "04207713ece899c3740823d33690441ad3a7f0ded1101aca744e2b0f37ac7ff2",
        downloadSize = 29_000_000L,
        minimumAppVersion = 29,
    )

    private val app get() = ClawApplication.instance
    val linuxDir get() = File(app.filesDir, "linux")
    val ubuntuDir get() = File(linuxDir, "ubuntu")
    val rootfs get() = File(ubuntuDir, "rootfs")
    val home get() = File(rootfs, "root")
    private val staging get() = File(ubuntuDir, "rootfs.tmp")
    private val downloads get() = File(linuxDir, "downloads")
    private val partialArchive get() = File(downloads, "ubuntu-rootfs.tar.gz.part")
    private val runtimeDir get() = File(linuxDir, "runtime")
    val executable get() = File("/system/bin/linker64")
    val proot get() = File(runtimeDir, "proot")
    val loader get() = File(runtimeDir, "loader")
    val libDir get() = File(runtimeDir, "lib")
    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.MINUTES).retryOnConnectionFailure(true).build()
    private val activeDownload = AtomicReference<Call?>(null)
    private val _status = MutableStateFlow(initialStatus())
    val status: StateFlow<UbuntuSetupStatus> = _status
    private val _diagnostics = MutableStateFlow<UbuntuDiagnostics?>(null)
    val diagnostics: StateFlow<UbuntuDiagnostics?> = _diagnostics

    fun isReady(): Boolean {
        val install = runCatching { com.google.gson.Gson().fromJson(marker().readText(), UbuntuInstallState::class.java) }.getOrNull()
        return requiredFilesExist(rootfs) && proot.canExecute() && install?.healthCheckPassed == true && install.version == manifest.version && install.checksum == manifest.sha256
    }

    @Synchronized
    fun install(): Result<Unit> {
        if (isReady()) { _status.value = UbuntuSetupStatus(UbuntuSetupStage.READY); return Result.success(Unit) }
        if (!Build.SUPPORTED_ABIS.contains(manifest.androidAbi)) return fail("Ubuntu requires an arm64-v8a device")
        if (BuildConfig.VERSION_CODE < manifest.minimumAppVersion) return fail("This Ubuntu runtime requires a newer OctoBot version")
        linuxDir.mkdirs(); ubuntuDir.mkdirs(); downloads.mkdirs(); runtimeDir.mkdirs(); libDir.mkdirs()
        return runCatching {
            copyRuntime()
            downloadArchive()
            _status.value = UbuntuSetupStatus(UbuntuSetupStage.VERIFYING, partialArchive.length(), partialArchive.length())
            check(sha256(partialArchive) == manifest.sha256) { "Ubuntu download checksum does not match the official manifest" }
            deleteTreeNoFollow(staging)
            staging.mkdirs()
            _status.value = UbuntuSetupStatus(UbuntuSetupStage.EXTRACTING)
            extractSafely(partialArchive, staging)
            validateExtractedRootfs(staging)
            _status.value = UbuntuSetupStatus(UbuntuSetupStage.CONFIGURING)
            configure(staging)
            deleteTreeNoFollow(rootfs)
            check(staging.renameTo(rootfs)) { "Unable to activate Ubuntu root filesystem" }
            _status.value = UbuntuSetupStatus(UbuntuSetupStage.HEALTH_CHECKING)
            val diag = healthCheck()
            _diagnostics.value = diag
            check(diag.exitCode == 0 && diag.stdout.contains("health-check-ok") && diag.stdout.contains("apt")) { "Ubuntu could not start:\n${diag.display()}" }
            marker().writeText(com.google.gson.Gson().toJson(UbuntuInstallState(manifest.version, manifest.architecture, manifest.sha256, System.currentTimeMillis(), true, rootfs.absolutePath)))
            partialArchive.delete()
            _status.value = UbuntuSetupStatus(UbuntuSetupStage.READY)
            Log.i(TAG, "Ubuntu ${manifest.version} is ready at ${rootfs.absolutePath}")
            Unit
        }.onFailure { error ->
            deleteTreeNoFollow(staging)
            if (error.message?.contains("checksum", true) == true) partialArchive.delete()
            fail(error.message ?: error.javaClass.simpleName)
        }
    }

    fun cancelDownload() { activeDownload.getAndSet(null)?.cancel(); _status.value = UbuntuSetupStatus(UbuntuSetupStage.NOT_INSTALLED, message = "Download cancelled") }
    fun deletePartialFiles() { activeDownload.getAndSet(null)?.cancel(); partialArchive.delete(); deleteTreeNoFollow(staging); if (!isReady()) marker().delete(); _status.value = if (isReady()) UbuntuSetupStatus(UbuntuSetupStage.READY) else UbuntuSetupStatus(UbuntuSetupStage.NOT_INSTALLED) }
    fun reset() { deletePartialFiles(); marker().delete(); deleteTreeNoFollow(rootfs); _status.value = UbuntuSetupStatus(UbuntuSetupStage.NOT_INSTALLED) }

    fun prootCommand(command: String): List<String> = prootArgs(listOf("/bin/bash", "-lc", command))
    fun interactiveProotArgs(): Array<String> = prootArgs(listOf("/bin/bash", "--noprofile", "-i")).drop(1).toTypedArray()
    fun hostEnvironment(): Array<String> = arrayOf(
        "PROOT_LOADER=${loader.absolutePath}",
        "PROOT_TMP_DIR=${File(runtimeDir, "tmp").apply { mkdirs() }.absolutePath}",
        "LD_LIBRARY_PATH=${libDir.absolutePath}",
        "PROOT_NO_SECCOMP=1",
        "TERM=xterm-256color",
        "PATH=/system/bin:/system/xbin",
    )

    private fun prootArgs(shell: List<String>): List<String> {
        check(requiredFilesExist(rootfs) && proot.canExecute()) { "Ubuntu is not ready" }
        // Apps targeting modern Android cannot exec binaries copied to filesDir.
        // Starting the Android ELF through the platform linker is the supported
        // compatibility path used by terminal environments for private binaries.
        val args = mutableListOf(executable.absolutePath, proot.absolutePath, "--kill-on-exit", "-0", "-r", rootfs.absolutePath, "-b", "/dev", "-b", "/proc", "-b", "/sys", "-b", "${app.filesDir.absolutePath}:/siko", "-w", "/root", "/usr/bin/env", "-i", "HOME=/root", "USER=root", "SHELL=/bin/bash", "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin", "TERM=xterm-256color", "LANG=C.UTF-8", "PS1=~ \\$ ")
        args += shell
        return args
    }

    private fun downloadArchive() {
        _status.value = UbuntuSetupStatus(UbuntuSetupStage.DOWNLOADING, totalBytes = manifest.downloadSize)
        val request = Request.Builder().url(manifest.officialUrl).header("User-Agent", "OctoBot/${BuildConfig.VERSION_NAME}").build()
        val call = client.newCall(request); activeDownload.set(call)
        call.execute().use { response ->
            check(response.isSuccessful) { "Ubuntu download failed: HTTP ${response.code}" }
            val body = response.body ?: error("Ubuntu server returned an empty response")
            val total = body.contentLength().takeIf { it > 0 } ?: manifest.downloadSize
            var downloaded = 0L; var windowBytes = 0L; var windowStarted = System.nanoTime()
            FileOutputStream(partialArchive, false).use { output -> body.byteStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer); if (count < 0) break
                    output.write(buffer, 0, count); downloaded += count; windowBytes += count
                    val now = System.nanoTime(); val elapsed = now - windowStarted
                    if (elapsed >= 250_000_000L) {
                        val speed = (windowBytes * 1_000_000_000L / elapsed).coerceAtLeast(0)
                        _status.value = UbuntuSetupStatus(UbuntuSetupStage.DOWNLOADING, downloaded, total, speed)
                        windowBytes = 0; windowStarted = now
                    }
                }
                output.fd.sync()
            } }
            check(downloaded > 0) { "Ubuntu download was empty" }
        }
        activeDownload.compareAndSet(call, null)
    }

    internal fun extractSafely(archive: File, destination: File) {
        val canonicalRoot = destination.canonicalFile
        val links = mutableListOf<Pair<File, TarArchiveEntry>>()
        TarArchiveInputStream(GzipCompressorInputStream(FileInputStream(archive), true)).use { tar ->
            var entry = tar.nextEntry as? TarArchiveEntry
            while (entry != null) {
                val cleanName = entry.name.removePrefix("./")
                if (cleanName.isNotBlank()) {
                    val target = File(destination, cleanName).canonicalFile
                    check(target.path == canonicalRoot.path || target.path.startsWith(canonicalRoot.path + File.separator)) { "Unsafe path in Ubuntu archive: ${entry.name}" }
                    when {
                        entry.isDirectory -> target.mkdirs()
                        entry.isSymbolicLink || entry.isLink -> links += target to entry
                        entry.isFile -> { target.parentFile?.mkdirs(); FileOutputStream(target).use { tar.copyTo(it) }; runCatching { Os.chmod(target.absolutePath, entry.mode) } }
                    }
                }
                entry = tar.nextEntry as? TarArchiveEntry
            }
        }
        links.forEach { (target, entry) ->
            target.parentFile?.mkdirs(); if (target.exists()) target.delete()
            if (entry.isSymbolicLink) Os.symlink(entry.linkName, target.absolutePath)
            else {
                val source = File(destination, entry.linkName.removePrefix("./")).canonicalFile
                check(source.path.startsWith(canonicalRoot.path + File.separator)) { "Unsafe hard link in Ubuntu archive" }
                check(source.isFile) { "Hard-link source is missing: ${entry.linkName}" }
                source.inputStream().use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                runCatching { Os.chmod(target.absolutePath, entry.mode) }
            }
        }
    }

    private fun configure(dir: File) {
        File(dir, "root").mkdirs()
        File(dir, "etc/resolv.conf").writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
        File(dir, "root/.bashrc").writeText("export PS1='~ \\$ '\nexport HOME=/root\ncd /root\n")
    }

    private fun copyRuntime() {
        fun copy(asset: String, target: File) {
            target.parentFile?.mkdirs()
            app.assets.open("linux/arm64-v8a/$asset").use { input -> target.outputStream().use(input::copyTo) }
        }
        copy("proot", proot)
        copy("loader", loader)
        copy("libandroid-shmem.so", File(libDir, "libandroid-shmem.so"))
        copy("libtalloc.so.2", File(libDir, "libtalloc.so.2"))
        check(proot.setExecutable(true) && proot.canExecute()) { "Unable to prepare bundled PRoot" }
        check(loader.setExecutable(true) && loader.canExecute()) { "Unable to prepare bundled PRoot loader" }
        check(executable.canExecute()) { "Android ARM64 linker is unavailable" }
    }

    private fun healthCheck(): UbuntuDiagnostics {
        val command = "cat /etc/os-release && command -v apt && apt --version && echo health-check-ok"
        val args = runCatching { prootCommand(command) }.getOrElse { return baseDiagnostics("", exception = it) }
        return try {
            val pb = ProcessBuilder(args).directory(linuxDir)
            hostEnvironment().forEach { item -> item.indexOf('=').let { pb.environment()[item.substring(0, it)] = item.substring(it + 1) } }
            Log.i(TAG, "RootFS=${rootfs.absolutePath}; PRoot=${proot.absolutePath}; ABI=${Build.SUPPORTED_ABIS.joinToString()}; command=${args.joinToString(" ")}")
            val process = pb.start()
            val stdout = StringBuilder(); val stderr = StringBuilder()
            val outThread = Thread { process.inputStream.bufferedReader().use { stdout.append(it.readText()) } }.apply { start() }
            val errThread = Thread { process.errorStream.bufferedReader().use { stderr.append(it.readText()) } }.apply { start() }
            val finished = process.waitFor(60, TimeUnit.SECONDS)
            if (!finished) process.destroyForcibly()
            outThread.join(2_000); errThread.join(2_000)
            baseDiagnostics(args.joinToString(" "), stdout.toString(), stderr.toString(), if (finished) process.exitValue() else -1).also { Log.i(TAG, it.display()) }
        } catch (error: Throwable) { baseDiagnostics(args.joinToString(" "), exception = error).also { Log.e(TAG, it.display(), error) } }
    }

    private fun baseDiagnostics(command: String, stdout: String = "", stderr: String = "", exitCode: Int? = null, exception: Throwable? = null) = UbuntuDiagnostics(
        abi = Build.SUPPORTED_ABIS.joinToString(), rootfsPath = rootfs.absolutePath, rootfsExists = rootfs.exists(),
        shellExists = File(rootfs, "bin/sh").exists(), bashExists = File(rootfs, "bin/bash").exists() || File(rootfs, "usr/bin/bash").exists(),
        aptExists = File(rootfs, "usr/bin/apt").exists(), osReleaseExists = File(rootfs, "etc/os-release").exists(),
        prootPath = proot.absolutePath, prootExists = proot.exists(), prootExecutable = proot.canExecute(), fullCommand = command,
        stdout = stdout, stderr = stderr, exitCode = exitCode, exceptionType = exception?.javaClass?.name, exceptionMessage = exception?.message,
    )

    private fun requiredFilesExist(dir: File) = File(dir, "bin/sh").exists() && (File(dir, "bin/bash").exists() || File(dir, "usr/bin/bash").exists()) && File(dir, "usr/bin/apt").exists() && File(dir, "etc/os-release").exists()
    private fun validateExtractedRootfs(dir: File) {
        check(requiredFilesExist(dir)) { "Ubuntu archive is incomplete" }
        check(Files.isSymbolicLink(File(dir, "bin").toPath())) { "Ubuntu /bin link was not extracted" }
        check(Files.isSymbolicLink(File(dir, "etc/os-release").toPath())) { "Ubuntu os-release link was not extracted" }
        check(File(dir, "usr/bin/perl5.38.2").length() == File(dir, "usr/bin/perl").length()) { "Ubuntu hard links were not restored correctly" }
        val entries = Files.walk(dir.toPath()).use { stream -> stream.count() }
        check(entries >= 3_000) { "Ubuntu extraction produced too few files ($entries)" }
    }
    private fun deleteTreeNoFollow(directory: File) {
        if (!directory.exists() && !Files.isSymbolicLink(directory.toPath())) return
        Files.walkFileTree(directory.toPath(), object : SimpleFileVisitor<java.nio.file.Path>() {
            override fun visitFile(file: java.nio.file.Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.deleteIfExists(file)
                return FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(dir: java.nio.file.Path, error: java.io.IOException?): FileVisitResult {
                if (error != null) throw error
                Files.deleteIfExists(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }
    private fun marker() = File(ubuntuDir, "install_state.json")
    private fun sha256(file: File): String { val digest = MessageDigest.getInstance("SHA-256"); FileInputStream(file).use { input -> val b = ByteArray(128 * 1024); while (true) { val n = input.read(b); if (n < 0) break; digest.update(b, 0, n) } }; return digest.digest().joinToString("") { "%02x".format(it) } }
    private fun initialStatus() = if (isReady()) UbuntuSetupStatus(UbuntuSetupStage.READY) else UbuntuSetupStatus(UbuntuSetupStage.NOT_INSTALLED)
    private fun fail(message: String): Result<Unit> { Log.e(TAG, message); _status.value = UbuntuSetupStatus(UbuntuSetupStage.FAILED, message = message); return Result.failure(IllegalStateException(message)) }
}
