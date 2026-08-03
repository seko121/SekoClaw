package com.sikoclaw.app.floating

import android.app.Application
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import com.lzf.easyfloat.EasyFloat
import com.lzf.easyfloat.enums.ShowPattern
import com.lzf.easyfloat.enums.SidePattern
import com.lzf.easyfloat.interfaces.OnFloatCallbacks
import com.sikoclaw.app.R
import com.sikoclaw.app.ui.chat.ChatMessage
import com.sikoclaw.app.ui.chat.ComposeChatActivity
import com.sikoclaw.app.utils.KVUtils
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import kotlin.math.abs
import java.util.WeakHashMap

object FloatingAssistantManager {
    private const val TAG = "floating_assistant_chat"
    private const val TRASH_TAG = "floating_assistant_trash"
    private const val X = "FLOAT_ASSISTANT_X"
    private const val Y = "FLOAT_ASSISTANT_Y"
    @Volatile private var appForeground = false
    /** Kept outside View.tag because that tag belongs to OctoBot's image state. */
    private val voicePulseAnimations = WeakHashMap<ImageView, ObjectAnimator>()

    fun setAppForeground(foreground: Boolean) {
        appForeground = foreground
        if (foreground) EasyFloat.hide(TAG)
        else if (FloatingAssistantConfig.enabled()) EasyFloat.show(TAG)
    }

    fun show(app: Application) {
        if (EasyFloat.getFloatView(TAG) != null) {
            if (!appForeground) EasyFloat.show(TAG)
            return
        }
        val density = app.resources.displayMetrics.density
        EasyFloat.with(app)
            .setLayout(R.layout.layout_floating_assistant)
            .setShowPattern(ShowPattern.ALL_TIME)
            .setSidePattern(if (FloatingAssistantConfig.snap()) SidePattern.RESULT_HORIZONTAL else SidePattern.DEFAULT)
            .setGravity(Gravity.START or Gravity.TOP, KVUtils.getInt(X, (16 * density).toInt()), KVUtils.getInt(Y, (96 * density).toInt()))
            // Dragging the whole overlay steals ScrollView gestures. Movement is
            // deliberately limited to the bubble and panel header in bind().
            .setDragEnable(false).hasEditText(true).setTag(TAG)
            .registerCallbacks(object : OnFloatCallbacks {
                override fun createdResult(isCreated: Boolean, msg: String?, view: View?) {
                    view?.post { bind(app, view); if (appForeground) EasyFloat.hide(TAG) }
                }
                override fun drag(view: View, event: MotionEvent) { showTrash(app); updateTrashHighlight(view, app) }
                override fun dragEnd(view: View) {
                    if (isOverTrash(view, app)) {
                        FloatingAssistantConfig.setEnabled(false)
                        EasyFloat.dismiss(TAG); EasyFloat.dismiss(TRASH_TAG)
                        FloatingAssistantService.stop(app)
                    } else {
                        val pos = IntArray(2); view.getLocationOnScreen(pos)
                        KVUtils.putInt(X, pos[0]); KVUtils.putInt(Y, pos[1].coerceAtLeast((48 * density).toInt()))
                        EasyFloat.dismiss(TRASH_TAG)
                    }
                }
                override fun dismiss() { EasyFloat.dismiss(TRASH_TAG) }
                override fun hide(view: View) = Unit
                override fun show(view: View) = Unit
                override fun touchEvent(view: View, event: MotionEvent) = Unit
            }).show()
    }

