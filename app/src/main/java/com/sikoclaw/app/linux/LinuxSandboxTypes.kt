/*
 * Adapted from Kai, Copyright Simon Schubert and contributors.
 * Licensed under the Apache License, Version 2.0.
 * Modified for OctoBot Android integration.
 */
package com.sikoclaw.app.linux

import androidx.compose.runtime.Immutable

@Immutable
sealed interface TerminalLine {
    val text: String
    data class Command(override val text: String) : TerminalLine
    data class Output(override val text: String) : TerminalLine
    data class Error(override val text: String) : TerminalLine
}

internal fun String.smartTruncate(maxLength: Int): String =
    if (length <= maxLength) this else take(maxLength / 2) + "\n... output truncated ...\n" + takeLast(maxLength / 2)
