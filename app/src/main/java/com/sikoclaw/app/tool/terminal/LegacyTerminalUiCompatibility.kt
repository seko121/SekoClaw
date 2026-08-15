package com.sikoclaw.app.tool.terminal

/** Kept only so older, unreachable Compose helpers continue to compile during the UI migration. */
enum class TerminalEnvironment { SIKO, ANDROID, ALPINE }
enum class SikoSetupState { NOT_INSTALLED, DOWNLOADING, VERIFYING, EXTRACTING, CONFIGURING, READY, FAILED }

object SikoEnvironment {
    fun setupState() = when (UbuntuRuntime.status.value.stage) {
        UbuntuSetupStage.NOT_INSTALLED, UbuntuSetupStage.WAITING_FOR_APPROVAL -> SikoSetupState.NOT_INSTALLED
        UbuntuSetupStage.DOWNLOADING -> SikoSetupState.DOWNLOADING
        UbuntuSetupStage.VERIFYING -> SikoSetupState.VERIFYING
        UbuntuSetupStage.EXTRACTING -> SikoSetupState.EXTRACTING
        UbuntuSetupStage.CONFIGURING, UbuntuSetupStage.HEALTH_CHECKING -> SikoSetupState.CONFIGURING
        UbuntuSetupStage.READY -> SikoSetupState.READY
        UbuntuSetupStage.FAILED -> SikoSetupState.FAILED
    }
    fun lastError() = UbuntuRuntime.status.value.message
    fun status() = UbuntuRuntime.status.value.message ?: setupState().name
    fun bootstrap(forceRetry: Boolean = false) = UbuntuRuntime.install().fold(
        { com.sikoclaw.app.tool.ToolResult.success("Ubuntu is ready") },
        { com.sikoclaw.app.tool.ToolResult.error(it.message ?: "Ubuntu setup failed") },
    )
}
