package com.sikoclaw.app.tool.web

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.sikoclaw.app.ClawApplication
import com.sikoclaw.app.tool.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import com.google.gson.JsonParser
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

private val webClient=OkHttpClient.Builder().connectTimeout(12,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).followRedirects(true).build()

class WebSearchTool:BaseTool(){
 override fun getName()="web_search";override fun getDisplayName()="Web Search";override fun getDescriptionEN()="Search the public web in the background and return result titles, snippets, and URLs. Use it for current information.";override fun getDescriptionCN()=getDescriptionEN()
 override fun getParameters()=listOf(ToolParameter("query","string","Search query",true),ToolParameter("max_results","integer","1-10, default 5",false))
 override fun execute(p:Map<String,Any>):ToolResult { return try {
   val q=requireString(p,"query"); val max=optionalInt(p,"max_results",5).coerceIn(1,10); val config=SearchProviderStore.current()
   val results=when(config.provider){
     SearchProvider.DUCKDUCKGO -> duckDuckGo(q,max)
     SearchProvider.BRAVE -> brave(q,max,config.apiKey)
     SearchProvider.TAVILY -> tavily(q,max,config.apiKey)
     SearchProvider.GOOGLE_CUSTOM -> googleCustom(q,max,config.apiKey,config.engineId)
   }
   if(results.isEmpty()) ToolResult.error("No search results") else ToolResult.success(results.joinToString("\n\n"))
 }catch(e:Exception){ToolResult.error("Search failed: ${e.message}")} }

 private fun duckDuckGo(query:String,max:Int):List<String>{ val url="https://html.duckduckgo.com/html/".toHttpUrl().newBuilder().addQueryParameter("q",query).build(); val req=Request.Builder().url(url).header("User-Agent","Mozilla/5.0 OctoBot/1.0").build(); return webClient.newCall(req).execute().use{r->if(!r.isSuccessful)error("DuckDuckGo HTTP ${r.code}"); val html=r.body?.string().orEmpty(); val pattern=Regex("<a[^>]+class=\\\"result__a\\\"[^>]+href=\\\"([^\\\"]+)\\\"[^>]*>(.*?)</a>",RegexOption.IGNORE_CASE);pattern.findAll(html).take(max).mapIndexed{i,m->val raw=m.groupValues[1];val link=Regex("uddg=([^&]+)").find(raw)?.groupValues?.get(1)?.let{URLDecoder.decode(it,"UTF-8")}?:raw.replace("&amp;","&"); val title=m.groupValues[2].replace(Regex("<[^>]+>"),"").replace("&amp;","&");"${i+1}. $title\n$link"}.toList()} }
 private fun brave(query:String,max:Int,key:String):List<String>{ require(key.isNotBlank()){ "Add a Brave Search API key in Settings" }; val url="https://api.search.brave.com/res/v1/web/search".toHttpUrl().newBuilder().addQueryParameter("q",query).addQueryParameter("count",max.toString()).build(); val req=Request.Builder().url(url).header("X-Subscription-Token",key).build(); return webClient.newCall(req).execute().use{r->if(!r.isSuccessful)error("Brave Search HTTP ${r.code}"); val list=JsonParser.parseString(r.body?.string().orEmpty()).asJsonObject.getAsJsonObject("web")?.getAsJsonArray("results")?:return emptyList(); list.take(max).mapIndexed{i,e->val o=e.asJsonObject;"${i+1}. ${o.get("title")?.asString.orEmpty()}\n${o.get("url")?.asString.orEmpty()}\n${o.get("description")?.asString.orEmpty()}"}} }
 private fun tavily(query:String,max:Int,key:String):List<String>{ require(key.isNotBlank()){ "Add a Tavily API key in Settings" }; val json=com.google.gson.JsonObject().apply { addProperty("api_key",key); addProperty("query",query); addProperty("max_results",max); addProperty("search_depth","basic") }; val req=Request.Builder().url("https://api.tavily.com/search").post(json.toString().toRequestBody("application/json".toMediaType())).build(); return webClient.newCall(req).execute().use{r->if(!r.isSuccessful)error("Tavily Search HTTP ${r.code}"); val list=JsonParser.parseString(r.body?.string().orEmpty()).asJsonObject.getAsJsonArray("results")?:return emptyList(); list.take(max).mapIndexed{i,e->val o=e.asJsonObject;"${i+1}. ${o.get("title")?.asString.orEmpty()}\n${o.get("url")?.asString.orEmpty()}\n${o.get("content")?.asString.orEmpty()}"}} }
 private fun googleCustom(query:String,max:Int,key:String,engine:String):List<String>{ require(key.isNotBlank()&&engine.isNotBlank()){ "Add a Google Custom Search API key and Search Engine ID in Settings" }; val url="https://www.googleapis.com/customsearch/v1".toHttpUrl().newBuilder().addQueryParameter("q",query).addQueryParameter("key",key).addQueryParameter("cx",engine).addQueryParameter("num",max.toString()).build(); return webClient.newCall(Request.Builder().url(url).build()).execute().use{r->if(!r.isSuccessful)error("Google Search HTTP ${r.code}"); val list=JsonParser.parseString(r.body?.string().orEmpty()).asJsonObject.getAsJsonArray("items")?:return emptyList(); list.take(max).mapIndexed{i,e->val o=e.asJsonObject;"${i+1}. ${o.get("title")?.asString.orEmpty()}\n${o.get("link")?.asString.orEmpty()}\n${o.get("snippet")?.asString.orEmpty()}"}} }
}

