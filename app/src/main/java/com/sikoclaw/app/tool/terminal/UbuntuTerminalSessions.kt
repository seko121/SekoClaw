package com.sikoclaw.app.tool.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalViewClient
import java.util.UUID

data class UbuntuTerminalTab(val id: String, var name: String, val session: TerminalSession)

object UbuntuTerminalSessions {
    private val sessions = mutableListOf<UbuntuTerminalTab>()
    fun all(): List<UbuntuTerminalTab> = synchronized(sessions) { sessions.toList() }
    fun create(client: TerminalSessionClient, name: String = "Terminal ${all().size + 1}"): UbuntuTerminalTab {
        check(UbuntuRuntime.isReady()) { "Ubuntu is not ready" }
        val session = TerminalSession(UbuntuRuntime.executable.absolutePath, UbuntuRuntime.linuxDir.absolutePath, UbuntuRuntime.interactiveProotArgs(), UbuntuRuntime.hostEnvironment(), 10_000, client)
        session.mSessionName = name
        return UbuntuTerminalTab(UUID.randomUUID().toString(), name, session).also { synchronized(sessions) { sessions += it } }
    }
    fun rename(id: String, name: String) = synchronized(sessions) { sessions.firstOrNull { it.id == id }?.let { it.name = name; it.session.mSessionName = name } }
    fun close(id: String) = synchronized(sessions) { sessions.firstOrNull { it.id == id }?.also { it.session.finishIfRunning(); sessions.remove(it) } }
    fun closeAll() = synchronized(sessions) { sessions.forEach { it.session.finishIfRunning() }; sessions.clear() }
}

class SikoTerminalClient(private val context: Context, private val invalidate: () -> Unit = {}) : TerminalSessionClient, TerminalViewClient {
    var control = false
    var alt = false
    override fun onTextChanged(changedSession: TerminalSession) = invalidate()
    override fun onTitleChanged(changedSession: TerminalSession) = invalidate()
    override fun onSessionFinished(finishedSession: TerminalSession) = invalidate()
    override fun onCopyTextToClipboard(session: TerminalSession, text: String) { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Terminal", text)) }
    override fun onPasteTextFromClipboard(session: TerminalSession) { val text = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() ?: return; session.write(text) }
    override fun onBell(session: TerminalSession) = Unit
    override fun onColorsChanged(session: TerminalSession) = invalidate()
    override fun onTerminalCursorStateChange(state: Boolean) = Unit
    override fun getTerminalCursorStyle(): Int? = 0
    override fun onScale(scale: Float): Float = scale
    override fun onSingleTapUp(e: MotionEvent) = Unit
    override fun shouldBackButtonBeMappedToEscape() = false
    // TYPE_NULL makes several Android keyboards send hardware-style key events
    // that are lost when TerminalView is hosted inside Compose. Character mode
    // keeps Gboard/Samsung Keyboard on the normal commitText path.
    override fun shouldEnforceCharBasedInput() = true
    override fun shouldUseCtrlSpaceWorkaround() = false
    override fun isTerminalViewSelected() = true
    override fun copyModeChanged(copyMode: Boolean) = Unit
    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession) = false
    override fun onKeyUp(keyCode: Int, e: KeyEvent) = false
    override fun onLongPress(event: MotionEvent) = false
    override fun readControlKey() = control.also { control = false }
    override fun readAltKey() = alt.also { alt = false }
    override fun readShiftKey() = false
    override fun readFnKey() = false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession) = false
    override fun onEmulatorSet() = Unit
    override fun logError(tag: String, message: String) { Log.e(tag, message) }
    override fun logWarn(tag: String, message: String) { Log.w(tag, message) }
    override fun logInfo(tag: String, message: String) { Log.i(tag, message) }
    override fun logDebug(tag: String, message: String) { Log.d(tag, message) }
    override fun logVerbose(tag: String, message: String) { Log.v(tag, message) }
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { Log.e(tag, message, e) }
    override fun logStackTrace(tag: String, e: Exception) { Log.e(tag, e.message, e) }
}
