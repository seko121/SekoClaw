package com.sikoclaw.app.ui.artifact

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.sikoclaw.app.ui.chat.ThemeManager
import java.io.File

/** Isolated preview for user-created HTML artifacts. No file/content access or JS bridge is exposed. */
class ArtifactActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data ?: run { finish(); return }
        val html = runCatching { contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            ?: run { finish(); return }
        setContent {
            var webView by remember { mutableStateOf<WebView?>(null) }
            val colors = ThemeManager.getColors()
            MaterialTheme(colorScheme = if (ThemeManager.isDark()) darkColorScheme() else lightColorScheme()) {
                Scaffold(topBar = {
                    TopAppBar(
                        title = { Text("Interactive artifact") },
                        navigationIcon = { IconButton({ finish() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                        actions = { IconButton({ webView?.reload() }) { Icon(Icons.Outlined.Refresh, "Reload") } },
                    )
                }) { padding ->
                    AndroidView(
                        factory = { context -> WebView(context).apply {
                            webChromeClient = WebChromeClient()
                            settings.javaScriptEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.domStorageEnabled = false
                            settings.databaseEnabled = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            settings.setGeolocationEnabled(false)
                            if (android.os.Build.VERSION.SDK_INT >= 26) settings.safeBrowsingEnabled = true
                            loadDataWithBaseURL("https://artifact.sikoclaw.invalid/", html, "text/html", "UTF-8", null)
                            webView = this
                        } },
                        modifier = Modifier.fillMaxSize().padding(padding),
                    )
                }
            }
        }
    }
}