class DownloadFileTool:BaseTool(){
 override fun getName()="download_file";override fun getDisplayName()="Download File";override fun getDescriptionEN()="Download a file from a direct HTTPS URL into the phone Downloads/OctoBot folder. Never download executables unless the user explicitly requested that exact file.";override fun getDescriptionCN()=getDescriptionEN()
 override fun getParameters()=listOf(ToolParameter("url","string","Direct HTTPS file URL",true),ToolParameter("file_name","string","Optional safe file name",false))
 override fun execute(p:Map<String,Any>):ToolResult { return try{val raw=requireString(p,"url");if(!raw.startsWith("https://"))return ToolResult.error("Only HTTPS downloads are allowed");val parsed=raw.toHttpUrl();var name=optionalString(p,"file_name","").ifBlank{parsed.pathSegments.lastOrNull().orEmpty().ifBlank{"download_${System.currentTimeMillis()}"}};name=name.replace(Regex("[^A-Za-z0-9._ -]"),"_").take(120);val req=Request.Builder().url(parsed).header("User-Agent","OctoBot/1.0").build();webClient.newCall(req).execute().use{r->if(!r.isSuccessful)return ToolResult.error("Download HTTP ${r.code}");val body=r.body?:return ToolResult.error("Empty file");val c=ClawApplication.instance;if(Build.VERSION.SDK_INT>=29){val values=ContentValues().apply{put(MediaStore.Downloads.DISPLAY_NAME,name);put(MediaStore.Downloads.MIME_TYPE,body.contentType()?.toString()?:"application/octet-stream");put(MediaStore.Downloads.RELATIVE_PATH,"Download/OctoBot")};val uri=c.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)?:return ToolResult.error("Cannot create download");c.contentResolver.openOutputStream(uri)?.use{out->body.byteStream().use{it.copyTo(out)}}?:return ToolResult.error("Cannot write file")}else{@Suppress("DEPRECATION") val directory=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).apply{mkdirs()};java.io.File(directory,name).outputStream().use{out->body.byteStream().use{it.copyTo(out)}}};ToolResult.success("Downloaded $name (${body.contentLength()} bytes) to Downloads/OctoBot")}}catch(e:Exception){ToolResult.error("Download failed: ${e.message}")} }
}
