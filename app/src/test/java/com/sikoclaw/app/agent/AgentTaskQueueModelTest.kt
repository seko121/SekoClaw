package com.sikoclaw.app.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentTaskQueueModelTest {
    @Test fun queuedTaskKeepsPromptAndIdentity() {
        val task = QueuedAgentTask(prompt = "next task")
        assertEquals("next task", task.prompt)
        assert(task.id.isNotBlank())
    }
}
