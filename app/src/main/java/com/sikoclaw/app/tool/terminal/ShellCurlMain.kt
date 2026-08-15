package com.sikoclaw.app.tool.terminal

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/** Small curl-compatible command for the app shell, backed by Android/OkHttp TLS. */
object ShellCurlMain {
    @JvmStatic fun main(args: Array<String>) {
        if (args.any { it == "--version" || it == "-V" }) {
            println("curl (OctoBot Android) 1.0 OkHttp HTTPS")
            println("Protocols: http https")
            return
        }
        var head = false; var silent = false; var fail = false; var output: String? = null; var url: String? = null
        var i = 0
        while (i < args.size) {
            when (val arg = args[i]) {
                "-I", "--head" -> head = true
                "-s", "--silent" -> silent = true
                "-f", "--fail" -> fail = true
                "-L", "--location", "-S", "--show-error" -> Unit
                "-o", "--output" -> { i++; output = args.getOrNull(i) }
                else -> if (!arg.startsWith('-')) url = arg
            }; i++
        }
        val target = url ?: run { System.err.println("curl: no URL specified"); exitProcess(2) }
        try {
            val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(5, TimeUnit.MINUTES).followRedirects(true).build()
            val request = Request.Builder().url(target).apply { if (head) head() }.build()
            client.newCall(request).execute().use { response ->
                if (head) {
                    println("HTTP ${response.code} ${response.message}")
                    response.headers.forEach { (name, value) -> println("$name: $value") }
                } else {
                    val bytes = response.body?.bytes() ?: ByteArray(0)
                    if (output != null) File(output!!).writeBytes(bytes) else System.out.write(bytes)
                }
                if (fail && !response.isSuccessful) exitProcess(22)
                if (!silent && output != null) System.err.println("Saved ${response.body?.contentLength() ?: 0} bytes to $output")
            }
        } catch (error: Throwable) {
            System.err.println("curl: ${error.message ?: error.javaClass.simpleName}")
            exitProcess(1)
        }
    }
}
