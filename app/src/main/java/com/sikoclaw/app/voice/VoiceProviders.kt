package com.sikoclaw.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Provider boundaries keep the voice loop independent from any GPL implementation. */
interface SpeechToTextProvider { fun start(listener: SpeechListener, languageTag: String? = null); fun stop(); fun destroy() }
interface SpeechListener { fun onSpeechStarted(); fun onFinalText(text: String); fun onError(message: String) }
interface SpeechOutputProvider { fun speak(text: String, onDone: () -> Unit, languageTag: String? = null); fun stop(); fun destroy() }

class AndroidSpeechToTextProvider(context: Context) : SpeechToTextProvider {
    private val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    private var activeListener: SpeechListener? = null
    init {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() { activeListener?.onSpeechStarted() }
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onError(error: Int) { activeListener?.onError("Speech recognition error $error") }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) activeListener?.onFinalText(text) else activeListener?.onError("No speech detected")
            }
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
    }
    override fun start(listener: SpeechListener, languageTag: String?) {
        activeListener = listener
        recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // Explicit Arabic must be a full BCP-47 tag.  In Auto, Android 14+
            // recognizers may choose Arabic or English instead of inheriting a
            // device locale that is often English-only.
            val preferred = languageTag ?: Locale.getDefault().toLanguageTag()
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, preferred)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, preferred)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, preferred)
            if (languageTag == null) {
                putExtra("android.speech.extra.ENABLE_LANGUAGE_DETECTION", true)
                putStringArrayListExtra(
                    "android.speech.extra.LANGUAGE_DETECTION_ALLOWED_LANGUAGES",
                    arrayListOf("ar-EG", "ar-SA", "en-US", "en-GB"),
                )
            }
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        })
    }
    override fun stop() = recognizer.cancel()
    override fun destroy() = recognizer.destroy()
}

class AndroidSpeechOutputProvider(context: Context) : SpeechOutputProvider {
    private var done: (() -> Unit)? = null
    private var finalUtteranceId: String? = null
    private var ready = false
    private data class PendingSpeech(val text: String, val callback: () -> Unit, val languageTag: String?)
    private var pending: PendingSpeech? = null
    private val tts = TextToSpeech(context) { result ->
        ready = result == TextToSpeech.SUCCESS
        pending?.let { request -> pending = null; speak(request.text, request.callback, request.languageTag) }
    }
    init { tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit
        override fun onError(utteranceId: String?) { if (utteranceId == finalUtteranceId) done?.invoke() }
        override fun onDone(utteranceId: String?) { if (utteranceId == finalUtteranceId) done?.invoke() }
    }) }
    override fun speak(text: String, onDone: () -> Unit, languageTag: String?) {
        if (!ready) { pending = PendingSpeech(text, onDone, languageTag); return }
        done = onDone
        val parts = Regex("(?<=[.!?؟،;:\\n])|(?=[.!?؟،;:\\n])").split(text).map { it.trim() }.filter { it.isNotBlank() }
        finalUtteranceId = "octobot_reply-${(parts.size - 1).coerceAtLeast(0)}"
        if (parts.isEmpty()) { onDone(); return }
        parts.forEachIndexed { index, part ->
            val locale = languageTag?.let(Locale::forLanguageTag)
                ?: if (part.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' }) Locale("ar", "EG") else Locale.US
            if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) tts.language = locale
            tts.speak(part, if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, Bundle(), "octobot_reply-$index")
        }
    }
    override fun stop() { tts.stop() }
    override fun destroy() = tts.shutdown()
}
