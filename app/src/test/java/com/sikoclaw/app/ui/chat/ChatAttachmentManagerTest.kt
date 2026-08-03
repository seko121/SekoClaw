package com.sikoclaw.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatAttachmentManagerTest {
    @Test fun `media and document MIME types are stable`() {
        assertEquals("image/webp", ChatAttachmentManager.mimeFromName("preview.webp"))
        assertEquals("video/x-matroska", ChatAttachmentManager.mimeFromName("clip.mkv"))
        assertEquals("audio/mp4", ChatAttachmentManager.mimeFromName("voice.m4a"))
        assertEquals("application/pdf", ChatAttachmentManager.mimeFromName("report.pdf"))
    }
}
