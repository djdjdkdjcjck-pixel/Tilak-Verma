package com.example.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import com.example.ZoyaForegroundService
import com.example.live.ZoyaState

/**
 * Full Screen Four-Side Edge Lighting Overlay for Jarvis Assistant.
 * Thin, smooth glowing light strips along all 4 edges and all 4 corners.
 * Overlay over other apps (YouTube, Instagram, Facebook, games) using TYPE_APPLICATION_OVERLAY.
 * Colors:
 * - Listening: RED
 * - Speaking: RED
 * - Processing (Thinking): GREEN
 * - Idle / Waiting: Four-color multicolored animated gradient (Red, Yellow, Green, Blue)
 */
class JarvisEdgeLightingOverlay(private val service: ZoyaForegroundService) {

    companion object {
        private const val TAG = "JarvisEdgeLighting"
    }

    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private var edgeLightView: EdgeLightView? = null
    private var isShowing = false
    private val mainHandler = Handler(Looper.getMainLooper())

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
        mainHandler.post {
            if (isShowing) return@post
            val prefs = service.getSharedPreferences("ZoyaPrefs", Context.MODE_PRIVATE)
            val edgeEnabled = prefs.getBoolean("edge_lighting_enabled", true)
            if (!edgeEnabled) {
                Log.i(TAG, "Edge lighting is disabled in settings.")
                return@post
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(service)) {
                Log.w(TAG, "Cannot show edge lighting: SYSTEM_ALERT_WINDOW permission not granted.")
                return@post
            }

            try {
                val view = EdgeLightView(service)
                windowManager?.addView(view, layoutParams)
                edgeLightView = view
                isShowing = true
                Log.i(TAG, "Jarvis four-side edge lighting shown successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add edge lighting view", e)
            }
        }
    }

    fun updateState(state: ZoyaState) {
        mainHandler.post {
            edgeLightView?.setAssistantState(state)
        }
    }

    fun hide() {
        mainHandler.post {
            if (!isShowing) return@post
            try {
                edgeLightView?.let { windowManager?.removeView(it) }
                edgeLightView = null
                isShowing = false
                Log.i(TAG, "Jarvis edge lighting removed.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove edge lighting view", e)
            }
        }
    }

    inner class EdgeLightView(context: Context) : View(context) {

        private var currentState = ZoyaState.IDLE
        private var pulseAlpha = 0.85f
        private var animProgress = 0f
        private val borderRect = RectF()

        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 14f // Thin, smooth, elegant light strip
            strokeCap = Paint.Cap.ROUND
        }

        private val outerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 32f
            maskFilter = android.graphics.BlurMaskFilter(16f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }

        private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                animProgress = it.animatedValue as Float
                pulseAlpha = 0.7f + 0.3f * kotlin.math.sin(animProgress * 2 * Math.PI).toFloat()
                postInvalidateOnAnimation()
            }
        }

        init {
            animator.start()
            updateColorsForState(ZoyaState.IDLE)
        }

        fun setAssistantState(state: ZoyaState) {
            mainHandler.post {
                if (currentState == state) return@post
                currentState = state
                updateColorsForState(state)
                invalidate()
            }
        }

        private fun updateColorsForState(state: ZoyaState) {
            when (state) {
                ZoyaState.LISTENING, ZoyaState.SPEAKING -> {
                    // RED edge lights
                    val red = Color.parseColor("#FF1744")
                    borderPaint.shader = null
                    borderPaint.color = red
                    outerGlowPaint.shader = null
                    outerGlowPaint.color = red
                }
                ZoyaState.THINKING -> {
                    // GREEN edge lights
                    val green = Color.parseColor("#00E676")
                    borderPaint.shader = null
                    borderPaint.color = green
                    outerGlowPaint.shader = null
                    outerGlowPaint.color = green
                }
                ZoyaState.IDLE -> {
                    // Four-color multicolored edge lights (handled in onDraw via dynamic gradient)
                }
            }
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            animator.cancel()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val stroke = borderPaint.strokeWidth
            val half = stroke / 2f
            borderRect.set(half, half, w - half, h - half)
            val cornerRadius = 36f

            if (currentState == ZoyaState.IDLE) {
                // Multicolored four-color shifting gradient across edges
                val shift = animProgress * w
                val colors = intArrayOf(
                    Color.parseColor("#FF1744"), // Red
                    Color.parseColor("#FFEA00"), // Yellow
                    Color.parseColor("#00E676"), // Green
                    Color.parseColor("#2979FF"), // Blue
                    Color.parseColor("#FF1744")  // Wrap
                )
                val shader = LinearGradient(
                    shift, 0f, shift + w, h,
                    colors,
                    null,
                    Shader.TileMode.REPEAT
                )
                borderPaint.shader = shader
                outerGlowPaint.shader = shader
            }

            outerGlowPaint.alpha = (pulseAlpha * 180).toInt()
            canvas.drawRoundRect(borderRect, cornerRadius, cornerRadius, outerGlowPaint)

            borderPaint.alpha = (pulseAlpha * 255).toInt()
            canvas.drawRoundRect(borderRect, cornerRadius, cornerRadius, borderPaint)
        }
    }
}
