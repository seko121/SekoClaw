package com.sikoclaw.app.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color as AndroidColor
import android.text.method.LinkMovementMethod
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin

private sealed interface MarkdownPart {
    data class Prose(val value: String) : MarkdownPart
    data class Code(val language: String, val value: String) : MarkdownPart
}

private fun markdownParts(source: String): List<MarkdownPart> {
    val result = mutableListOf<MarkdownPart>()
    val fence = Regex("```([^\\n`]*)\\n([\\s\\S]*?)(?:```|$)")
    var cursor = 0
    fence.findAll(source).forEach { match ->
        if (match.range.first > cursor) result += MarkdownPart.Prose(source.substring(cursor, match.range.first))
        result += MarkdownPart.Code(match.groupValues[1].trim(), match.groupValues[2].trimEnd())
        cursor = match.range.last + 1
    }
    if (cursor < source.length) result += MarkdownPart.Prose(source.substring(cursor))
    return result.ifEmpty { listOf(MarkdownPart.Prose(source)) }
}

@Composable
fun MarkdownMessage(source: String, colors: SikoClawColors) {
    val context = LocalContext.current
    val markwon = remember { Markwon.builder(context).usePlugin(StrikethroughPlugin.create()).build() }
    val parts = remember(source) { markdownParts(source) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parts.forEach { part ->
            when (part) {
                is MarkdownPart.Prose -> if (part.value.isNotBlank()) AndroidView(
                    factory = { ctx -> TextView(ctx).apply {
                        setTextColor(colors.aiText.toArgbCompat())
                        textSize = 15f
                        setLineSpacing(0f, 1.18f)
                        movementMethod = LinkMovementMethod.getInstance()
                        setLinkTextColor(colors.accent.toArgbCompat())
                    } },
                    update = { markwon.setMarkdown(it, part.value.trim()) },
                    modifier = Modifier.fillMaxWidth(),
                )
                is MarkdownPart.Code -> Column(Modifier.fillMaxWidth().background(Color(0xFF050A12), RoundedCornerShape(10.dp)).padding(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(part.language.ifBlank { "code" }, color = colors.textTertiary, fontSize = 11.sp)
                        IconButton(onClick = {
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("code", part.value))
                            Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                        }, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.ContentCopy, "Copy code", tint = colors.accent, modifier = Modifier.size(16.dp)) }
                    }
                    Text(part.value, color = Color(0xFFE6EDF7), fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.horizontalScroll(rememberScrollState()).padding(4.dp))
                }
            }
        }
    }
}

private fun Color.toArgbCompat(): Int = AndroidColor.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())
