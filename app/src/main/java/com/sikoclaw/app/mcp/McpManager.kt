package com.sikoclaw.app.mcp

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.sikoclaw.app.tool.BaseTool
import com.sikoclaw.app.tool.ToolParameter
import com.sikoclaw.app.tool.ToolRegistry
import com.sikoclaw.app.tool.ToolResult
import com.sikoclaw.app.utils.KVUtils
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class McpServer(val id:String, val name:String, val url:String, val token:String="", val enabled:Boolean=true, val headers:Map<String,String> = emptyMap(), val transport:String="HTTP")

object McpManager {
    private const val KEY="MCP_SERVERS_JSON"
    private val gson=Gson()
    private val client=OkHttpClient.Builder().connectTimeout(8,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS).build()
    fun all():MutableList<McpServer> = try { gson.fromJson(KVUtils.getString(KEY,"[]"),object:TypeToken<MutableList<McpServer>>(){}.type)?: mutableListOf() } catch(_:Exception){ mutableListOf() }
    fun save(items:List<McpServer>)=KVUtils.putString(KEY,gson.toJson(items))
    fun exportConfiguration():String=gson.toJson(all().map { it.copy(token="") })
    fun importConfiguration(json:String):Int { val parsed:List<McpServer> = gson.fromJson(json,object:TypeToken<List<McpServer>>(){}.type)?:emptyList();require(parsed.all{it.name.isNotBlank()&&(it.url.startsWith("https://")||it.url.startsWith("http://"))}){"Invalid MCP configuration"};val merged=(all()+parsed.map{it.copy(token="",enabled=false)}).distinctBy{it.id};save(merged);return parsed.size }
    fun upsert(server:McpServer){val x=all();x.removeAll{it.id==server.id};x.add(server);save(x);if(!server.enabled)ToolRegistry.unregisterPrefix("mcp_${server.id}_")}
    fun delete(id:String){ToolRegistry.unregisterPrefix("mcp_${id}_");save(all().filterNot{it.id==id})}
    fun connectEnabled():Int { var count=0; all().filter{it.enabled}.forEach { try { count+=connect(it) } catch(_:Exception){} }; return count }
    fun connect(server:McpServer):Int {
        call(server,"initialize",JsonObject().apply { addProperty("protocolVersion","2025-03-26"); add("capabilities",JsonObject()); add("clientInfo",JsonObject().apply{addProperty("name","OctoBot");addProperty("version","1.0")}) })
        val result=call(server,"tools/list",JsonObject()).getAsJsonObject("result")
        val tools=result?.getAsJsonArray("tools")?:return 0
        tools.forEach { item ->
            val o=item.asJsonObject; val original=o["name"].asString
            val schema=o.getAsJsonObject("inputSchema")?.getAsJsonObject("properties")
            val required=o.getAsJsonObject("inputSchema")?.getAsJsonArray("required")?.map{it.asString}?.toSet()?: emptySet()
            val params=schema?.entrySet()?.map { (n,v) ->
                val s=v.asJsonObject; ToolParameter(n,s["type"]?.asString?:"string",s["description"]?.asString?:n,n in required)
            }?: emptyList()
            ToolRegistry.register(McpProxyTool(server,original,o["description"]?.asString?:"MCP tool",params))
        }
        return tools.size()
    }
    internal fun invoke(server:McpServer,name:String,args:Map<String,Any>):ToolResult = try {
        val p=JsonObject().apply{addProperty("name",name);add("arguments",gson.toJsonTree(args))}
        val response=call(server,"tools/call",p)
        if(response.has("error")) ToolResult.error(response["error"].toString()) else ToolResult.success(response.get("result")?.toString()?:"OK")
    } catch(e:Exception){ToolResult.error("MCP ${server.name}: ${e.message}")}
    private fun call(server:McpServer,method:String,params:JsonObject):JsonObject {
        val body=JsonObject().apply{addProperty("jsonrpc","2.0");addProperty("id",System.nanoTime());addProperty("method",method);add("params",params)}
        val b=Request.Builder().url(server.url).post(gson.toJson(body).toRequestBody("application/json".toMediaType())).header("Accept","application/json, text/event-stream")
        if(server.token.isNotBlank()) b.header("Authorization","Bearer ${server.token}")
        server.headers.forEach { (name,value) -> if(name.isNotBlank()) b.header(name,value) }
        client.newCall(b.build()).execute().use { r -> if(!r.isSuccessful) error("HTTP ${r.code}"); val text=r.body?.string().orEmpty(); val json=text.lineSequence().firstOrNull{it.startsWith("data:")}?.removePrefix("data:")?.trim()?:text; return gson.fromJson(json,JsonObject::class.java) }
    }
}

private class McpProxyTool(private val server:McpServer,private val remoteName:String,private val desc:String,private val params:List<ToolParameter>):BaseTool(){
    override fun getName()="mcp_${server.id}_${remoteName}".replace(Regex("[^A-Za-z0-9_]"),"_").take(64)
    override fun getDisplayName()="${server.name}: $remoteName"
    override fun getDescriptionEN()=desc
    override fun getDescriptionCN()=desc
    override fun getParameters()=params
    override fun execute(params:Map<String,Any>)=McpManager.invoke(server,remoteName,params)
}
