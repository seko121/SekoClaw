package com.sikoclaw.app.ui.web

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import com.sikoclaw.app.R
import com.sikoclaw.app.base.BaseActivity
import com.sikoclaw.app.plugin.PluginCatalog
import com.sikoclaw.app.widget.CommonToolbar
import org.json.JSONArray
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebResponse
import java.net.URLEncoder
import java.util.UUID

internal fun resolveBrowserInput(input: String): String {
    val text = input.trim()
    return when {
        text.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) -> text
        text.contains('.') && !text.contains(' ') -> "https://$text"
        else -> "https://www.google.com/search?q=${URLEncoder.encode(text, "UTF-8")}"
    }
}

/** Browser chrome built around GeckoView. GeckoView is the engine, not the browser UI. */
class WebActivity : BaseActivity() {
    private data class BrowserTab(val id: String, val session: GeckoSession, var url: String, var title: String = "New tab", val private: Boolean = false, var canGoBack: Boolean = false, var canGoForward: Boolean = false)

    private lateinit var toolbar: CommonToolbar
    private lateinit var progress: ProgressBar
    private lateinit var geckoView: GeckoView
    private lateinit var addressBar: EditText
    private lateinit var back: ImageButton
    private lateinit var forward: ImageButton
    private lateinit var reload: ImageButton
    private lateinit var tabsButton: TextView
    private val tabs = mutableListOf<BrowserTab>()
    private var selectedTabId: String? = null
    private var loading = false
    private val selectedTab get() = tabs.firstOrNull { it.id == selectedTabId }

    companion object {
        private const val EXTRA_URL = "extra_url"
        private const val EXTRA_TITLE = "extra_title"
        private const val PREFS = "siko_browser"
        private const val TABS = "tabs"
        private const val HOME = "https://www.google.com/"
        @Volatile private var runtime: GeckoRuntime? = null

        fun start(context: Context, url: String, title: String? = null) {
            context.startActivity(Intent(context, WebActivity::class.java).apply {
                putExtra(EXTRA_URL, url); putExtra(EXTRA_TITLE, title)
                if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!PluginCatalog.isEnabled("browser")) { finish(); return }
        setContentView(R.layout.activity_web)
        toolbar = findViewById(R.id.toolbar); progress = findViewById(R.id.progressBar); geckoView = findViewById(R.id.webView)
        addressBar = findViewById(R.id.addressBar); back = findViewById(R.id.browserBack); forward = findViewById(R.id.browserForward)
        reload = findViewById(R.id.browserReload); tabsButton = findViewById(R.id.browserTabs)
        toolbar.setTitle("Browser"); toolbar.showBackButton(true) { finish() }

        back.setOnClickListener { selectedTab?.session?.goBack() }
        forward.setOnClickListener { selectedTab?.session?.goForward() }
        reload.setOnClickListener { selectedTab?.session?.let { if (loading) it.stop() else it.reload() } }
        tabsButton.setOnClickListener { showTabs() }
        findViewById<View>(R.id.browserMenu).setOnClickListener { showMenu(it) }
        addressBar.setOnFocusChangeListener { _, focused -> if (focused) addressBar.selectAll() }
        addressBar.setOnClickListener { addressBar.selectAll() }
        addressBar.setOnEditorActionListener { _, _, event ->
            if (event == null || event.keyCode == KeyEvent.KEYCODE_ENTER) { navigate(addressBar.text.toString()); true } else false
        }

        val requested = intent.getStringExtra(EXTRA_URL)?.takeIf { it.isNotBlank() }
        restoreTabs(requested)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { selectedTab?.let { if (it.canGoBack) it.session.goBack() else finish() } ?: finish() }
        })
    }

    private fun newTab(url: String = HOME, private: Boolean = false, select: Boolean = true): BrowserTab {
        val session = GeckoSession(GeckoSessionSettings.Builder().usePrivateMode(private).build())
        val tab = BrowserTab(UUID.randomUUID().toString(), session, url, private = private)
        installDelegates(tab)
        session.open(runtime ?: synchronized(WebActivity::class.java) { runtime ?: GeckoRuntime.create(applicationContext).also { runtime = it } })
        tabs += tab
        if (select) selectTab(tab)
        session.loadUri(url)
        persistTabs(); updateControls()
        return tab
    }

