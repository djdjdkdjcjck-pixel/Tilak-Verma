package com.example.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import com.example.ZoyaForegroundService
import com.example.live.ZoyaState

/**
 * Full Screen Edge Lighting Overlay for Jarvis Assistant.
 * Displays smooth glowing lights along all four edges and corners of the phone screen
 * across all applications (YouTube, Instagram, WhatsApp, Chrome, etc.).
 * Colors:
 * - Listening: RED
 * - Speaking: BLUE
 * - Processing (Thinking): GREEN
 * - Idle / Waiting: YELLOW
 */
class JarvisEdgeLightingOverlay(private val service: ZoyaForegroundService) {

    companion object {
        private const val TAG = "JarvisEdgeLighting"
    }

    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private var edgeLightView: EdgeLightView? = null
    private var isShowing = false

    private val layoutParams = WindowManager.LayoutParams().apply {
        width = WindowManager.LayoutParams.MATCH_PARENT
        height = WindowManager.LayoutParams.MATCH_PARENT
        type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        format = PixelFormat.TRANSLUCENT
        gravity = Gravity.TOP or Gravity.START
    }

    fun show() {
        if (isShowing) return
        val prefs = service.getSharedPreferences("ZoyaPrefs", Context.MODE_PRIVATE)
        val edgeEnabled = prefs.getBoolean("edge_lighting_enabled", true)
        if (!edgeEnabled) {
            Log.i(TAG, "Edge lighting is disabled in settings.")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(service)) {
            Log.w(TAG, "Cannot show edge lighting: SYSTEM_ALERT_WINDOW permission not granted.")
            return
        }

        try {
            val view = EdgeLightView(service)
            windowManager?.addView(view, layoutParams)
            edgeLightView = view
            isShowing = true
            Log.i(TAG, "Jarvis full screen edge lighting shown successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add edge lighting view", e)
        }
    }

    fun updateState(state: ZoyaState) {
        edgeLightView?.setAssistantState(state)
    }

    fun hide() {
        if (!isShowing) return
        try {
            edgeLightView?.let { windowManager?.removeView(it) }
            edgeLightView = null
            isShowing = false
            Log.i(TAG, "Jarvis edge lighting removed.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove edge lighting view", e)
        }
    }

    inner class EdgeLightView(context: Context) : View(context) {

        private var currentState = ZoyaState.IDLE
        private var pulseAlpha = 0.7f
        private val borderRect = RectF()

        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 10f // Gorgeous 10px glowing edge
            strokeCap = Paint.Cap.ROUND
        }

        private val outerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 24f
            maskFilter = android.graphics.BlurMaskFilter(18f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }

        private val pulseAnimator = ValueAnimator.ofFloat(0.4f, 1.0f).apply {
            duration = 1000L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener {
                pulseAlpha = it.animatedValue as Float
                postInvalidateOnAnimation()
            }
        }

        init {
            pulseAnimator.start()
            updateColorForState(ZoyaState.IDLE)
        }

        fun setAssistantState(state: ZoyaState) {
            if (currentState == state) return
            currentState = state
            updateColorForState(state)
            invalidate()
        }

        private fun updateColorForState(state: ZoyaState) {
            val colorHex = when (state) {
                ZoyaState.LISTENING -> Color.parseColor("#FF1744") // RED
                ZoyaState.SPEAKING -> Color.parseColor("#2979FF")  // BLUE
                ZoyaState.THINKING -> Color.parseColor("#00E676")  // GREEN
                ZoyaState.IDLE -> Color.parseColor("#FFEA00")       // YELLOW
            }
            borderPaint.color = colorHex
            outerGlowPaint.color = colorHex
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            pulseAnimator.cancel()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val stroke = borderPaint.strokeWidth
            val half = stroke / 2f

            borderRect.set(half, half, w - half, h - half)
            val cornerRadius = 32f

            // Draw outer ambient glow around all 4 edges and 4 corners
            outerGlowPaint.alpha = (pulseAlpha * 120).toInt()
            canvas.drawRoundRect(borderRect, cornerRadius, cornerRadius, outerGlowPaint)

            // Draw sharp glowing border along top, bottom, left, right and corners
            borderPaint.alpha = (pulseAlpha * 255).toInt()
            canvas.drawRoundRect(borderRect, cornerRadius, cornerRadius, borderPaint)
        }
    }
}
