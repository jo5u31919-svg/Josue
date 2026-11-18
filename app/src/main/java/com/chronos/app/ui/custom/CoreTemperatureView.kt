package com.chronos.app.ui.custom

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.res.ResourcesCompat
import com.chronos.app.R
import java.util.Locale
import kotlin.math.sin

class CoreTemperatureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var temperatureProgress: Float = 0f
    private var isOverclockActive: Boolean = false
    private var isUnstable: Boolean = false
    private var pulsePhase: Float = 0f

    private val monoTypeface = ResourcesCompat.getFont(context, R.font.jetbrains_mono)

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val trackBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.3f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = monoTypeface
    }

    private val trackRect = RectF()
    private val fillRect = RectF()
    private val glowRect = RectF()

    private val pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1100L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            pulsePhase = it.animatedValue as Float
            if (isOverclockActive || isUnstable) {
                invalidate()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!pulseAnimator.isStarted) {
            pulseAnimator.start()
        }
    }

    override fun onDetachedFromWindow() {
        pulseAnimator.cancel()
        super.onDetachedFromWindow()
    }

    fun updateTemperature(progress: Float, active: Boolean, unstable: Boolean) {
        temperatureProgress = progress.coerceIn(0f, 1f)
        isOverclockActive = active
        isUnstable = unstable
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val density = resources.displayMetrics.density
        val padH = 4f * density
        val barTop = 24f * density
        val barHeight = 14f * density
        val barLeft = padH
        val barRight = w - padH
        val barWidth = (barRight - barLeft).coerceAtLeast(1f)
        val radius = barHeight / 2f

        val tempCelsius = 36.0f + (temperatureProgress * 63.8f)
        val tempLabel = String.format(Locale.US, "%.1f°C", tempCelsius)
        val statusText = when {
            isUnstable -> "INESTABLE"
            temperatureProgress >= 0.85f -> "SOBRECARGA MÁX"
            temperatureProgress >= 0.50f -> "ALTO RENDIMIENTO"
            isOverclockActive -> "CALENTANDO"
            else -> "NOMINAL"
        }

        val activeHeadColor = when {
            isUnstable -> 0xFFFFB800.toInt()
            temperatureProgress >= 0.72f -> 0xFFFF1E42.toInt()
            temperatureProgress >= 0.40f -> 0xFFFF8800.toInt()
            else -> 0xFF00F0FF.toInt()
        }

        // Top Header Readout
        textPaint.textSize = 10.5f * density
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = 0xFFA6B3D1.toInt()
        canvas.drawText("TEMP. NÚCLEO // $statusText", barLeft, 15f * density, textPaint)

        textPaint.textSize = 12f * density
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.color = activeHeadColor
        canvas.drawText(tempLabel, barRight, 15f * density, textPaint)

        // Track Background
        trackRect.set(barLeft, barTop, barRight, barTop + barHeight)
        trackPaint.color = 0xFF141826.toInt()
        trackBorderPaint.color = Color.argb(90, Color.red(activeHeadColor), Color.green(activeHeadColor), Color.blue(activeHeadColor))
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint)
        canvas.drawRoundRect(trackRect, radius, radius, trackBorderPaint)

        // Thermal Fill
        val flicker = if (isOverclockActive) (0.012f * sin(pulsePhase * Math.PI * 2.0)).toFloat() else 0f
        val visualFraction = (temperatureProgress + flicker).coerceIn(0.03f, 1f)
        val fillRight = barLeft + barWidth * visualFraction

        glowRect.set(barLeft, barTop - 3f * density, fillRight, barTop + barHeight + 3f * density)
        glowPaint.shader = LinearGradient(
            barLeft, barTop, fillRight, barTop,
            intArrayOf(
                Color.argb(40, 0, 240, 255),
                Color.argb(if (isOverclockActive) 130 else 80, Color.red(activeHeadColor), Color.green(activeHeadColor), Color.blue(activeHeadColor))
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(glowRect, radius + 3f * density, radius + 3f * density, glowPaint)

        fillRect.set(barLeft, barTop, fillRight, barTop + barHeight)
        fillPaint.shader = LinearGradient(
            barLeft, barTop, barRight, barTop,
            intArrayOf(0xFF00F0FF.toInt(), 0xFFFFB800.toInt(), 0xFFFF1E42.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(fillRect, radius, radius, fillPaint)

        // Segmented Cyber Ticks across Bar
        tickPaint.color = 0xCC05050B.toInt()
        val segments = 10
        for (i in 1 until segments) {
            val tx = barLeft + (barWidth * i / segments.toFloat())
            canvas.drawLine(tx, barTop + 2f * density, tx, barTop + barHeight - 2f * density, tickPaint)
        }

        // Plasma Tip Flare
        val tipY = barTop + barHeight / 2f
        val flareR = (11f + if (isOverclockActive) 3f * sin(pulsePhase * Math.PI * 2.0).toFloat() else 0f) * density
        glowPaint.shader = RadialGradient(
            fillRight, tipY, flareR,
            intArrayOf(
                Color.WHITE,
                Color.argb(210, Color.red(activeHeadColor), Color.green(activeHeadColor), Color.blue(activeHeadColor)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(fillRight, tipY, flareR, glowPaint)

        // Bottom Scale Labels
        textPaint.textSize = 9f * density
        textPaint.color = 0xFF657091.toInt()
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("36°C", barLeft, barTop + barHeight + 13f * density, textPaint)
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("68°C", (barLeft + barRight) / 2f, barTop + barHeight + 13f * density, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("100°C MAX", barRight, barTop + barHeight + 13f * density, textPaint)
    }
}
