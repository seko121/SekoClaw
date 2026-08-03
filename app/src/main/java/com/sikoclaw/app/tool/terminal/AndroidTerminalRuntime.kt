package com.sikoclaw.app.tool.terminal

import android.system.Os
import com.sikoclaw.app.ClawApplication
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File
import java.nio.file.Files
import java.util.UUID

/** App-private Android/Bionic shell runtime. No distribution or PRoot is involved. */
object AndroidTerminalRuntime {
    private val app get() = ClawApplication.instance
    val terminalDir get() = File(app.filesDir, "terminal")
    val homeDir get() = File(terminalDir, "home")
    val usrDir get() = File(terminalDir, "usr")
    val binDir get() = File(usrDir, "bin")
    val tmpDir get() = File(usrDir, "tmp")
    val libDir get() = File(usrDir, "lib")
    val libexecDir get() = File(usrDir, "libexec")
    val etcDir get() = File(usrDir, "etc")
    val shell get() = File("/system/bin/sh")
    private val busybox get() = File(app.applicationInfo.nativeLibraryDir, "libbusybox.so")

    private val busyboxApplets = listOf(
        "ash", "awk", "cat", "chmod", "cp", "cut", "date", "dd", "df", "du",
        "echo", "env", "find", "grep", "gzip", "head", "less", "ln", "ls", "mkdir",
        "mv", "printf", "pwd", "rm", "sed", "sha256sum", "sort", "tail", "tar", "touch",
        "tr", "unzip", "wc", "wget", "which", "xargs", "xz",
    )

    @Synchronized
    fun prepare() {
        homeDir.mkdirs(); binDir.mkdirs(); tmpDir.mkdirs(); libDir.mkdirs(); libexecDir.mkdirs(); etcDir.mkdirs()
        check(shell.canExecute()) { "Android shell is unavailable" }
        check(busybox.canExecute()) { "Bundled Android BusyBox is unavailable" }
        busyboxApplets.forEach { name ->
            val link = File(binDir, name).toPath()
            if (Files.isSymbolicLink(link) && runCatching { Files.readSymbolicLink(link).toString() == busybox.absolutePath }.getOrDefault(false)) return@forEach
            Files.deleteIfExists(link)
            Os.symlink(busybox.absolutePath, link.toString())
        }
        val curl = File(binDir, "curl")
        curl.writeText("#!/system/bin/sh\nexport CLASSPATH='${app.applicationInfo.sourceDir}'\nexec /system/bin/app_process / com.sikoclaw.app.tool.terminal.ShellCurlMain \"\$@\"\n")
        Os.chmod(curl.absolutePath, 0b111101101)
        val zip = File(binDir, "zip")
        zip.writeText("#!/system/bin/sh\nexport CLASSPATH='${app.applicationInfo.sourceDir}'\nexec /system/bin/app_process / com.sikoclaw.app.tool.terminal.ShellZipMain \"\$@\"\n")
        Os.chmod(zip.absolutePath, 0b111101101)
        copyAsset("openssl", File(libexecDir, "openssl"))
        copyAsset("libcrypto.so.3", File(libDir, "libcrypto.so.3"))
        copyAsset("libssl.so.3", File(libDir, "libssl.so.3"))
        copyAsset("openssl.cnf", File(etcDir, "openssl.cnf"))
        copyAsset("cert.pem", File(etcDir, "cert.pem"))
        val openssl = File(binDir, "openssl")
        openssl.writeText("#!/system/bin/sh\nexport LD_LIBRARY_PATH='${libDir.absolutePath}'\nexport OPENSSL_CONF='${File(etcDir, "openssl.cnf").absolutePath}'\nexport SSL_CERT_FILE='${File(etcDir, "cert.pem").absolutePath}'\nexec /system/bin/linker64 '${File(libexecDir, "openssl").absolutePath}' \"\$@\"\n")
        Os.chmod(openssl.absolutePath, 0b111101101)
        File(homeDir, ".profile").writeText("export HOME='${homeDir.absolutePath}'\nexport PATH='${binDir.absolutePath}:/system/bin'\nexport TMPDIR='${tmpDir.absolutePath}'\nexport PS1='~ \\$ '\ncd \"\$HOME\"\n")
    }

    private fun copyAsset(name: String, target: File) {
        if (target.isFile && target.length() > 0) return
        app.assets.open("terminal/arm64-v8a/$name").use { input -> target.outputStream().use(input::copyTo) }
    }

    fun environment(): Array<String> {
        prepare()
        return arrayOf(
            "HOME=${homeDir.absolutePath}",
            "PATH=${binDir.absolutePath}:/system/bin",
            "TMPDIR=${tmpDir.absolutePath}",
            "TERM=xterm-256color",
            "SHELL=${shell.absolutePath}",
            "ENV=${File(homeDir, ".profile").absolutePath}",
            "PS1=~ \\$ ",
        )
    }
}

data class AndroidTerminalTab(val id: String, var name: String, val session: TerminalSession)

object AndroidTerminalSessions {
    private val sessions = mutableListOf<AndroidTerminalTab>()
    fun all(): List<AndroidTerminalTab> = synchronized(sessions) { sessions.toList() }
    fun create(client: TerminalSessionClient, name: String = "Terminal ${all().size + 1}"): AndroidTerminalTab {
        AndroidTerminalRuntime.prepare()
        val session = TerminalSession(
            AndroidTerminalRuntime.shell.absolutePath,
            AndroidTerminalRuntime.homeDir.absolutePath,
            arrayOf("-i"),
            AndroidTerminalRuntime.environment(),
            10_000,
            client,
        )
        session.mSessionName = name
        return AndroidTerminalTab(UUID.randomUUID().toString(), name, session).also { synchronized(sessions) { sessions += it } }
    }
    fun close(id: String) = synchronized(sessions) { sessions.firstOrNull { it.id == id }?.also { it.session.finishIfRunning(); sessions.remove(it) } }
    fun closeAll() = synchronized(sessions) { sessions.forEach { it.session.finishIfRunning() }; sessions.clear() }
}
