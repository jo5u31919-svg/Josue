package com.chronos.app.ui.custom

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.sin

enum class OverclockVisualPhase {
    IDLE,
    RUNNING,
    PAUSED_UNSTABLE,
    VICTORY
}

class OverclockOverloadFrameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var visualPhase: OverclockVisualPhase = OverclockVisualPhase.IDLE
    private var progressFraction: Float = 0f
    private var animCycle: Float = 0f
    private var victoryBurst: Float = 0f

    private val borderPath = Path()
    private val laserSegmentA = Path()
    private val laserSegmentB = Path()
    private val pathMeasure = PathMeasure()
    private val borderRect = RectF()
    private val posA = FloatArray(2)
    private val posB = FloatArray(2)

    private val bgAuraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val scanLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val edgeFlashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val frameBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val laserGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val laserCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val flarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glitchBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shockwavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val loopAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2200L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            animCycle = it.animatedValue as Float
            invalidate()
        }
    }

    private var victoryAnimator: ValueAnimator? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!loopAnimator.isStarted) {
            loopAnimator.start()
        }
    }

    override fun onDetachedFromWindow() {
        loopAnimator.cancel()
        victoryAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    fun updateState(phase: OverclockVisualPhase, progress: Float) {
        val enteringVictory = phase == OverclockVisualPhase.VICTORY && this.visualPhase != OverclockVisualPhase.VICTORY
        this.visualPhase = phase
        this.progressFraction = progress.coerceIn(0f, 1f)
        if (enteringVictory) {
            triggerVictoryBurst()
        }
        invalidate()
    }

    private fun triggerVictoryBurst() {
        victoryAnimator?.cancel()
        victoryBurst = 0f
        victoryAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1200L
            interpolator = LinearInterpolator()
            addUpdateListener {
                victoryBurst = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildBorderPath(w.toFloat(), h.toFloat())
    }

    private fun rebuildBorderPath(w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return
        val density = resources.displayMetrics.density
        val inset = 4f * density
        val radius = 24f * density
        borderRect.set(inset, inset, w - inset, h - inset)
        borderPath.reset()
        borderPath.addRoundRect(borderRect, radius, radius, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val density = resources.displayMetrics.density

        val pulseWave = (0.5f + 0.5f * sin(animCycle * Math.PI * 2.0)).toFloat()
        val fastSurge = (0.5f + 0.5f * sin(animCycle * Math.PI * 6.0)).toFloat()

        val primaryColor = when (visualPhase) {
            OverclockVisualPhase.IDLE -> COLOR_CYAN
            OverclockVisualPhase.RUNNING -> COLOR_RED
            OverclockVisualPhase.PAUSED_UNSTABLE -> COLOR_AMBER
            OverclockVisualPhase.VICTORY -> COLOR_VICTORY_GREEN
        }
        val secondaryColor = when (visualPhase) {
            OverclockVisualPhase.IDLE -> COLOR_RED
            OverclockVisualPhase.RUNNING -> COLOR_CYAN
            OverclockVisualPhase.PAUSED_UNSTABLE -> COLOR_RED
            OverclockVisualPhase.VICTORY -> COLOR_CYAN
        }

        // 1. Deep OLED Core Backdrop + Reactive Center Energy Aura
        canvas.drawColor(COLOR_DEEP_BG)
        val auraAlpha = when (visualPhase) {
            OverclockVisualPhase.IDLE -> 34
            OverclockVisualPhase.RUNNING -> (48 + 38 * progressFraction + 22 * pulseWave).toInt()
            OverclockVisualPhase.PAUSED_UNSTABLE -> (40 + 48 * fastSurge).toInt()
            OverclockVisualPhase.VICTORY -> (70 + 30 * pulseWave).toInt()
        }.coerceIn(0, 140)

        bgAuraPaint.shader = RadialGradient(
            w * 0.5f,
            h * 0.44f,
            w * 0.85f,
            intArrayOf(
                Color.argb(auraAlpha, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor)),
                Color.argb(auraAlpha / 2, Color.red(secondaryColor), Color.green(secondaryColor), Color.blue(secondaryColor)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, bgAuraPaint)

        // 2. Subtle Cybernetic Terminal Scanlines
        scanLinePaint.color = Color.argb(14, 255, 255, 255)
        val scanStep = 6f * density
        var sy = 0f
        while (sy < h) {
            canvas.drawLine(0f, sy, w, sy, scanLinePaint)
            sy += scanStep
        }

        // 3. Periodic Edge Overload Flashes along Screen Borders
        val edgeIntensity = when (visualPhase) {
            OverclockVisualPhase.IDLE -> 18 + (12 * pulseWave).toInt()
            OverclockVisualPhase.RUNNING -> (35 + 55 * progressFraction + 50 * fastSurge).toInt()
            OverclockVisualPhase.PAUSED_UNSTABLE -> (45 + 90 * fastSurge).toInt()
            OverclockVisualPhase.VICTORY -> (65 + 35 * pulseWave).toInt()
        }.coerceIn(0, 175)

        val edgeThickness = 36f * density
        // Top & Bottom luminous edge surges
        edgeFlashPaint.shader = LinearGradient(
            0f, 0f, 0f, edgeThickness,
            Color.argb(edgeIntensity, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor)),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, edgeThickness, edgeFlashPaint)

        edgeFlashPaint.shader = LinearGradient(
            0f, h - edgeThickness, 0f, h,
            Color.TRANSPARENT,
            Color.argb(edgeIntensity, Color.red(secondaryColor), Color.green(secondaryColor), Color.blue(secondaryColor)),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, h - edgeThickness, w, h, edgeFlashPaint)

        // Left & Right edge surges
        edgeFlashPaint.shader = LinearGradient(
            0f, 0f, edgeThickness * 0.7f, 0f,
            Color.argb(edgeIntensity / 2, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor)),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, edgeThickness * 0.7f, h, edgeFlashPaint)

        edgeFlashPaint.shader = LinearGradient(
            w - edgeThickness * 0.7f, 0f, w, 0f,
            Color.TRANSPARENT,
            Color.argb(edgeIntensity / 2, Color.red(secondaryColor), Color.green(secondaryColor), Color.blue(secondaryColor)),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(w - edgeThickness * 0.7f, 0f, w, h, edgeFlashPaint)

        // 4. Perimeter Laser Overload Waves
        frameBasePaint.color = Color.argb(55, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor))
        frameBasePaint.strokeWidth = 1.5f * density
        canvas.drawPath(borderPath, frameBasePaint)

        pathMeasure.setPath(borderPath, true)
        val totalLen = pathMeasure.length
        if (totalLen > 0f) {
            val segLen = totalLen * (if (visualPhase == OverclockVisualPhase.RUNNING) 0.24f else 0.16f)
            val headA = (animCycle * totalLen) % totalLen
            val headB = ((animCycle + 0.5f) * totalLen) % totalLen

            extractWrappedSegment(totalLen, headA - segLen, headA, laserSegmentA)
            extractWrappedSegment(totalLen, headB - segLen, headB, laserSegmentB)

            drawLaserSegment(canvas, laserSegmentA, primaryColor, density)
            drawLaserSegment(canvas, laserSegmentB, secondaryColor, density)

            if (pathMeasure.getPosTan(headA, posA, null)) {
                drawFlare(canvas, posA[0], posA[1], primaryColor, (14f + 4f * fastSurge) * density)
            }
            if (pathMeasure.getPosTan(headB, posB, null)) {
                drawFlare(canvas, posB[0], posB[1], secondaryColor, (14f + 4f * fastSurge) * density)
            }
        }

        // 5. Instability Glitch Bars when Paused
        if (visualPhase == OverclockVisualPhase.PAUSED_UNSTABLE && fastSurge > 0.55f) {
            glitchBarPaint.color = Color.argb((70 * fastSurge).toInt(), Color.red(COLOR_RED), Color.green(COLOR_RED), Color.blue(COLOR_RED))
            val barY1 = h * (0.22f + 0.08f * animCycle)
            val barY2 = h * (0.76f - 0.06f * animCycle)
            canvas.drawRect(0f, barY1, w * 0.65f, barY1 + 3f * density, glitchBarPaint)
            canvas.drawRect(w * 0.35f, barY2, w, barY2 + 2.5f * density, glitchBarPaint)
        }

        // 6. Victory Shockwave Burst
        if (visualPhase == OverclockVisualPhase.VICTORY && victoryBurst in 0.01f..0.99f) {
            val maxR = maxOf(w, h) * 0.72f
            val r = maxR * victoryBurst
            val alpha = ((1f - abs(victoryBurst)) * 210).toInt().coerceIn(0, 210)
            shockwavePaint.color = Color.argb(alpha, Color.red(COLOR_VICTORY_GREEN), Color.green(COLOR_VICTORY_GREEN), Color.blue(COLOR_VICTORY_GREEN))
            shockwavePaint.strokeWidth = (6f * (1f - victoryBurst) + 1.5f) * density
            canvas.drawCircle(w * 0.5f, h * 0.45f, r, shockwavePaint)
        }
    }

    private fun extractWrappedSegment(totalLen: Float, startRaw: Float, endRaw: Float, outPath: Path) {
        outPath.reset()
        if (startRaw >= 0f) {
            pathMeasure.getSegment(startRaw, endRaw, outPath, true)
        } else {
            val wrappedStart = totalLen + startRaw
            pathMeasure.getSegment(wrappedStart, totalLen, outPath, true)
            val second = Path()
            pathMeasure.getSegment(0f, endRaw, second, true)
            outPath.addPath(second)
        }
    }

    private fun drawLaserSegment(canvas: Canvas, path: Path, color: Int, density: Float) {
        laserGlowPaint.color = Color.argb(95, Color.red(color), Color.green(color), Color.blue(color))
        laserGlowPaint.strokeWidth = 7.5f * density
        canvas.drawPath(path, laserGlowPaint)

        laserCorePaint.color = color
        laserCorePaint.strokeWidth = 2.6f * density
        canvas.drawPath(path, laserCorePaint)
    }

    private fun drawFlare(canvas: Canvas, x: Float, y: Float, color: Int, radius: Float) {
        flarePaint.shader = RadialGradient(
            x, y, radius,
            intArrayOf(
                Color.WHITE,
                Color.argb(200, Color.red(color), Color.green(color), Color.blue(color)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.35f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(x, y, radius, flarePaint)
    }

    companion object {
        private const val COLOR_DEEP_BG = 0xFF05050B.toInt()
        private const val COLOR_CYAN = 0xFF00F0FF.toInt()
        private const val COLOR_RED = 0xFFFF1E42.toInt()
        private const val COLOR_AMBER = 0xFFFFB800.toInt()
        private const val COLOR_VICTORY_GREEN = 0xFF00FF9F.toInt()
    }
}
