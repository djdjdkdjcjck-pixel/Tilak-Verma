package com.example.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.SweepGradient
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import com.example.ZoyaForegroundService
import com.example.live.ZoyaState
import kotlin.math.abs

/**
 * Joya Floating Circular Animated Overlay Indicator ("गोल-गोल छोटा एनिमेशन")
 * Always present across Instagram, YouTube, and all other apps.
 * Non-intrusive (48dp), draggable, tap to trigger Joya listening.
 */
class JoyaFloatingOverlay(private val service: ZoyaForegroundService) {

    companion object {
        private const val TAG = "JoyaFloatingOverlay"
    }

    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private var overlayView: JoyaCircleView? = null
    private var isShowing = false

    private val layoutParams = WindowManager.LayoutParams().apply {
        width = dpToPx(56)
        height = dpToPx(56)
        type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        format = PixelFormat.TRANSLUCENT
        gravity = Gravity.BOTTOM or Gravity.END
        x = dpToPx(20)
        y = dpToPx(120)
    }

    fun show() {
        if (isShowing) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(service)) {
            Log.w(TAG, "Cannot show floating indicator: SYSTEM_ALERT_WINDOW permission not granted.")
            return
        }

        Handler(Looper.getMainLooper()).post {
            if (isShowing) return@post
            try {
                val view = JoyaCircleView(service)
                setupTouchListener(view)
                windowManager?.addView(view, layoutParams)
                overlayView = view
                isShowing = true
                Log.i(TAG, "Joya circular floating indicator shown successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add floating overlay view", e)
            }
        }
    }

    fun updateState(state: ZoyaState) {
        Handler(Looper.getMainLooper()).post {
            overlayView?.setAssistantState(state)
        }
    }

    fun hide() {
        if (!isShowing) return
        Handler(Looper.getMainLooper()).post {
            if (!isShowing) return@post
            try {
                overlayView?.let { windowManager?.removeView(it) }
                overlayView = null
                isShowing = false
                Log.i(TAG, "Joya floating overlay removed.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove floating overlay view", e)
            }
        }
    }

    private fun setupTouchListener(view: View) {
        view.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isClick = true

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isClick = true
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (abs(dx) > 10 || abs(dy) > 10) {
                            isClick = false
                        }
                        layoutParams.x = initialX + dx
                        layoutParams.y = initialY + dy
                        if (isShowing) {
                            try {
                                windowManager?.updateViewLayout(overlayView, layoutParams)
                            } catch (e: Exception) {}
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isClick) {
                            val intent = Intent(service, com.example.MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                            service.startActivity(intent)
                            service.triggerManualListening()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun dpToPx(dp: Int): Int {
        val density = service.resources.displayMetrics.density
        return (dp * density).toInt()
    }

    /**
     * Custom lightweight view drawing the "gol-gol chhota animation"
     */
    inner class JoyaCircleView(context: Context) : View(context) {

        private var rotationAngle = 0f
        private var pulseScale = 1.0f
        private var currentState = ZoyaState.IDLE

        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 15, 15, 15)
            style = Paint.Style.FILL
        }

        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
        }

        private val centerCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 215, 0) // Golden
            style = Paint.Style.FILL
        }

        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(80, 255, 215, 0)
            style = Paint.Style.FILL
        }

        private val ringBounds = RectF()

        private val animator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 1800L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                rotationAngle = it.animatedValue as Float
                postInvalidateOnAnimation()
            }
        }

        private val pulseAnimator = ValueAnimator.ofFloat(0.85f, 1.15f).apply {
            duration = 900L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener {
                pulseScale = it.animatedValue as Float
                postInvalidateOnAnimation()
            }
        }

        init {
            animator.start()
            pulseAnimator.start()
        }

        fun setAssistantState(state: ZoyaState) {
            Handler(Looper.getMainLooper()).post {
                if (currentState == state) return@post
                currentState = state
                when (state) {
                    ZoyaState.LISTENING -> {
                        animator.duration = 900L
                        val col = Color.rgb(255, 23, 68) // Laal (Red)
                        centerCorePaint.color = col
                        ringPaint.color = col
                        glowPaint.color = Color.argb(80, 255, 23, 68)
                    }
                    ZoyaState.SPEAKING -> {
                        animator.duration = 600L
                        val col = Color.rgb(41, 121, 255) // Neela (Blue)
                        centerCorePaint.color = col
                        ringPaint.color = col
                        glowPaint.color = Color.argb(80, 41, 121, 255)
                    }
                    ZoyaState.THINKING -> {
                        animator.duration = 1100L
                        val col = Color.rgb(0, 230, 118) // Hara (Green)
                        centerCorePaint.color = col
                        ringPaint.color = col
                        glowPaint.color = Color.argb(80, 0, 230, 118)
                    }
                    ZoyaState.IDLE -> {
                        animator.duration = 2000L
                        val col = Color.rgb(255, 230, 0) // Peela (Yellow)
                        centerCorePaint.color = col
                        ringPaint.color = col
                        glowPaint.color = Color.argb(80, 255, 230, 0)
                    }
                }
                invalidate()
            }
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            animator.cancel()
            pulseAnimator.cancel()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val radius = (width.coerceAtMost(height) / 2f) - 4f

            // 1. Dark titanium outer housing
            canvas.drawCircle(cx, cy, radius, backgroundPaint)

            // 2. Ambient Arc Reactor glow pulse
            val glowRadius = (radius * 0.7f) * pulseScale
            canvas.drawCircle(cx, cy, glowRadius, glowPaint)

            // 3. 8 Segmented Arc Reactor Coils
            canvas.save()
            canvas.rotate(rotationAngle, cx, cy)
            val coilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = when (currentState) {
                    ZoyaState.LISTENING -> Color.rgb(255, 23, 68)
                    ZoyaState.SPEAKING -> Color.rgb(41, 121, 255)
                    ZoyaState.THINKING -> Color.rgb(0, 230, 118)
                    ZoyaState.IDLE -> Color.rgb(255, 230, 0)
                }
                strokeWidth = 3f
                style = Paint.Style.STROKE
            }
            val coilInner = radius * 0.42f
            val coilOuter = radius * 0.80f
            for (i in 0 until 8) {
                val angle = Math.toRadians((i * 45.0))
                val x1 = cx + (coilInner * kotlin.math.cos(angle)).toFloat()
                val y1 = cy + (coilInner * kotlin.math.sin(angle)).toFloat()
                val x2 = cx + (coilOuter * kotlin.math.cos(angle)).toFloat()
                val y2 = cy + (coilOuter * kotlin.math.sin(angle)).toFloat()
                canvas.drawLine(x1, y1, x2, y2, coilPaint)
            }
            canvas.restore()

            // 4. Central Glowing Arc Reactor Core
            val coreRadius = (radius * 0.32f) * pulseScale
            canvas.drawCircle(cx, cy, coreRadius, centerCorePaint)
            val coreHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            canvas.drawCircle(cx - coreRadius * 0.25f, cy - coreRadius * 0.25f, coreRadius * 0.3f, coreHighlightPaint)

            // 5. Outer rotating magnetic containment ring
            ringBounds.set(cx - radius + 3f, cy - radius + 3f, cx + radius - 3f, cy + radius - 3f)
            canvas.save()
            canvas.rotate(-rotationAngle * 1.5f, cx, cy)
            canvas.drawArc(ringBounds, 0f, 270f, false, ringPaint)
            canvas.restore()
        }
    }
}
