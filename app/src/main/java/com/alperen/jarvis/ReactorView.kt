package com.alperen.jarvis

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator

enum class ReactorState { IDLE, LISTENING, THINKING, SPEAKING }

class ReactorView(context: Context) : View(context) {

    var state: ReactorState = ReactorState.IDLE
        set(value) {
            field = value
            updateAnimatorSpeed()
        }

    private var rotation = 0f
    private var pulse = 0f

    private val rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 6000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            rotation = it.animatedValue as Float
            postInvalidateOnAnimation()
        }
    }

    private val pulseAnimator = ValueAnimator.ofFloat(0f, 1f, 0f).apply {
        duration = 1600
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            pulse = it.animatedValue as Float
            postInvalidateOnAnimation()
        }
    }

    init {
        rotationAnimator.start()
        pulseAnimator.start()
    }

    private fun updateAnimatorSpeed() {
        val (rotSpeed, pulseSpeed) = when (state) {
            ReactorState.IDLE -> 6000L to 1600L
            ReactorState.LISTENING -> 2200L to 700L
            ReactorState.THINKING -> 1200L to 500L
            ReactorState.SPEAKING -> 1800L to 350L
        }
        rotationAnimator.duration = rotSpeed
        pulseAnimator.duration = pulseSpeed
    }

    private fun colorFor(state: ReactorState): Int = when (state) {
        ReactorState.IDLE -> Color.parseColor("#00E5FF")
        ReactorState.LISTENING -> Color.parseColor("#39FF88")
        ReactorState.THINKING -> Color.parseColor("#FFC400")
        ReactorState.SPEAKING -> Color.parseColor("#00E5FF")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val baseRadius = minOf(width, height) / 2f * 0.85f
        val color = colorFor(state)

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            this.color = color
            alpha = 180
            pathEffect = DashPathEffect(floatArrayOf(24f, 18f), 0f)
        }
        canvas.save()
        canvas.rotate(rotation, cx, cy)
        canvas.drawCircle(cx, cy, baseRadius, ringPaint)
        canvas.restore()

        val ringPaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            this.color = color
            alpha = 120
        }
        canvas.save()
        canvas.rotate(-rotation * 1.4f, cx, cy)
        canvas.drawCircle(cx, cy, baseRadius * 0.7f, ringPaint2)
        canvas.restore()

        val glowRadius = baseRadius * 0.42f * (1f + pulse * 0.35f)
        val gradient = RadialGradient(
            cx, cy, glowRadius,
            intArrayOf(color, Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = gradient
        }
        canvas.drawCircle(cx, cy, glowRadius, glowPaint)

        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
        }
        canvas.drawCircle(cx, cy, baseRadius * 0.16f, corePaint)
    }

    override fun onDetachedFromWindow() {
        rotationAnimator.cancel()
        pulseAnimator.cancel()
        super.onDetachedFromWindow()
    }
}
