package com.sikoclaw.app.tool.web

import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import com.sikoclaw.app.ClawApplication
import com.sikoclaw.app.tool.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

private val webClient=OkHttpClient.Builder().connectTimeout(12,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).followRedirects(true).build()

class WebSearchTool:BaseTool(){
 override fun getName()="web_search";override fun getDisplayName()="Web Search";override fun getDescriptionEN()="Search the public web in the background and return result titles, snippets, and URLs. Use it for current information.";override fun getDescriptionCN()=getDescriptionEN()
 override fun getParameters()=listOf(ToolParameter("query","string","Search query",true),ToolParameter("max_results","integer","1-10, default 5",false))
 override fun execute(p:Map<String,Any>):ToolResult { return try{val q=requireString(p,"query");val max=optionalInt(p,"max_results",5).coerceIn(1,10);val url="https://html.duckduckgo.com/html/".toHttpUrl().newBuilder().addQueryParameter("q",q).build();val req=Request.Builder().url(url).header("User-Agent","Mozilla/5.0 SikoClaw/1.0").build();webClient.newCall(req).execute().use{r->if(!r.isSuccessful) ToolResult.error("Search HTTP ${r.code}") else {val html=r.body?.string().orEmpty();val pattern=Regex("<a[^>]+class=\\\"result__a\\\"[^>]+href=\\\"([^\\\"]+)\\\"[^>]*>(.*?)</a>",RegexOption.IGNORE_CASE);val results=pattern.findAll(html).take(max).mapIndexed{i,m->val raw=m.groupValues[1];val link=Regex("uddg=([^&]+)").find(raw)?.groupValues?.get(1)?.let{URLDecoder.decode(it,"UTF-8")}?:raw.replace("&amp;","&");val title=m.groupValues[2].replace(Regex("<[^>]+>"),"").replace("&amp;","&");"${i+1}. $title\n$link"}.toList();if(results.isEmpty())ToolResult.error("No search results")else ToolResult.success(results.joinToString("\n\n"))}}}catch(e:Exception){ToolResult.error("Search failed: ${e.message}")} }
}

class DownloadFileTool:BaseTool(){
 override fun getName()="download_file";override fun getDisplayName()="Download File";override fun getDescriptionEN()="Download a file from a direct HTTPS URL into the phone Downloads/Siko Claw folder. Never download executables unless the user explicitly requested that exact file.";override fun getDescriptionCN()=getDescriptionEN()
 override fun getParameters()=listOf(ToolParameter("url","string","Direct HTTPS file URL",true),ToolParameter("file_name","string","Optional safe file name",false))
 override fun execute(p:Map<String,Any>):ToolResult { return try{val raw=requireString(p,"url");if(!raw.startsWith("https://"))return ToolResult.error("Only HTTPS downloads are allowed");val parsed=raw.toHttpUrl();var name=optionalString(p,"file_name","").ifBlank{parsed.pathSegments.lastOrNull().orEmpty().ifBlank{"download_${System.currentTimeMillis()}"}};name=name.replace(Regex("[^A-Za-z0-9._ -]"),"_").take(120);val req=Request.Builder().url(parsed).header("User-Agent","SikoClaw/1.0").build();webClient.newCall(req).execute().use{r->if(!r.isSuccessful)return ToolResult.error("Download HTTP ${r.code}");val body=r.body?:return ToolResult.error("Empty file");val c=ClawApplication.instance;val values=ContentValues().apply{put(MediaStore.Downloads.DISPLAY_NAME,name);put(MediaStore.Downloads.MIME_TYPE,body.contentType()?.toString()?:"application/octet-stream");if(Build.VERSION.SDK_INT>=29)put(MediaStore.Downloads.RELATIVE_PATH,"Download/Siko Claw")};val uri=c.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)?:return ToolResult.error("Cannot create download");c.contentResolver.openOutputStream(uri)?.use{out->body.byteStream().use{it.copyTo(out)}}?:return ToolResult.error("Cannot write file");ToolResult.success("Downloaded $name (${body.contentLength()} bytes) to Downloads/Siko Claw")}}catch(e:Exception){ToolResult.error("Download failed: ${e.message}")} }
}
