package com.sikoclaw.app.agent.memory

import org.junit.Assert.*
import org.junit.Test

class ExplicitMemoryCaptureTest {
    @Test fun capturesExplicitArabicMemory() = assertEquals("أنا أفضل الردود المختصرة", ExplicitMemoryCapture.extract("احفظ: أنا أفضل الردود المختصرة"))
    @Test fun ignoresOrdinaryTemporaryMessage() = assertNull(ExplicitMemoryCapture.extract("ما حالة الطقس اليوم؟"))
    @Test fun neverCapturesSecrets() = assertNull(ExplicitMemoryCapture.extract("remember this: my API key is sk-private"))
}
