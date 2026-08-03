package com.sikoclaw.app.ui.chat

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.ImageView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide

@Composable
fun MediaAttachmentPreview(
    attachment: ChatAttachment,
    colors: SikoClawColors,
    onOpen: () -> Unit,
) {
    if (!attachment.mimeType.startsWith("image/") && !attachment.mimeType.startsWith("video/")) return
    val context = LocalContext.current
    val uri = remember(attachment.uri) { Uri.parse(attachment.uri) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 220.dp)
            .clickable(onClick = onOpen),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
            update = { Glide.with(it).load(uri).into(it) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 220.dp),
        )
        if (attachment.mimeType.startsWith("video/")) {
            Surface(color = colors.background.copy(alpha = 0.72f), shape = androidx.compose.foundation.shape.CircleShape) {
                Icon(Icons.Filled.PlayArrow, "Play video", tint = colors.accent, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

fun mediaDurationMillis(context: android.content.Context, attachment: ChatAttachment): Long =
    runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(attachment.uri))
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }
    }.getOrDefault(0L)
