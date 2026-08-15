package com.sikoclaw.app.voice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Minimize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sikoclaw.app.floating.SharedChatBus
import com.sikoclaw.app.ui.chat.ChatMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** A full-screen, hands-free session that deliberately ignores transient chat placeholders. */
class VoiceCallActivity : ComponentActivity() {
    private lateinit var stt: SpeechToTextProvider
    private lateinit var tts: SpeechOutputProvider
    private var collecting: Job? = null
    private var lastAssistantTimestamp = 0L
    private var listening = false
    /** Cancels delayed recognizer restarts as well as the visible activity. */
    private var callEnded = false
    private var retryRunnable: Runnable? = null
    private var callState by mutableStateOf(VoiceLoopState.LISTENING)
    private var transcript by mutableStateOf("Say something when you're ready")
    private var languageTag by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VoiceSessionCoordinator.attach(this)
        setContent { VoiceCallScreen(callState, transcript, languageTag, { tag ->
            languageTag = tag
            if (::stt.isInitialized) { stt.stop(); listening = false; listen() }
        }, { minimizeToBubble() }, onEnd = { endCall() }) }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
        } else begin()
    }

    private fun begin() {
        if (callEnded) return
        stt = AndroidSpeechToTextProvider(this)
        tts = AndroidSpeechOutputProvider(this)
        observeAssistantReplies()
        listen()
    }

    private fun listen() {
        if (callEnded || isFinishing || listening) return
        listening = true
        callState = VoiceLoopState.LISTENING
        VoiceSessionCoordinator.update(callState)
        transcript = "Listening…"
        stt.start(object : SpeechListener {
            override fun onSpeechStarted() {
                if (callEnded) return
                tts.stop(); callState = VoiceLoopState.LISTENING
                VoiceSessionCoordinator.update(callState)
                transcript = "Listening…"
            }
            override fun onFinalText(text: String) {
                if (callEnded) return
                listening = false
                lastAssistantTimestamp = System.currentTimeMillis()
                callState = VoiceLoopState.THINKING
                VoiceSessionCoordinator.update(callState)
                transcript = "You: $text"
                val delivered = SharedChatBus.sendTask(text)
                if (!delivered) {
                    callState = VoiceLoopState.ERROR
                    transcript = "Open OctoBot first, then start a voice call."
                }
            }
            override fun onError(message: String) {
                listening = false
                if (!callEnded && !isFinishing) statusRetry()
            }
        }, languageTag)
    }

    private fun statusRetry() {
        retryRunnable?.let { window.decorView.removeCallbacks(it) }
        callState = VoiceLoopState.LISTENING
        VoiceSessionCoordinator.update(callState)
        transcript = "Listening…"
        retryRunnable = Runnable { if (!callEnded) listen() }.also { window.decorView.postDelayed(it, 650) }
    }

    private fun observeAssistantReplies() {
        collecting = lifecycleScope.launch {
            SharedChatBus.messages.collectLatest { messages ->
                val reply = messages.lastOrNull { message ->
                    message.role == ChatMessage.Role.ASSISTANT &&
                        !message.isStreaming &&
                        message.timestamp >= lastAssistantTimestamp &&
                        message.content.cleanForSpeech().isNotBlank()
                } ?: return@collectLatest
                val speech = reply.content.cleanForSpeech()
                // Mark consumed before playback so recompositions cannot replay a response.
                lastAssistantTimestamp = Long.MAX_VALUE
                listening = false
                callState = VoiceLoopState.SPEAKING
                VoiceSessionCoordinator.update(callState)
                transcript = speech
                tts.speak(speech, {
                    runOnUiThread {
                        lastAssistantTimestamp = System.currentTimeMillis()
                        listen()
                    }
                }, languageTag)
            }
        }
    }

    private fun String.cleanForSpeech(): String = trim().let { text ->
        if (text == "..." || text == "…" || text.matches(Regex("^[.\\s]+$"))) "" else text
    }

    private fun minimizeToBubble() {
        // Keeps the conversation reachable outside OctoBot. The foreground overlay
        // is intentionally user-visible, so Android does not silently hide the session.
        runCatching { androidx.core.content.ContextCompat.startForegroundService(this, android.content.Intent(this, com.sikoclaw.app.floating.FloatingAssistantService::class.java)) }
        moveTaskToBack(true)
    }

    fun closeFromCoordinator() { runOnUiThread { endCall() } }

    private fun endCall() {
        if (callEnded) return
        callEnded = true
        listening = false
        retryRunnable?.let { window.decorView.removeCallbacks(it) }
        retryRunnable = null
        collecting?.cancel()
        if (::stt.isInitialized) stt.stop()
        if (::tts.isInitialized) tts.stop()
        VoiceSessionCoordinator.detach(this)
        finish()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MIC && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) begin()
        else { callState = VoiceLoopState.ERROR; transcript = "Microphone permission is required for voice calls." }
    }

    override fun onDestroy() {
        callEnded = true
        retryRunnable?.let { window.decorView.removeCallbacks(it) }
        collecting?.cancel()
        if (::stt.isInitialized) stt.destroy()
        if (::tts.isInitialized) tts.destroy()
        VoiceSessionCoordinator.detach(this)
        super.onDestroy()
    }
    private companion object { const val REQUEST_MIC = 71 }
}

