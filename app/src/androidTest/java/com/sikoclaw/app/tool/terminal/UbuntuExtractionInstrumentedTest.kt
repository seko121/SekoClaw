package com.sikoclaw.app.tool.terminal

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
class UbuntuExtractionInstrumentedTest {
    @Test
    fun extractsFilesSymlinksAndHardLinksInsideAppStorage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val base = File(context.cacheDir, "ubuntu-extraction-test").apply { deleteRecursively(); mkdirs() }
        val archive = File(base, "sample.tar.gz")
        GzipCompressorOutputStream(archive.outputStream()).use { gzip ->
            TarArchiveOutputStream(gzip).use { tar ->
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                listOf("usr/", "usr/bin/").forEach { name -> tar.putArchiveEntry(TarArchiveEntry(name).apply { mode = 0b111101101 }); tar.closeArchiveEntry() }
                val payload = "working-hard-link".toByteArray()
                tar.putArchiveEntry(TarArchiveEntry("usr/bin/original").apply { size = payload.size.toLong(); mode = 0b111101101 }); tar.write(payload); tar.closeArchiveEntry()
                tar.putArchiveEntry(TarArchiveEntry("usr/bin/copy", TarArchiveEntry.LF_LINK).apply { linkName = "usr/bin/original" }); tar.closeArchiveEntry()
                tar.putArchiveEntry(TarArchiveEntry("bin", TarArchiveEntry.LF_SYMLINK).apply { linkName = "usr/bin" }); tar.closeArchiveEntry()
                tar.finish()
            }
        }
        val destination = File(base, "rootfs").apply { mkdirs() }
        UbuntuRuntime.extractSafely(archive, destination)
        assertTrue(Files.isSymbolicLink(File(destination, "bin").toPath()))
        assertEquals("usr/bin", Files.readSymbolicLink(File(destination, "bin").toPath()).toString())
        assertArrayEquals(File(destination, "usr/bin/original").readBytes(), File(destination, "usr/bin/copy").readBytes())
        assertTrue(File(destination, "bin/original").canExecute())
    }

    @Test
    fun partialCleanupDoesNotFollowSymlinks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val outside = File(context.cacheDir, "must-survive-cleanup").apply { mkdirs() }
        val sentinel = File(outside, "sentinel.txt").apply { writeText("keep") }
        val staging = File(context.filesDir, "linux/ubuntu/rootfs.tmp").apply { mkdirs() }
        Files.createSymbolicLink(File(staging, "outside-link").toPath(), outside.toPath())
        File(staging, "partial.txt").writeText("partial")
        UbuntuRuntime.deletePartialFiles()
        assertTrue("cleanup followed a symlink outside staging", sentinel.isFile)
        assertEquals("keep", sentinel.readText())
        assertTrue("staging directory was not removed", !staging.exists())
    }
}
