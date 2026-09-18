package com.blindbelt.prototype

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min

class BeltSimulatorView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var cmd = 0
    private var band = 0
    private var startTime = 0L
    private val label = StringBuilder()

    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 32f
        textAlign = Paint.Align.CENTER
    }

    fun update(newCmd: Int, newBand: Int) {
        if (newCmd == cmd && newBand == band) return
        cmd = newCmd
        band = newBand
        startTime = SystemClock.uptimeMillis()
        label.setLength(0)
        when (cmd) {
            0 -> label.append("CLEAR")
            1 -> label.append("STEER_LEFT")
            2 -> label.append("STEER_RIGHT")
            3 -> label.append("STOP")
            4 -> label.append("DROP")
            else -> label.append("FAULT")
        }
        label.append(" band=").append(band)
        invalidate()
    }

    private fun pulsePeriodMs(): Long = when (band) {
        0 -> 1200L
        1 -> 600L
        else -> 250L
    }

    private fun pulse(t: Long, period: Long): Float {
        val phase = (t % period) / period.toFloat()
        return 0.2f + 0.8f * (0.5f + 0.5f * cos(2.0f * PI.toFloat() * phase))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w > 0f && h > 0f) {
            val r = min(w / 4f, h / 3f)
            val cy = h / 2f
            val t = SystemClock.uptimeMillis() - startTime
            val period = pulsePeriodMs()

            var leftB = 0.15f
            var rightB = 0.15f
            when (cmd) {
                0 -> { /* CLEAR: dark */ }
                1 -> leftB = pulse(t, period)
                2 -> rightB = pulse(t, period)
                3 -> { leftB = 1f; rightB = 1f }
                4 -> {
                    val phase = (t % period) / period.toFloat()
                    val dbl = 0.2f + 0.8f * (0.5f + 0.5f * cos(4.0f * PI.toFloat() * phase))
                    leftB = dbl; rightB = dbl
                }
                else -> {
                    val on = (t / 500L) % 2L == 0L
                    leftB = if (on) 1f else 0.2f
                    rightB = if (on) 0.2f else 1f
                }
            }

            drawDisc(canvas, w / 4f, cy, r, leftB)
            drawDisc(canvas, 3f * w / 4f, cy, r, rightB)
            canvas.drawText(label.toString(), w / 2f, h - 12f, textPaint)
        }
        postInvalidateOnAnimation()
    }

    private fun drawDisc(canvas: Canvas, cx: Float, cy: Float, r: Float, b: Float) {
        val v = (b.coerceIn(0f, 1f) * 255).toInt()
        discPaint.color = Color.rgb(v, v, v)
        canvas.drawCircle(cx, cy, r, discPaint)
    }
}
