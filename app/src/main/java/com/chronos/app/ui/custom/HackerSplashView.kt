package com.chronos.app.ui.custom

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.res.ResourcesCompat
import com.chronos.app.R
import com.chronos.app.theme.CyberThemeId
import com.chronos.app.theme.ResolvedCyberPalette
import kotlin.math.floor

class HackerSplashView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var themePalette: ResolvedCyberPalette = CyberThemeId.CYBERPUNK_CLASSIC.resolve(true)
    private var progress: Float = 0f
    private var dissolveProgress: Float = 0f
    private var bootAnimator: ValueAnimator? = null
    private var dissolveAnimator: ValueAnimator? = null

    private val dotCount = 18
    private val monoTypeface = ResourcesCompat.getFont(context, R.font.jetbrains_mono)
    private val displayTypeface = ResourcesCompat.getFont(context, R.font.orbitron)

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = themePalette.backgroundColor
    }

    private val ambientAuraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val spatialRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val dotInactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = themePalette.surfaceElevatedColor
    }

    private val dotActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = themePalette.primaryNeon
    }

    private val dotGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = themePalette.textPrimary
        textAlign = Paint.Align.CENTER
        typeface = displayTypeface
        letterSpacing = 0.16f
    }

    private val consolePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = themePalette.primaryNeon
        textAlign = Paint.Align.CENTER
        typeface = monoTypeface
    }

    private val statusLines = listOf(
        "[0x01] INIT CHRONOS_KERNEL...",
        "[0x02] SYNCING SPATIAL_HUD...",
        "[0x03] CALIBRATING LASER_ARRAY...",
        "[0x04] SYSTEM READY // ACCESS GRANTED"
    )

    var onSplashFinished: (() -> Unit)? = null

    fun applyTheme(palette: ResolvedCyberPalette) {
        themePalette = palette
        bgPaint.color = palette.backgroundColor
        dotInactivePaint.color = palette.surfaceElevatedColor
        dotActivePaint.color = palette.primaryNeon
        titlePaint.color = palette.textPrimary
        consolePaint.color = palette.primaryNeon
        invalidate()
    }

    fun startBootSequence() {
        bootAnimator?.cancel()
        dissolveAnimator?.cancel()
        dissolveProgress = 0f
        progress = 0f
        alpha = 1f
        visibility = VISIBLE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setRenderEffect(null)
        }

        bootAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1750L
            interpolator = DecelerateInterpolator(1.1f)
            addUpdateListener { anim ->
                progress = anim.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    startBlurDissolve()
                }
            })
            start()
        }
    }

    private fun startBlurDissolve() {
        dissolveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 550L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val v = anim.animatedValue as Float
                dissolveProgress = v
                alpha = (1f - v).coerceIn(0f, 1f)
                scaleX = 1f + v * 0.08f
                scaleY = 1f + v * 0.08f
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val blurRadius = (v * 34f).coerceAtLeast(0.1f)
                    setRenderEffect(
                        RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
                    )
                }
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    visibility = GONE
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setRenderEffect(null)
                    }
                    onSplashFinished?.invoke()
                }
            })
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val density = resources.displayMetrics.density
        val cx = w / 2f
        val cy = h / 2f

        canvas.drawRect(0f, 0f, w, h, bgPaint)

        val auraRadius = (w * 0.55f).coerceAtLeast(180f * density)
        val pColor = themePalette.primaryNeon
        ambientAuraPaint.shader = RadialGradient(
            cx,
            cy,
            auraRadius,
            intArrayOf(
                Color.argb((38 * (1f - dissolveProgress)).toInt(), Color.red(pColor), Color.green(pColor), Color.blue(pColor)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, auraRadius, ambientAuraPaint)

        val ringR = (108f + progress * 32f) * density
        spatialRingPaint.strokeWidth = 1.1f * density
        spatialRingPaint.color = Color.argb(
            ((40 + 55 * progress) * (1f - dissolveProgress)).toInt(),
            Color.red(pColor),
            Color.green(pColor),
            Color.blue(pColor)
        )
        canvas.drawCircle(cx, cy, ringR, spatialRingPaint)

        titlePaint.textSize = 21f * density
        canvas.drawText("CHRONOS SYSTEM", cx, cy - 54f * density, titlePaint)

        val barWidth = (w * 0.72f).coerceAtMost(320f * density)
        val startX = cx - barWidth / 2f
        val step = barWidth / (dotCount - 1).coerceAtLeast(1)
        val baseRadius = 4.2f * density
        val activeThreshold = progress * dotCount

        for (i in 0 until dotCount) {
            val dotX = startX + i * step
            val dotY = cy
            val dotIntensity = (activeThreshold - i).coerceIn(0f, 1f)

            if (dotIntensity > 0f) {
                val glowRadius = (13f + 13f * dissolveProgress) * density * dotIntensity
                val dotColor = if (i > dotCount * 0.72f) themePalette.secondaryNeon else themePalette.primaryNeon
                dotGlowPaint.shader = RadialGradient(
                    dotX,
                    dotY,
                    glowRadius.coerceAtLeast(1f),
                    intArrayOf(
                        Color.argb((185 * dotIntensity).toInt(), Color.red(dotColor), Color.green(dotColor), Color.blue(dotColor)),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(dotX, dotY, glowRadius, dotGlowPaint)

                dotActivePaint.color = dotColor
                dotActivePaint.alpha = (255 * dotIntensity).toInt()
                canvas.drawCircle(dotX, dotY, baseRadius * (0.85f + 0.25f * dotIntensity), dotActivePaint)
            } else {
                canvas.drawCircle(dotX, dotY, baseRadius * 0.75f, dotInactivePaint)
            }
        }

        val percent = (progress * 100).toInt().coerceIn(0, 100)
        val lineIndex = floor(progress * (statusLines.size - 0.01f)).toInt().coerceIn(0, statusLines.lastIndex)
        consolePaint.textSize = 11f * density
        consolePaint.alpha = ((1f - dissolveProgress) * 235).toInt()
        canvas.drawText("${statusLines[lineIndex]} [$percent%]", cx, cy + 48f * density, consolePaint)
    }

    override fun onDetachedFromWindow() {
        bootAnimator?.cancel()
        dissolveAnimator?.cancel()
        super.onDetachedFromWindow()
    }
}
