package com.sikoclaw.app.agent.skill

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sikoclaw.app.utils.KVUtils

data class UserSkill(val id: String, val name: String, val description: String, val instructions: String, val triggers: String, val enabled: Boolean = true)

object UserSkillStore {
    private const val KEY = "USER_SKILLS_JSON"
    private val gson = Gson()
    fun all(): MutableList<UserSkill> = try {
        gson.fromJson(KVUtils.getString(KEY, "[]"), object : TypeToken<MutableList<UserSkill>>() {}.type) ?: mutableListOf()
    } catch (_: Exception) { mutableListOf() }
    fun save(items: List<UserSkill>) = KVUtils.putString(KEY, gson.toJson(items))
    fun upsert(skill: UserSkill) { val list=all(); list.removeAll { it.id==skill.id }; list.add(skill); save(list) }
    fun delete(id: String) { save(all().filterNot { it.id == id }) }
    fun promptBlock(): String {
        val enabled = all().filter { it.enabled }
        if (enabled.isEmpty()) return ""
        return buildString {
            append("USER SKILLS (follow when relevant):\n")
            enabled.forEach { append("- ${it.name}; triggers: ${it.triggers}; ${it.instructions}\n") }
            append("\n")
        }
    }
    fun loadIntoRegistry() { /* User skills are prompt skills, not unsafe blind deterministic macros. */ }
    fun ensureDefaults() {
        if (KVUtils.getBoolean("DEFAULT_AGENT_SKILLS_V1", false)) { ensureMoreDefaults(); return }
        val existing = all()
        val defaults = listOf(
            UserSkill("deep_web_research", "Deep Web Research", "Research current topics from multiple sources", "Plan focused searches, use web_search repeatedly, open the strongest sources, compare dates and claims, then answer with source URLs. Never invent a citation.", "research, search the web, latest, verify"),
            UserSkill("file_analyst", "File Analyst", "Inspect user-provided documents and text files", "Read every attached file that is available, distinguish file facts from assumptions, summarize first, then answer the user's specific question. Mention unreadable formats honestly.", "analyze file, PDF, document, attachment"),
            UserSkill("safe_download", "Safe Download", "Find and download requested files safely", "Confirm the exact requested artifact, prefer official HTTPS sources, use download_file, verify the result and report its saved location. Never download an executable unless explicitly requested.", "download, save file, get file"),
            UserSkill("learn_workflow", "Learn Reusable Workflow", "Turn a successfully repeated workflow into a reusable skill", "After a multi-step workflow succeeds and is likely to be reused, capture only the stable procedure with create_skill. Do not store secrets, personal content, or one-time values in a skill.", "remember workflow, make a skill, do this next time")
        )
        defaults.filter { d -> existing.none { it.id == d.id } }.forEach(existing::add)
        save(existing)
        KVUtils.putBoolean("DEFAULT_AGENT_SKILLS_V1", true)
        ensureMoreDefaults()
    }
    private fun ensureMoreDefaults() {
        if (KVUtils.getBoolean("DEFAULT_AGENT_SKILLS_V2", false)) return
        val items=all()
        val more=listOf(
            UserSkill("terminal_developer", "Terminal Developer", "Use Alpine Linux to inspect projects and run development commands", "Inspect the working directory first, use Alpine Linux, install only required packages with apk, make scoped changes, and run a relevant verification command before reporting completion.", "terminal, linux, code, build, git"),
            UserSkill("scheduled_automation", "Scheduled Automation", "Create and maintain recurring agent jobs", "Clarify the intended schedule, create or edit the cron job, validate the expression, show the next run time, and never delete or disable an existing job without confirmation.", "schedule, every day, cron, recurring"),
            UserSkill("mcp_connector", "MCP Connector", "Connect an MCP server and safely use its discovered tools", "Validate the HTTPS endpoint, connect and list discovered tools, explain what became available, then call only the minimum MCP tool required. Never expose bearer tokens.", "MCP, connect server, add integration"),
            UserSkill("systematic_troubleshooting", "Systematic Troubleshooting", "Diagnose failures using evidence before changing things", "Reproduce or inspect the error, collect logs and environment facts, form the smallest likely hypothesis, test it, then apply a scoped fix and verify the original flow.", "error, broken, not working, diagnose, fix")
        )
        more.filter{d->items.none{it.id==d.id}}.forEach(items::add);save(items);KVUtils.putBoolean("DEFAULT_AGENT_SKILLS_V2",true)
    }
}

class CreateSkillTool : com.sikoclaw.app.tool.BaseTool() {
    override fun getName() = "create_skill"
    override fun getDisplayName() = "Create Skill"
    override fun getDescriptionEN() = "Create or update a reusable skill from a workflow learned in chat."
    override fun getDescriptionCN() = getDescriptionEN()
    override fun getParameters() = listOf(
        com.sikoclaw.app.tool.ToolParameter("name", "string", "Short skill name", true),
        com.sikoclaw.app.tool.ToolParameter("description", "string", "What it does", true),
        com.sikoclaw.app.tool.ToolParameter("triggers", "string", "Comma-separated phrases that activate it", true),
        com.sikoclaw.app.tool.ToolParameter("instructions", "string", "Safe reusable instructions", true)
    )
    override fun execute(params: Map<String, Any>): com.sikoclaw.app.tool.ToolResult {
        val name=requireString(params,"name").trim(); if(name.isBlank()) return com.sikoclaw.app.tool.ToolResult.error("name required")
        val id=name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "skill_${System.currentTimeMillis()}" }
        UserSkillStore.upsert(UserSkill(id,name,requireString(params,"description"),requireString(params,"instructions"),requireString(params,"triggers")))
        return com.sikoclaw.app.tool.ToolResult.success("Skill '$name' saved and enabled")
    }
}
