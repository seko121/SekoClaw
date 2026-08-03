package com.sikoclaw.app.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import com.sikoclaw.app.utils.KVUtils

/** A non-interactive accessibility overlay that makes agent gestures visible and stoppable. */
object AgentControlOverlay {
    private val main = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var view: ControlOverlayView? = null
    private val hidePointer = Runnable { view?.setPointer(null, null) }

    @JvmStatic fun attach(service: AccessibilityService) = main.post {
        if (view != null) return@post
        windowManager = service.getSystemService(WindowManager::class.java)
        val overlay = ControlOverlayView(service)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        runCatching { windowManager?.addView(overlay, params); view = overlay }
    }

    @JvmStatic fun showGesture(x: Int, y: Int) = main.post {
        val overlay = view ?: return@post
        overlay.pointerVisible = KVUtils.isVirtualPointerEnabled()
        overlay.setPointer(x.toFloat(), y.toFloat())
        main.removeCallbacks(hidePointer)
        main.postDelayed(hidePointer, 900)
    }

    @JvmStatic fun movePointer(x: Int, y: Int) = main.post {
        val overlay = view ?: return@post
        overlay.pointerVisible = KVUtils.isVirtualPointerEnabled()
        overlay.setPointer(x.toFloat(), y.toFloat())
    }

    @JvmStatic fun setAgentActive(active: Boolean) = main.post {
        view?.active = active && KVUtils.isAgentControlHaloEnabled()
        view?.invalidate()
        if (!active) view?.setPointer(null, null)
    }

    @JvmStatic fun detach() = main.post {
        val current = view ?: return@post
        runCatching { windowManager?.removeView(current) }
        view = null
        windowManager = null
    }

    @JvmStatic fun stopControlSession() = main.post {
        main.removeCallbacks(hidePointer)
        view?.active = false
        view?.pointerVisible = false
        view?.setPointer(null, null)
    }
}

private class ControlOverlayView(service: AccessibilityService) : View(service) {
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(30, 174, 255)
        style = Paint.Style.STROKE
        strokeWidth = 7f
        setShadowLayer(18f, 0f, 0f, color)
    }
    private val pointer = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(45, 185, 255) }
    var active = false
    var pointerVisible = false
    private var pointerX: Float? = null
    private var pointerY: Float? = null

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

    fun setPointer(x: Float?, y: Float?) {
        pointerX = x
        pointerY = y
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (active) canvas.drawRoundRect(5f, 5f, width - 5f, height - 5f, 24f, 24f, halo)
        if (pointerVisible && pointerX != null && pointerY != null) {
            canvas.drawCircle(pointerX!!, pointerY!!, 13f, pointer)
            canvas.drawCircle(pointerX!!, pointerY!!, 22f, halo)
        }
    }
}