    private fun selectTab(tab: BrowserTab) {
        runCatching { geckoView.releaseSession() }
        selectedTabId = tab.id
        geckoView.setSession(tab.session)
        addressBar.setText(tab.url)
        toolbar.setTitle(tab.title)
        updateControls()
    }

    private fun closeTab(tab: BrowserTab) {
        val wasSelected = tab.id == selectedTabId
        if (wasSelected) runCatching { geckoView.releaseSession() }
        tab.session.close(); tabs.remove(tab)
        if (tabs.isEmpty()) newTab() else if (wasSelected) selectTab(tabs.last())
        persistTabs(); updateControls()
    }

    private fun installDelegates(tab: BrowserTab) {
        tab.session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                if (!title.isNullOrBlank()) tab.title = title
                if (tab.id == selectedTabId) toolbar.setTitle(tab.title)
                persistTabs()
            }
            override fun onExternalResponse(session: GeckoSession, response: WebResponse) { saveDownload(response) }
        }
        tab.session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) { if (tab.id == selectedTabId) { loading = true; progress.visibility = View.VISIBLE; progress.progress = 8; updateControls() } }
            override fun onProgressChange(session: GeckoSession, value: Int) { if (tab.id == selectedTabId) { progress.progress = value; progress.visibility = if (value >= 100) View.GONE else View.VISIBLE } }
            override fun onPageStop(session: GeckoSession, success: Boolean) { if (tab.id == selectedTabId) { loading = false; progress.visibility = View.GONE; updateControls() } }
        }
        tab.session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(session: GeckoSession, url: String?, perms: List<GeckoSession.PermissionDelegate.ContentPermission>, hasUserGesture: Boolean) {
                if (!url.isNullOrBlank()) {
                    tab.url = url
                    if (tab.id == selectedTabId && !addressBar.hasFocus()) addressBar.setText(url)
                    if (!tab.private && (url.startsWith("https://") || url.startsWith("http://"))) {
                        com.sikoclaw.app.utils.KVUtils.setLastBrowserUrl(url); addHistory(url, tab.title)
                    }
                    persistTabs()
                }
            }
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) { tab.canGoBack = canGoBack; if (tab.id == selectedTabId) updateControls() }
            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) { tab.canGoForward = canGoForward; if (tab.id == selectedTabId) updateControls() }
        }
    }

    private fun saveDownload(response: WebResponse) {
        Thread {
            runCatching {
                val disposition = response.headers.entries.firstOrNull { it.key.equals("content-disposition", true) }?.value.orEmpty()
                val headerName = Regex("filename\\*?=(?:UTF-8''|\")?([^\";]+)", RegexOption.IGNORE_CASE).find(disposition)?.groupValues?.getOrNull(1)
                val name = Uri.decode(headerName ?: Uri.parse(response.uri).lastPathSegment ?: "download-${System.currentTimeMillis()}").replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val mime = response.headers.entries.firstOrNull { it.key.equals("content-type", true) }?.value?.substringBefore(';') ?: "application/octet-stream"
                val body = response.body ?: error("Download response was empty")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, name); put(MediaStore.Downloads.MIME_TYPE, mime); put(MediaStore.Downloads.IS_PENDING, 1) }
                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Could not create download")
                    try { contentResolver.openOutputStream(uri)?.use { output -> body.use { input -> input.copyTo(output) } } ?: error("Could not open download") }
                    catch (error: Throwable) { contentResolver.delete(uri, null, null); throw error }
                    values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0); contentResolver.update(uri, values, null, null)
                } else {
                    @Suppress("DEPRECATION")
                    val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).apply { mkdirs() }
                    val target = java.io.File(directory, name)
                    target.outputStream().use { output -> body.use { input -> input.copyTo(output) } }
                }
                runOnUiThread { Toast.makeText(this, "Downloaded $name", Toast.LENGTH_LONG).show() }
            }.onFailure { error -> runOnUiThread { Toast.makeText(this, "Download failed: ${error.message}", Toast.LENGTH_LONG).show() } }
        }.start()
    }

    private fun navigate(input: String) {
        val text = input.trim(); if (text.isBlank()) return
        val target = resolveBrowserInput(text)
        selectedTab?.session?.loadUri(target)
        addressBar.clearFocus(); (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(addressBar.windowToken, 0)
    }

    private fun updateControls() {
        val tab = selectedTab
        back.isEnabled = tab?.canGoBack == true; forward.isEnabled = tab?.canGoForward == true
        back.alpha = if (back.isEnabled) 1f else .35f; forward.alpha = if (forward.isEnabled) 1f else .35f
        reload.setImageResource(if (loading) android.R.drawable.ic_delete else android.R.drawable.ic_popup_sync)
        tabsButton.text = tabs.size.toString()
    }

    private fun showTabs() {
        val labels = tabs.map { (if (it.private) "Private · " else "") + it.title }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Tabs").setItems(labels) { _, index -> selectTab(tabs[index]) }
            .setPositiveButton("New tab") { _, _ -> newTab() }
            .setNeutralButton("Close current") { _, _ -> selectedTab?.let(::closeTab) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            listOf("New tab", "New private tab", "History", "Downloads", "Bookmarks", "Find in page", "Share page", "Open externally", "Copy URL", "Desktop site", "Add bookmark").forEachIndexed { i, text -> menu.add(0, i + 1, i, text) }
            setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    "New tab" -> newTab(); "New private tab" -> newTab(private = true); "History" -> showSavedList("History", "history"); "Downloads" -> runCatching { startActivity(Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)) }
                    "Bookmarks" -> showSavedList("Bookmarks", "bookmarks"); "Find in page" -> showFindDialog()
                    "Share page" -> selectedTab?.let { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, it.url), "Share page")) }
                    "Open externally" -> selectedTab?.let { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.url))) }
                    "Copy URL" -> selectedTab?.let { (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("URL", it.url)); Toast.makeText(this@WebActivity, "URL copied", Toast.LENGTH_SHORT).show() }
                    "Desktop site" -> selectedTab?.session?.settings?.userAgentMode = if (selectedTab?.session?.settings?.userAgentMode == GeckoSessionSettings.USER_AGENT_MODE_DESKTOP) GeckoSessionSettings.USER_AGENT_MODE_MOBILE else GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                    "Add bookmark" -> selectedTab?.let { addSaved("bookmarks", it.url, it.title); Toast.makeText(this@WebActivity, "Bookmark added", Toast.LENGTH_SHORT).show() }
                }; true
            }; show()
        }
    }

    private fun showFindDialog() {
        val field = EditText(this).apply { hint = "Find in page"; setSingleLine() }
        AlertDialog.Builder(this).setTitle("Find in page").setView(field).setPositiveButton("Find") { _, _ -> selectedTab?.session?.finder?.find(field.text.toString(), 0) }.setNegativeButton("Cancel", null).show()
    }

    private fun restoreTabs(requested: String?) {
        val saved = runCatching { JSONArray(getSharedPreferences(PREFS, MODE_PRIVATE).getString(TABS, "[]")) }.getOrNull()
        if (requested != null) newTab(requested)
        else if (saved != null && saved.length() > 0) for (i in 0 until saved.length()) newTab(saved.optString(i, HOME), select = i == saved.length() - 1)
        else newTab(com.sikoclaw.app.utils.KVUtils.getLastBrowserUrl().ifBlank { HOME })
    }

    private fun persistTabs() { getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(TABS, JSONArray(tabs.filterNot { it.private }.map { it.url }).toString()).apply() }
    private fun addHistory(url: String, title: String) = addSaved("history", url, title, 100)
    private fun addSaved(key: String, url: String, title: String, limit: Int = 200) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE); val items = prefs.getStringSet(key, emptySet()).orEmpty().toMutableList()
        items.removeAll { it.substringAfter('|') == url }; items.add(0, "$title|$url"); prefs.edit().putStringSet(key, items.take(limit).toSet()).apply()
    }
    private fun showSavedList(title: String, key: String) {
        val items = getSharedPreferences(PREFS, MODE_PRIVATE).getStringSet(key, emptySet()).orEmpty().toList()
        AlertDialog.Builder(this).setTitle(title).setItems(items.map { it.substringBefore('|').ifBlank { it.substringAfter('|') } }.toTypedArray()) { _, i -> navigate(items[i].substringAfter('|')) }.setNegativeButton("Close", null).show()
    }

    override fun onDestroy() { tabs.forEach { runCatching { it.session.close() } }; tabs.clear(); super.onDestroy() }
}