    private fun bind(app: Application, root: View) {
        root.alpha = FloatingAssistantConfig.opacity() / 100f
        val bubble = root.findViewById<ImageView>(R.id.assistantBubble)
        bubble.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.rgb(15, 27, 48)); setStroke(dp(app, 2), Color.rgb(96, 165, 250)) }
        bubble.clipToOutline = true
        val pixels = dp(app, FloatingAssistantConfig.size())
        bubble.layoutParams = bubble.layoutParams.apply { width = pixels; height = pixels }
        val panel = root.findViewById<View>(R.id.assistantPanel)
        val messages = root.findViewById<LinearLayout>(R.id.assistantMessages)
        val scroll = root.findViewById<ScrollView>(R.id.assistantMessageScroll)
        val status = root.findViewById<TextView>(R.id.assistantStatus)
        val input = root.findViewById<EditText>(R.id.assistantInput)
        configureDragHandle(app, root, bubble)
        configureDragHandle(app, root, root.findViewById(R.id.assistantDragHandle))
        bubble.setOnClickListener {
            if (panel.visibility == View.VISIBLE) collapsePanel(panel) else {
                panel.visibility = View.VISIBLE
                panel.alpha = 0f; panel.scaleX = .96f; panel.scaleY = .96f
                panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start()
                refresh(app, messages, scroll, status, forceScroll = true)
            }
        }
        root.findViewById<ImageButton>(R.id.assistantCollapse).setOnClickListener { collapsePanel(panel) }
        root.findViewById<ImageButton>(R.id.assistantExpand).setOnClickListener { app.startActivity(Intent(app, ComposeChatActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); collapsePanel(panel) }
        root.findViewById<ImageButton>(R.id.assistantCall).setOnClickListener { app.startActivity(Intent(app, com.sikoclaw.app.voice.VoiceCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        root.findViewById<ImageButton>(R.id.assistantStop).setOnClickListener { SharedChatBus.stop() }
        root.findViewById<ImageButton>(R.id.assistantSend).setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isNotEmpty()) {
                if (!SharedChatBus.sendTask(text)) app.startActivity(Intent(app, ComposeChatActivity::class.java).putExtra("task", text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                input.text.clear(); refresh(app, messages, scroll, status, true)
            }
        }
        root.postDelayed(object : Runnable {
            override fun run() {
                applyOctoBotMotion(bubble)
                applyVoicePulse(bubble)
                if (panel.visibility == View.VISIBLE) refresh(app, messages, scroll, status, false)
                root.postDelayed(this, 350)
            }
        }, 350)
    }

    private fun refresh(app: Application, container: LinearLayout, scroll: ScrollView, statusView: TextView, forceScroll: Boolean) {
        EasyFloat.getFloatView(TAG)?.findViewById<ImageView>(R.id.assistantBubble)?.let {
            applyOctoBotMotion(it)
            applyVoicePulse(it)
        }
        statusView.text = SharedChatBus.status.value
        statusView.visibility = if (FloatingAssistantConfig.showActions()) View.VISIBLE else View.GONE
        if (!FloatingAssistantConfig.showPreviews()) { container.removeAllViews(); return }
        val rows = SharedChatBus.messages.value.takeLast(12).filter { it.role != ChatMessage.Role.SYSTEM && it.role != ChatMessage.Role.TOOL_GROUP }
        val fingerprint = rows.hashCode()
        if (container.tag == fingerprint) return
        val wasNearBottom = scroll.scrollY + scroll.height >= container.height - dp(app, 40)
        container.tag = fingerprint; container.removeAllViews()
        val markwon = Markwon.builder(app).usePlugin(StrikethroughPlugin.create()).build()
        rows.forEach { message -> container.addView(messageRow(app, message, markwon)) }
        if (forceScroll || wasNearBottom || rows.lastOrNull()?.isStreaming == true) scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun messageRow(app: Application, message: ChatMessage, markwon: Markwon): View {
        val assistant = message.role != ChatMessage.Role.USER
        val row = LinearLayout(app).apply { orientation = LinearLayout.HORIZONTAL; gravity = if (assistant) Gravity.START or Gravity.BOTTOM else Gravity.END; setPadding(0, dp(app, 4), 0, dp(app, 4)) }
        if (assistant) row.addView(ImageView(app).apply { scaleType = ImageView.ScaleType.CENTER_CROP; applyOctoBotMotion(this); background = GradientDrawable().apply { shape = GradientDrawable.OVAL }; clipToOutline = true }, LinearLayout.LayoutParams(dp(app, 26), dp(app, 26)).apply { marginEnd = dp(app, 6) })
        val text = TextView(app).apply {
            setTextColor(if (assistant) Color.rgb(231, 238, 248) else Color.WHITE)
            textSize = 13f; setPadding(dp(app, 10), dp(app, 8), dp(app, 10), dp(app, 8))
            background = GradientDrawable().apply { cornerRadius = dp(app, 14).toFloat(); setColor(if (message.role == ChatMessage.Role.USER) Color.rgb(37, 99, 235) else if (message.role == ChatMessage.Role.REASONING) Color.rgb(41, 50, 67) else Color.rgb(30, 45, 69)) }
            markwon.setMarkdown(this, if (message.role == ChatMessage.Role.REASONING) "**Thoughts**\n\n${message.content}" else message.content)
            setTextIsSelectable(true)
        }
        row.addView(text, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.84f))
        return row
    }

    private fun showTrash(app: Application) {
        if (EasyFloat.getFloatView(TRASH_TAG) != null) return
        EasyFloat.with(app).setLayout(R.layout.layout_floating_trash).setShowPattern(ShowPattern.ALL_TIME)
            .setSidePattern(SidePattern.DEFAULT).setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, dp(app, 42))
            .setDragEnable(false).setTag(TRASH_TAG).show()
    }

    private fun updateTrashHighlight(view: View, app: Application) {
        EasyFloat.getFloatView(TRASH_TAG)?.apply { scaleX = if (isOverTrash(view, app)) 1.18f else 1f; scaleY = scaleX; alpha = if (isOverTrash(view, app)) 1f else .72f }
    }

    private fun isOverTrash(view: View, app: Application): Boolean {
        val target = view.findViewById<ImageView?>(R.id.assistantBubble) ?: view
        val trash = EasyFloat.getFloatView(TRASH_TAG) ?: return false
        val targetRect = screenBounds(target)
        val trashRect = screenBounds(trash)
        return Rect.intersects(targetRect, trashRect)
    }

    private fun screenBounds(view: View): Rect {
        val p = IntArray(2); view.getLocationOnScreen(p)
        return Rect(p[0], p[1], p[0] + view.width, p[1] + view.height)
    }

    private fun configureDragHandle(app: Application, root: View, handle: View) {
        val threshold = dp(app, 8)
        var startRawX = 0f; var startRawY = 0f; var startX = 0; var startY = 0
        var dragging = false; var enteredTrash = false
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val p = IntArray(2); root.getLocationOnScreen(p)
                    startX = p[0]; startY = p[1]; startRawX = event.rawX; startRawY = event.rawY
                    dragging = false; enteredTrash = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - startRawX).toInt(); val dy = (event.rawY - startRawY).toInt()
                    if (!dragging && (abs(dx) > threshold || abs(dy) > threshold)) dragging = true
                    if (dragging) {
                        showTrash(app)
                        EasyFloat.updateFloat(TAG, startX + dx, (startY + dy).coerceAtLeast(dp(app, 24)))
                        val over = isOverTrash(root, app)
                        if (over && !enteredTrash) handle.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                        enteredTrash = over
                        updateTrashHighlight(root, app)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging && isOverTrash(root, app)) {
                        root.animate().alpha(0f).scaleX(.5f).scaleY(.5f).setDuration(140).withEndAction {
                            FloatingAssistantConfig.setEnabled(false)
                            EasyFloat.dismiss(TAG); EasyFloat.dismiss(TRASH_TAG)
                            FloatingAssistantService.stop(app)
                        }.start()
                    } else if (dragging) {
                        val p = IntArray(2); root.getLocationOnScreen(p)
                        KVUtils.putInt(X, p[0]); KVUtils.putInt(Y, p[1])
                        EasyFloat.dismiss(TRASH_TAG)
                    } else if (event.actionMasked == MotionEvent.ACTION_UP && handle === root.findViewById<ImageView>(R.id.assistantBubble)) {
                        handle.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun dp(app: Application, value: Int) = (value * app.resources.displayMetrics.density).toInt()
    private fun applyOctoBotMotion(view: ImageView) {
        val motion = com.sikoclaw.app.agent.AgentAnimationState.state.value
        if (view.tag == motion) return
        view.tag = motion
        view.setImageResource(com.sikoclaw.app.agent.AgentAnimationState.drawable(motion))
        (view.drawable as? android.graphics.drawable.AnimatedImageDrawable)?.start()
    }

    /** A restrained transform-only pulse: it communicates microphone listening without blocking chat input. */
    private fun applyVoicePulse(view: ImageView) {
        val listening = com.sikoclaw.app.voice.VoiceSessionCoordinator.state.value == com.sikoclaw.app.voice.VoiceLoopState.LISTENING
        val running = voicePulseAnimations[view]
        if (listening && running == null) {
            ObjectAnimator.ofPropertyValuesHolder(
                view,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.12f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.12f),
            ).apply {
                duration = 540L
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
                start()
                voicePulseAnimations[view] = this
            }
        } else if (!listening && running != null) {
            running.cancel()
            voicePulseAnimations.remove(view)
            view.scaleX = 1f
            view.scaleY = 1f
        }
    }
    private fun collapsePanel(panel: View) {
        panel.animate().alpha(0f).scaleX(.96f).scaleY(.96f).setDuration(140).withEndAction {
            panel.visibility = View.GONE
            panel.alpha = 1f; panel.scaleX = 1f; panel.scaleY = 1f
        }.start()
    }
    fun hideTemporarily() = EasyFloat.hide(TAG)
    fun hide() { EasyFloat.dismiss(TAG); EasyFloat.dismiss(TRASH_TAG) }
}
