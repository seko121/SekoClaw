package com.sikoclaw.app.plugin

import com.sikoclaw.app.utils.KVUtils

data class SikoPlugin(val id: String, val name: String, val description: String)

object PluginCatalog {
    private val bundled = listOf(
        SikoPlugin("browser", "Browser", "Browse visibly with the internal Firefox engine."),
        SikoPlugin("phone_control", "Phone control", "Allow approved taps, gestures and text input."),
        SikoPlugin("web_search", "Web search", "Search the web and download files in the background."),
        SikoPlugin("image_generation", "AI image generator", "Create an image from a written description and save it to the phone."),
        SikoPlugin("tts", "Text to speech", "Generate a voice or audio file and send it in the conversation."),
        SikoPlugin("document_studio", "Document Studio", "Create, read and edit PDF and Office documents."),
    )
    fun all(): List<SikoPlugin> = bundled
    fun isEnabled(id: String) = KVUtils.isPluginEnabled(id)
    fun setEnabled(id: String, enabled: Boolean) = KVUtils.setPluginEnabled(id, enabled)
}
