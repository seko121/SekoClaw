package com.sikoclaw.app.ui.chat

data class ParsedAssistantStream(val reasoning: String, val visible: String)

/** Separates provider reasoning wrappers from user-visible content while tokens arrive. */
object AssistantStreamParser {
    private val closedReasoning = Regex("<(think|analysis|reasoning)>[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE)
    private val reasoningBody = Regex("<(think|analysis|reasoning)>([\\s\\S]*?)(?:</\\1>|$)", RegexOption.IGNORE_CASE)

    fun parse(raw: String): ParsedAssistantStream {
        val thoughts = reasoningBody.findAll(raw).map { it.groupValues[2] }.joinToString("\n").trim()
        var visible = closedReasoning.replace(raw, "")
        val open = Regex("<(think|analysis|reasoning)>[\\s\\S]*$", RegexOption.IGNORE_CASE).find(visible)
        if (open != null) visible = visible.substring(0, open.range.first)
        return ParsedAssistantStream(thoughts, finalAssistantText(visible).trim())
    }
}
