package com.sikoclaw.app.tool.terminal

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess

object ShellZipMain {
    @JvmStatic fun main(args: Array<String>) {
        if (args.any { it == "--version" || it == "-v" }) { println("zip (OctoBot Android) 1.0"); return }
        val values = args.filterNot { it.startsWith('-') }
        if (values.size < 2) { System.err.println("usage: zip archive.zip file..."); exitProcess(2) }
        try {
            ZipOutputStream(FileOutputStream(values.first())).use { zip ->
                values.drop(1).forEach { path ->
                    val file = File(path); if (!file.isFile) return@forEach
                    zip.putNextEntry(ZipEntry(file.name)); FileInputStream(file).use { it.copyTo(zip) }; zip.closeEntry()
                }
            }
        } catch (error: Throwable) { System.err.println("zip: ${error.message}"); exitProcess(1) }
    }
}
