package com.sikoclaw.app.voice

import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Reads Arabic and Latin text in the same reply without forcing the entire reply
 * through the device's current locale.  This remains Android TTS; it gracefully
 * falls back when a user has not installed an Arabic voice on their device.
 */
internal object PolyglotSpeech {
    private data class Part(val text: String, val locale: Locale)

    fun speak(tts: TextToSpeech, text: String, requestedLanguage: String?, utterancePrefix: String, done: () -> Unit) {
        val parts = split(text, requestedLanguage)
        if (parts.isEmpty()) { done(); return }
        parts.forEachIndexed { index, part ->
            tts.language = usableLocale(tts, part.locale)
            tts.speak(
                part.text,
                if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                Bundle(),
                "$utterancePrefix-$index-${parts.lastIndex}",
            )
        }
    }

    /** The listener can reliably identify the final queued utterance. */
    fun isFinalUtterance(id: String?): Boolean = id?.substringAfterLast('-')?.toIntOrNull() ==
        id?.split('-')?.getOrNull(id.split('-').size - 2)?.toIntOrNull()

    private fun split(text: String, requestedLanguage: String?): List<Part> {
        val forced = requestedLanguage?.takeIf { it.isNotBlank() }?.let(Locale::forLanguageTag)
        if (forced != null) return listOf(Part(text, forced))
        val pieces = Regex("(?<=[.!?؟،;:\\n])|(?=[.!?؟،;:\\n])").split(text)
        return pieces.mapNotNull { raw ->
            val value = raw.trim()
            if (value.isBlank()) null else Part(value, if (value.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' }) Locale("ar", "EG") else Locale.US)
        }
    }

    private fun usableLocale(tts: TextToSpeech, wanted: Locale): Locale {
        val result = tts.isLanguageAvailable(wanted)
        return if (result >= TextToSpeech.LANG_AVAILABLE) wanted else Locale.US
    }
}
