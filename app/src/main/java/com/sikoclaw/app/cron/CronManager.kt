package com.sikoclaw.app.cron

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sikoclaw.app.ClawApplication
import com.sikoclaw.app.tool.*
import com.sikoclaw.app.ui.chat.ComposeChatActivity
import com.sikoclaw.app.utils.KVUtils
import java.util.Calendar

data class CronJob(val id:String,val name:String,val expression:String,val prompt:String,val enabled:Boolean=true,val nextRun:Long=0)
object CronManager{
 private const val KEY="CRON_JOBS_JSON";private val gson=Gson()
 fun all():MutableList<CronJob> = try{gson.fromJson(KVUtils.getString(KEY,"[]"),object:TypeToken<MutableList<CronJob>>(){}.type)?: mutableListOf()}catch(_:Exception){mutableListOf()}
 fun save(x:List<CronJob>)=KVUtils.putString(KEY,gson.toJson(x))
 fun upsert(context:Context,job:CronJob){val x=all();x.removeAll{it.id==job.id};val j=job.copy(nextRun=if(job.enabled)next(job.expression)else 0);x.add(j);save(x);if(j.enabled)schedule(context,j)else cancel(context,j.id)}
 fun delete(context:Context,id:String){cancel(context,id);save(all().filterNot{it.id==id})}
 fun restore(context:Context)=all().filter{it.enabled}.forEach{schedule(context,it.copy(nextRun=next(it.expression)))}
 fun fired(context:Context,id:String){
  val j=all().firstOrNull{it.id==id&&it.enabled}?:return
  if(com.sikoclaw.app.heartbeat.HeartbeatManager.isHeartbeatJob(id)&&!com.sikoclaw.app.heartbeat.HeartbeatManager.shouldRunNow()){
   com.sikoclaw.app.heartbeat.HeartbeatManager.markRun("Skipped outside active hours")
   upsert(context,j)
   return
  }
  if(com.sikoclaw.app.heartbeat.HeartbeatManager.isHeartbeatJob(id)) com.sikoclaw.app.heartbeat.HeartbeatManager.markRun("Started")
  context.startActivity(Intent(context,ComposeChatActivity::class.java).putExtra("task",j.prompt).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));upsert(context,j)
 }
 private fun pending(c:Context,id:String)=PendingIntent.getBroadcast(c,id.hashCode(),Intent(c,CronReceiver::class.java).putExtra("id",id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
 private fun schedule(c:Context,j:CronJob){c.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,if(j.nextRun>System.currentTimeMillis())j.nextRun else next(j.expression),pending(c,j.id))}
 private fun cancel(c:Context,id:String)=c.getSystemService(AlarmManager::class.java).cancel(pending(c,id))
 fun next(expression:String,from:Long=System.currentTimeMillis()):Long{val p=expression.trim().split(Regex("\\s+"));require(p.size==5){"Use 5 fields: minute hour day month weekday"};val c=Calendar.getInstance().apply{timeInMillis=from;set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0);add(Calendar.MINUTE,1)};repeat(525600){if(match(p[0],c.get(Calendar.MINUTE))&&match(p[1],c.get(Calendar.HOUR_OF_DAY))&&match(p[2],c.get(Calendar.DAY_OF_MONTH))&&match(p[3],c.get(Calendar.MONTH)+1)&&match(p[4],c.get(Calendar.DAY_OF_WEEK)-1))return c.timeInMillis;c.add(Calendar.MINUTE,1)};error("No run time in next year")}
 private fun match(f:String,v:Int)=when{f=="*"->true;f.startsWith("*/")->v%(f.substring(2).toIntOrNull()?:1)==0;f.contains(",")->f.split(",").any{it.toIntOrNull()==v};else->f.toIntOrNull()==v}
}
class CronReceiver:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){CronManager.fired(c,i.getStringExtra("id")?:return)}}
class CronTool:BaseTool(){
 override fun getName()="manage_cron_jobs";override fun getDisplayName()="Manage Cron Jobs";override fun getDescriptionEN()="List, create, update, enable, disable, or delete scheduled agent tasks using five-field cron expressions.";override fun getDescriptionCN()=getDescriptionEN()
 override fun getParameters()=listOf(ToolParameter("action","string","list, upsert, or delete",true),ToolParameter("id","string","Job id",false),ToolParameter("name","string","Job name",false),ToolParameter("cron","string","minute hour day month weekday",false),ToolParameter("prompt","string","Task prompt",false),ToolParameter("enabled","boolean","Enabled",false))
 override fun execute(p:Map<String,Any>):ToolResult=try{when(requireString(p,"action").lowercase()){"list"->ToolResult.success(Gson().toJson(CronManager.all()));"delete"->{CronManager.delete(ClawApplication.instance,requireString(p,"id"));ToolResult.success("Deleted")};else->{val n=requireString(p,"name");val cron=requireString(p,"cron");CronManager.next(cron);CronManager.upsert(ClawApplication.instance,CronJob(optionalString(p,"id","").ifBlank{"c${System.currentTimeMillis()}"},n,cron,requireString(p,"prompt"),optionalBoolean(p,"enabled",true)));ToolResult.success("Cron job '$n' saved")}}}catch(e:Exception){ToolResult.error(e.message?:"Invalid cron job")}
}
