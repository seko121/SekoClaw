package com.sikoclaw.app.ui.chat

data class SearchSnippet(val text: String, val matches: List<IntRange>)

object ConversationSearchFormatter {
    fun format(source: String, query: String, radius: Int = 55): SearchSnippet {
        val clean = source.replace(Regex("\\s+"), " ").trim()
        if (query.isBlank()) return SearchSnippet(clean.take(radius * 2), emptyList())
        val first = clean.indexOf(query, ignoreCase = true)
        val start = if (first < 0) 0 else (first - radius).coerceAtLeast(0)
        val end = if (first < 0) clean.length.coerceAtMost(radius * 2) else
            (first + query.length + radius).coerceAtMost(clean.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < clean.length) "…" else ""
        val text = prefix + clean.substring(start, end) + suffix
        val matches = buildList {
            var cursor = 0
            while (true) {
                val found = text.indexOf(query, cursor, ignoreCase = true)
                if (found < 0) break
                add(found until found + query.length)
                cursor = found + query.length
            }
        }
        return SearchSnippet(text, matches)
    }
}
