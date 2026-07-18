// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package com.sikoclaw.app

import com.sikoclaw.app.agent.DefaultAgentService
import com.sikoclaw.app.agent.llm.LocalBackendHealth
import com.sikoclaw.app.base.BaseApp
import com.sikoclaw.app.channel.ChannelManager
import com.sikoclaw.app.tool.ToolRegistry
import com.sikoclaw.app.utils.AppLogStore
import com.sikoclaw.app.utils.KVUtils
import com.sikoclaw.app.utils.XLog
import com.blankj.utilcode.util.NetworkUtils

/**
 * Application entry point
 */

val appViewModel: AppViewModel by lazy { ClawApplication.appViewModelInstance }
class ClawApplication : BaseApp() {

    companion object {
        private const val TAG = "ClawApplication"
        lateinit var instance: ClawApplication
            private set
        lateinit var appViewModelInstance: AppViewModel
    }

    override fun onCreate() {
        super.onCreate()
        AppCapabilityCoordinator.markProcessStart()
        instance = this
        AppLogStore.init(this)
        XLog.setDEBUG(BuildConfig.DEBUG)
        registerNetworkCallback()
        appViewModelInstance = getAppViewModelProvider()[AppViewModel::class.java]
        KVUtils.init(this)
        LocalBackendHealth.recoverPendingGpuCrashIfNeeded()
        ToolRegistry.getInstance().registerAllTools(ToolRegistry.DeviceType.MOBILE)
        com.sikoclaw.app.agent.skill.SkillRegistry.loadBuiltInSkills()
        com.sikoclaw.app.agent.skill.UserSkillStore.ensureDefaults()
        com.sikoclaw.app.cron.CronManager.restore(this)
        com.sikoclaw.app.agent.PlaybookManager.loadAll(this)
        XLog.e(TAG, "ClawApplication initialized, tools registered: ${ToolRegistry.getInstance().getAllTools().size}")

        // Write network logs to file (set to true when debugging)
        DefaultAgentService.FILE_LOGGING_ENABLED = BuildConfig.DEBUG
        DefaultAgentService.FILE_LOGGING_CACHE_DIR = cacheDir

        // Lightweight initialization (main thread)
        appViewModelInstance.initCommon()
        Thread({
            try {
                android.util.Log.e("SIKOCLAW_INIT", "app-async-init thread STARTED")
                com.sikoclaw.app.tool.terminal.InternalTerminal.bootstrapBundled()
                val mcpTools = com.sikoclaw.app.mcp.McpManager.connectEnabled()
                XLog.i(TAG, "Loaded $mcpTools MCP tools")
                val hasConfig = KVUtils.hasLlmConfig()
                android.util.Log.e("SIKOCLAW_INIT", "app-async-init: hasLlmConfig=$hasConfig, canDrawOverlays=${android.provider.Settings.canDrawOverlays(instance)}")
                if (hasConfig) {
                    appViewModelInstance.initAgent()
                    appViewModelInstance.afterInit()
                }
            } catch (e: Exception) {
                android.util.Log.e("SIKOCLAW_INIT", "app-async-init CRASHED: ${e.message}", e)
            }
        }, "app-async-init").start()
    }

    private var networkListener: NetworkUtils.OnNetworkStatusChangedListener? = null

    /**
     * Listen for network recovery and automatically re-initialize channels.
     * Fixes channel initialization failures when booting with no network, and reconnects channels after network outages.
     */
    private fun registerNetworkCallback() {
        networkListener = object : NetworkUtils.OnNetworkStatusChangedListener {
            override fun onConnected(networkType: NetworkUtils.NetworkType?) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (KVUtils.hasLlmConfig()) {
                        XLog.i(TAG, "Network recovered (${networkType?.name}), checking and reconnecting dropped channels")
                        ChannelManager.reconnectIfNeeded()
                    }
                }, 2000)
            }

            override fun onDisconnected() {
                XLog.w(TAG, "Network disconnected")
            }
        }
        NetworkUtils.registerNetworkStatusChangedListener(networkListener)
    }

}
