package com.sikoclaw.app.tool.terminal

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidTerminalInstrumentedTest {
    @Test fun appPrivateShellAndToolsWork() {
        assumeTrue(android.os.Build.SUPPORTED_ABIS.contains("arm64-v8a"))
        AndroidTerminalRuntime.prepare()
        val commands = listOf("pwd", "ls", "wget --help", "tar --help", "gzip --help", "xz --help", "unzip --help", "sha256sum --help", "curl --version", "openssl version", "zip --version")
        commands.forEach { command ->
            val process = ProcessBuilder(AndroidTerminalRuntime.shell.absolutePath, "-c", command).directory(AndroidTerminalRuntime.homeDir)
            AndroidTerminalRuntime.environment().forEach { value -> value.indexOf('=').let { process.environment()[value.substring(0, it)] = value.substring(it + 1) } }
            val running = process.redirectErrorStream(true).start()
            val output = running.inputStream.bufferedReader().readText()
            assertTrue("$command failed: $output", running.waitFor() == 0)
        }
    }

    @Test fun agentWorkingDirectoryPersists() {
        assumeTrue(android.os.Build.SUPPORTED_ABIS.contains("arm64-v8a"))
        assertTrue(InternalTerminal.run("mkdir -p test && cd test", 30).isSuccess)
        val pwd = InternalTerminal.run("pwd", 30)
        assertTrue(pwd.isSuccess && pwd.data.orEmpty().endsWith("/test"))
    }
}