@androidx.compose.runtime.Composable
private fun VoiceCallScreen(
    state: VoiceLoopState, text: String, languageTag: String?,
    onLanguage: (String?) -> Unit, onMinimize: () -> Unit, onEnd: () -> Unit,
) {
    val infinite = rememberInfiniteTransition(label = "voicePulse")
    val active = state == VoiceLoopState.LISTENING || state == VoiceLoopState.SPEAKING || state == VoiceLoopState.THINKING
    val pulse by infinite.animateFloat(1f, if (active) 1.13f else 1f, infiniteRepeatable(androidx.compose.animation.core.tween(920, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val (label, icon, tone) = when (state) {
        VoiceLoopState.LISTENING -> Triple("Listening", Icons.Outlined.GraphicEq, Color(0xFF38D9FF))
        VoiceLoopState.THINKING, VoiceLoopState.WORKING -> Triple("OctoBot is thinking", Icons.Outlined.Psychology, Color(0xFF8DA4FF))
        VoiceLoopState.SPEAKING -> Triple("OctoBot is speaking", Icons.Outlined.RecordVoiceOver, Color(0xFF57E6B1))
        VoiceLoopState.ERROR -> Triple("Voice session needs attention", Icons.Outlined.RecordVoiceOver, Color(0xFFFF8A8A))
        else -> Triple("Preparing", Icons.Outlined.GraphicEq, Color(0xFF38D9FF))
    }
    Surface(color = Color(0xFF051431), modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.foundation.layout.Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.End) {
                androidx.compose.material3.IconButton(onClick = onMinimize) { Icon(Icons.Outlined.Minimize, "Minimize call", tint = Color(0xFFC6D9F2)) }
            }
            Text("OctoBot", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Hands-free conversation", color = Color(0xFF9DB5D4), fontSize = 14.sp)
            androidx.compose.foundation.layout.Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(null to "Auto", "ar-EG" to "العربية", "en-US" to "English").forEach { (tag, title) ->
                    FilterChip(selected = languageTag == tag, onClick = { onLanguage(tag) }, label = { Text(title) })
                }
            }
            Text("Agent tools are enabled for every voice request", color = Color(0xFF57E6B1), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(54.dp))
            Box(Modifier.size(218.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(210.dp).scale(pulse).background(tone.copy(alpha = .14f), CircleShape))
                Surface(modifier = Modifier.size(154.dp), shape = CircleShape, color = Color(0xFF0B2B58), shadowElevation = 8.dp) {
                    Box(contentAlignment = Alignment.Center) { Icon(icon, label, tint = tone, modifier = Modifier.size(62.dp)) }
                }
            }
            Spacer(Modifier.height(26.dp))
            Text(label, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Surface(color = Color(0xFF0C2447), shape = RoundedCornerShape(20.dp), modifier = Modifier.weight(1f, fill = false)) {
                Text(text, color = Color(0xFFC6D9F2), fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(20.dp))
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onEnd, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD94B5D)), modifier = Modifier.width(176.dp).height(54.dp)) {
                Icon(Icons.Outlined.CallEnd, "End call")
                Spacer(Modifier.width(9.dp)); Text("End call")
            }
        }
    }
}
