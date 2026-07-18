package com.sikoclaw.app.tool.terminal

import com.sikoclaw.app.ClawApplication
import com.sikoclaw.app.tool.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

object InternalTerminal {
    private val base get() = File(ClawApplication.instance.filesDir, "terminal").apply { mkdirs() }
    private val rootfs get() = File(base, "rootfs")
    private val proot get() = File(base, "proot")
    private val loader get() = File(base, "loader")
    private val libDir get() = File(base, "lib").apply { mkdirs() }
    fun linuxInstalled() = proot.canExecute() && File(rootfs, "bin/sh").exists()
    fun status() = if (linuxInstalled()) "Alpine Linux ready (Git: apk add git)" else "Preparing bundled Alpine Linux"
    @Synchronized fun bootstrapBundled():ToolResult {
        if(linuxInstalled())return ToolResult.success(status())
        if(!android.os.Build.SUPPORTED_ABIS.contains("arm64-v8a"))return ToolResult.error("Bundled Alpine supports ARM64 phones. Android shell remains ready.")
        return try{
            val assets=ClawApplication.instance.assets
            fun copy(name:String,target:File){assets.open("linux/arm64-v8a/$name").use{i->target.outputStream().use{i.copyTo(it)}}}
            copy("proot",proot);proot.setExecutable(true);copy("loader",loader);loader.setExecutable(true)
            copy("libandroid-shmem.so",File(libDir,"libandroid-shmem.so"));copy("libtalloc.so.2",File(libDir,"libtalloc.so.2"))
            val archive=File(base,"alpine-minirootfs.tar.gz");copy("alpine-minirootfs.tar.gz",archive);rootfs.mkdirs()
            val extract=ProcessBuilder("/system/bin/tar","-xzf",archive.absolutePath,"-C",rootfs.absolutePath).redirectErrorStream(true).start()
            val log=extract.inputStream.bufferedReader().readText();if(extract.waitFor()!=0)return ToolResult.error("Alpine extraction failed: $log")
            archive.delete();File(rootfs,"etc/resolv.conf").writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
            ToolResult.success(status())
        }catch(e:Exception){ToolResult.error("Bundled Alpine setup failed: ${e.message}")}
    }
    fun run(command:String, linux:Boolean, timeout:Int):ToolResult { return try {
        val process = if(linux){
            if(!linuxInstalled())bootstrapBundled().let{if(!it.isSuccess)return it}
            ProcessBuilder(proot.absolutePath,"--kill-on-exit","-0","-r",rootfs.absolutePath,"-b","/dev","-b","/proc","-b","/sys","-w","/root","/usr/bin/env","-i","HOME=/root","PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin","TERM=xterm-256color","/bin/sh","-lc",command).apply{
                environment()["PROOT_LOADER"]=loader.absolutePath;environment()["PROOT_TMP_DIR"]=File(base,"tmp").apply{mkdirs()}.absolutePath;environment()["LD_LIBRARY_PATH"]=libDir.absolutePath
            }
        } else ProcessBuilder("/system/bin/sh","-c",command)
        process.directory(base);process.redirectErrorStream(false);val p=process.start()
        if(!p.waitFor(timeout.coerceIn(1,120).toLong(),TimeUnit.SECONDS)){p.destroyForcibly();return ToolResult.error("Command timed out")}
        val out=p.inputStream.bufferedReader().readText();val err=p.errorStream.bufferedReader().readText()
        ToolResult.success("exit=${p.exitValue()}\nstdout:\n$out\nstderr:\n$err".take(100000))
    } catch(e:Exception){ToolResult.error("Terminal error: ${e.message}")} }
    fun install(prootUrl:String,rootfsUrl:String,confirmed:Boolean):ToolResult{
        if(!confirmed)return ToolResult.error("Linux installation requires an explicit user request")
        if(!prootUrl.startsWith("https://")||!rootfsUrl.startsWith("https://"))return ToolResult.error("HTTPS URLs required")
        return try{val client=OkHttpClient.Builder().readTimeout(5,TimeUnit.MINUTES).build();fun download(url:String,file:File){client.newCall(Request.Builder().url(url).build()).execute().use{r->if(!r.isSuccessful)error("HTTP ${r.code}");r.body?.byteStream()?.use{input->file.outputStream().use{input.copyTo(it)}}?:error("Empty download")}}
            val archive=File(base,"linux-rootfs.tar.gz");download(prootUrl,proot);proot.setExecutable(true);download(rootfsUrl,archive);rootfs.mkdirs()
            val extract=ProcessBuilder("/system/bin/tar","-xzf",archive.absolutePath,"-C",rootfs.absolutePath).redirectErrorStream(true).start();val log=extract.inputStream.bufferedReader().readText();if(extract.waitFor()!=0)return ToolResult.error("Rootfs extraction failed: $log");archive.delete();ToolResult.success("Linux installed successfully. ${status()}")
        }catch(e:Exception){ToolResult.error("Linux installation failed: ${e.message}")}
    }
}

class InternalTerminalTool:BaseTool(){
 override fun getName()="internal_terminal";override fun getDisplayName()="Internal Terminal";override fun getDescriptionEN()="Run commands in the private Android shell or bundled Alpine Linux. Alpine uses apk packages; install Git when needed with apk add git. Do not run destructive commands.";override fun getDescriptionCN()=getDescriptionEN()
 override fun getParameters()=listOf(ToolParameter("command","string","Shell command",true),ToolParameter("environment","string","android or linux",false),ToolParameter("timeout_seconds","integer","1-120",false))
 override fun execute(p:Map<String,Any>)=InternalTerminal.run(requireString(p,"command"),optionalString(p,"environment","android").equals("linux",true),optionalInt(p,"timeout_seconds",45))
}
class InstallLinuxEnvironmentTool:BaseTool(){
 override fun getName()="install_linux_environment";override fun getDisplayName()="Install Linux Environment";override fun getDescriptionEN()="Download and install a proot-compatible Linux rootfs inside Siko Claw. Use only after the user explicitly asks to install Linux and after finding trusted architecture-compatible HTTPS artifacts.";override fun getDescriptionCN()=getDescriptionEN()
 override fun getParameters()=listOf(ToolParameter("proot_url","string","Trusted static proot binary URL for this CPU",true),ToolParameter("rootfs_url","string","Trusted Linux rootfs tar.gz URL for this CPU",true),ToolParameter("user_explicitly_requested","boolean","Must be true",true))
 override fun execute(p:Map<String,Any>)=InternalTerminal.install(requireString(p,"proot_url"),requireString(p,"rootfs_url"),optionalBoolean(p,"user_explicitly_requested",false))
}
