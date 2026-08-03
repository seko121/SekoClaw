package com.sikoclaw.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

object MessageSpeech {
    fun speak(context: Context, text: String) {
        val clean = text.replace(Regex("```[\\s\\S]*?```"), "").replace(Regex("[*#_>`]"), " ").trim()
        if (clean.isBlank()) return
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val parts = Regex("(?<=[.!?؟،;:\\n])|(?=[.!?؟،;:\\n])").split(clean).map { it.trim() }.filter { it.isNotBlank() }
                val lastId = "octobot_message-${(parts.size - 1).coerceAtLeast(0)}"
                engine?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) { if (utteranceId == lastId) { engine?.shutdown(); engine = null } }
                    override fun onError(utteranceId: String?) { if (utteranceId == lastId) { engine?.shutdown(); engine = null } }
                })
                parts.forEachIndexed { index, part ->
                    val locale = if (part.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' }) Locale("ar", "EG") else Locale.US
                    val availability = engine?.isLanguageAvailable(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
                    if (availability >= TextToSpeech.LANG_AVAILABLE) engine?.language = locale
                    engine?.speak(part, if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "octobot_message-$index")
                }
            } else {
                engine?.shutdown(); engine = null
            }
        }
    }
}
