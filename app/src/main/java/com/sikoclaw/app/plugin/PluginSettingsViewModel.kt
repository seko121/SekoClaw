package com.sikoclaw.app.plugin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sikoclaw.app.utils.KVUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PluginRuntimeState { ENABLED, DISABLED, STARTING, STOPPING, ERROR, PERMISSION_REQUIRED, NOT_INSTALLED, INSTALLING, READY }

data class PluginUiState(
    val plugin: SikoPlugin,
    val enabled: Boolean,
    val runtimeState: PluginRuntimeState,
    val detail: String? = null,
)

data class PluginSettingsState(
    val plugins: List<PluginUiState> = emptyList(),
    val haloEnabled: Boolean = true,
    val pointerEnabled: Boolean = true,
    val error: String? = null,
)

class PluginSettingsViewModel : ViewModel() {
    private val _state = MutableStateFlow(load())
    val state: StateFlow<PluginSettingsState> = _state.asStateFlow()

    fun togglePlugin(id: String, enabled: Boolean) {
        val previous = _state.value
        _state.value = previous.copy(plugins = previous.plugins.map {
            if (it.plugin.id == id) it.copy(enabled = enabled, runtimeState = if (enabled) PluginRuntimeState.STARTING else PluginRuntimeState.STOPPING, detail = null) else it
        }, error = null)
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { PluginCatalog.setEnabled(id, enabled) }
                _state.value = _state.value.copy(plugins = _state.value.plugins.map { item ->
                    if (item.plugin.id != id) item else item.copy(runtimeState = if (enabled) PluginRuntimeState.READY else PluginRuntimeState.DISABLED)
                })
            }.onFailure { error -> _state.value = previous.copy(error = "Could not save ${id}: ${error.message}") }
        }
    }

    fun setHalo(enabled: Boolean) = persistOption(enabled, true)
    fun setPointer(enabled: Boolean) = persistOption(enabled, false)
    fun clearError() { _state.value = _state.value.copy(error = null) }

    private fun persistOption(enabled: Boolean, halo: Boolean) {
        val previous = _state.value
        _state.value = if (halo) previous.copy(haloEnabled = enabled) else previous.copy(pointerEnabled = enabled)
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { if (halo) KVUtils.setAgentControlHaloEnabled(enabled) else KVUtils.setVirtualPointerEnabled(enabled) } }
                .onFailure { _state.value = previous.copy(error = "Could not save setting: ${it.message}") }
        }
    }

    private fun load(): PluginSettingsState = PluginSettingsState(
        plugins = PluginCatalog.all().map { plugin ->
            val enabled = PluginCatalog.isEnabled(plugin.id)
            PluginUiState(plugin, enabled, if (plugin.id == "terminal" && enabled) PluginRuntimeState.READY else if (enabled) PluginRuntimeState.ENABLED else PluginRuntimeState.DISABLED)
        },
        haloEnabled = KVUtils.isAgentControlHaloEnabled(),
        pointerEnabled = KVUtils.isVirtualPointerEnabled(),
    )

}
