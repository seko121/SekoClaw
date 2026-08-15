// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package com.sikoclaw.app

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
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
        KVUtils.init(this)
        com.sikoclaw.app.agent.llm.MultiProviderStore.migrateLegacyIfNeeded()
        com.sikoclaw.app.agent.llm.MultiProviderStore.ensureBuiltInFreeModel()
        // Model-backed ViewModels must be created only after both MMKV and the
        // built-in route are ready. Creating them earlier cached the legacy
        // LOCAL/empty selection and produced a false "No model selected" state.
        appViewModelInstance = getAppViewModelProvider()[AppViewModel::class.java]
        registerFloatingAssistantVisibility()
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

    private fun registerFloatingAssistantVisibility() {
        var started = 0
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                started++
                com.sikoclaw.app.floating.FloatingAssistantManager.setAppForeground(true)
            }
            override fun onActivityStopped(activity: Activity) {
                started = (started - 1).coerceAtLeast(0)
                android.os.Handler(mainLooper).postDelayed({
                    if (started == 0 && com.sikoclaw.app.floating.FloatingAssistantConfig.enabled() && android.provider.Settings.canDrawOverlays(this@ClawApplication)) {
                        com.sikoclaw.app.floating.FloatingAssistantManager.setAppForeground(false)
                        androidx.core.content.ContextCompat.startForegroundService(this@ClawApplication, Intent(this@ClawApplication, com.sikoclaw.app.floating.FloatingAssistantService::class.java))
                    }
                }, 250)
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
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
