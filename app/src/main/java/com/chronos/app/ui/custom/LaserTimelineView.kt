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
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.res.ResourcesCompat
import com.chronos.app.R
import com.chronos.app.domain.ChronosTimeEngine
import com.chronos.app.domain.DayPhase
import com.chronos.app.domain.ProjectedTimelineNode
import com.chronos.app.feedback.CyberFeedbackManager
import com.chronos.app.theme.CyberThemeId
import com.chronos.app.theme.ResolvedCyberPalette
import kotlin.math.hypot

class LaserTimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class RenderedNodeHit(
        val node: ProjectedTimelineNode,
        val cx: Float,
        val cy: Float
    )

    private var themePalette: ResolvedCyberPalette = CyberThemeId.CYBERPUNK_CLASSIC.resolve(true)
    private var dayProgress: Float = 0.5f
    private var dayPhase: DayPhase = DayPhase.NIGHT
    private var projectedNodes: List<ProjectedTimelineNode> = emptyList()
    private val renderedHits = mutableListOf<RenderedNodeHit>()
    private var pulsePhase: Float = 0f

    private var downX = 0f
    private var downY = 0f

    var onNodeSelected: ((ProjectedTimelineNode) -> Unit)? = null

    private val monoTypeface = ResourcesCompat.getFont(context, R.font.jetbrains_mono)

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val trackRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = monoTypeface
    }

    private val laserGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val laserCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val whiteHotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val flarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val durationLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val nodeDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val nodeRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val nodeNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = monoTypeface
    }

    private val trackRect = RectF()
    private val laserRect = RectF()

    private val pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1600L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = LinearInterpolator()
        addUpdateListener {
            pulsePhase = it.animatedValue as Float
            invalidate()
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

    fun updateTimeline(
        progress: Float,
        phase: DayPhase,
        nodes: List<ProjectedTimelineNode>,
        palette: ResolvedCyberPalette
    ) {
        dayProgress = progress.coerceIn(0f, 1f)
        dayPhase = phase
        projectedNodes = nodes
        themePalette = palette
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val density = resources.displayMetrics.density
        val padH = 18f * density
        val trackLeft = padH
        val trackRight = w - padH
        val trackWidth = (trackRight - trackLeft).coerceAtLeast(1f)
        val laserY = h * 0.65f
        val trackHeight = 9f * density

        // 1. HarmonyOS Frosted Glass Capsule Track + Specular Rim
        trackPaint.color = themePalette.surfaceElevatedColor
        trackRimPaint.color = themePalette.subtleBorderColor
        trackRect.set(trackLeft, laserY - trackHeight / 2f, trackRight, laserY + trackHeight / 2f)
        canvas.drawRoundRect(trackRect, trackHeight / 2f, trackHeight / 2f, trackPaint)
        canvas.drawRoundRect(trackRect, trackHeight / 2f, trackHeight / 2f, trackRimPaint)

        // 2. Hour Markers (00h, 06h, 12h, 18h, 24h)
        labelPaint.textSize = 9.5f * density
        labelPaint.color = themePalette.textSecondary
        tickPaint.color = themePalette.textMuted
        val markers = listOf(0f to "00h", 0.25f to "06h", 0.5f to "12h", 0.75f to "18h", 1f to "24h")
        markers.forEach { (frac, label) ->
            val x = trackLeft + trackWidth * frac
            canvas.drawLine(x, laserY + 9f * density, x, laserY + 15f * density, tickPaint)
            canvas.drawText(label, x, laserY + 27f * density, labelPaint)
        }

        // 3. Phase-Dynamic Luminous Laser Beam
        val (phaseStart, phaseEnd) = ChronosTimeEngine.laserColorsForPhase(dayPhase, themePalette)
        val tipX = trackLeft + trackWidth * dayProgress.coerceAtLeast(0.015f)

        val glowAlphaEnd = if (themePalette.isDark) 150 else 95
        val glowAlphaStart = if (themePalette.isDark) 55 else 35
        val glowHalfH = (10f + 3.5f * pulsePhase) * density
        laserGlowPaint.shader = LinearGradient(
            trackLeft,
            laserY,
            tipX,
            laserY,
            intArrayOf(
                Color.argb(glowAlphaStart, Color.red(phaseStart), Color.green(phaseStart), Color.blue(phaseStart)),
                Color.argb(glowAlphaEnd, Color.red(phaseEnd), Color.green(phaseEnd), Color.blue(phaseEnd))
            ),
            null,
            Shader.TileMode.CLAMP
        )
        laserRect.set(trackLeft, laserY - glowHalfH, tipX, laserY + glowHalfH)
        canvas.drawRoundRect(laserRect, glowHalfH, glowHalfH, laserGlowPaint)

        laserCorePaint.shader = LinearGradient(
            trackLeft,
            laserY,
            tipX,
            laserY,
            phaseStart,
            phaseEnd,
            Shader.TileMode.CLAMP
        )
        laserRect.set(trackLeft, laserY - trackHeight / 2f, tipX, laserY + trackHeight / 2f)
        canvas.drawRoundRect(laserRect, trackHeight / 2f, trackHeight / 2f, laserCorePaint)

        whiteHotPaint.alpha = if (themePalette.isDark) (185 + (65 * pulsePhase)).toInt() else 210
        val filamentHalfH = 1.4f * density
        laserRect.set(trackLeft + 3f * density, laserY - filamentHalfH, tipX, laserY + filamentHalfH)
        canvas.drawRoundRect(laserRect, filamentHalfH, filamentHalfH, whiteHotPaint)

        val flareRadius = (18f + 6f * pulsePhase) * density
        flarePaint.shader = RadialGradient(
            tipX,
            laserY,
            flareRadius,
            intArrayOf(
                Color.WHITE,
                Color.argb(if (themePalette.isDark) 215 else 175, Color.red(phaseEnd), Color.green(phaseEnd), Color.blue(phaseEnd)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.36f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(tipX, laserY, flareRadius, flarePaint)

        // 4. Projected Chronological Timer Nodes + Luminous Capsule Duration Lines
        renderedHits.clear()
        val nodeTrackY = h * 0.29f
        nodeNamePaint.textSize = 9.5f * density

        projectedNodes.forEachIndexed { index, node ->
            val startX = trackLeft + trackWidth * node.startFraction
            val endX = (trackLeft + trackWidth * (node.startFraction + node.durationFraction))
                .coerceAtLeast(startX + 14f * density)
                .coerceAtMost(trackRight)

            val nodeColor = if (node.isCurrentlyActiveWindow) {
                themePalette.secondaryNeon
            } else {
                themePalette.primaryNeon
            }

            durationLinePaint.color = Color.argb(if (themePalette.isDark) 55 else 42, Color.red(nodeColor), Color.green(nodeColor), Color.blue(nodeColor))
            durationLinePaint.strokeWidth = 7f * density
            canvas.drawLine(startX, nodeTrackY, endX, nodeTrackY, durationLinePaint)

            durationLinePaint.color = Color.argb(220, Color.red(nodeColor), Color.green(nodeColor), Color.blue(nodeColor))
            durationLinePaint.strokeWidth = 3f * density
            canvas.drawLine(startX, nodeTrackY, endX, nodeTrackY, durationLinePaint)

            durationLinePaint.color = Color.argb(120, Color.red(nodeColor), Color.green(nodeColor), Color.blue(nodeColor))
            durationLinePaint.strokeWidth = 1.4f * density
            canvas.drawLine(startX, nodeTrackY + 7f * density, startX, laserY - 7f * density, durationLinePaint)

            val haloR = if (node.isCurrentlyActiveWindow) (15f + 4.5f * pulsePhase) * density else 11.5f * density
            flarePaint.shader = RadialGradient(
                startX,
                nodeTrackY,
                haloR,
                intArrayOf(
                    Color.argb(if (themePalette.isDark) 175 else 120, Color.red(nodeColor), Color.green(nodeColor), Color.blue(nodeColor)),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(startX, nodeTrackY, haloR, flarePaint)

            val dotR = 6f * density
            nodeDotPaint.color = nodeColor
            canvas.drawCircle(startX, nodeTrackY, dotR, nodeDotPaint)

            nodeRingPaint.color = if (themePalette.isDark) Color.WHITE else themePalette.surfaceColor
            nodeRingPaint.strokeWidth = 1.8f * density
            canvas.drawCircle(startX, nodeTrackY, dotR, nodeRingPaint)

            val labelOffsetY = if (index % 2 == 0) -12f * density else -22f * density
            val shortName = if (node.timer.name.length > 9) {
                node.timer.name.take(8) + "…"
            } else {
                node.timer.name
            }
            nodeNamePaint.color = themePalette.textPrimary
            canvas.drawText(
                shortName,
                startX.coerceIn(trackLeft + 24f * density, trackRight - 24f * density),
                nodeTrackY + labelOffsetY,
                nodeNamePaint
            )

            renderedHits.add(RenderedNodeHit(node, startX, nodeTrackY))
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                val distMoved = hypot(event.x - downX, event.y - downY)
                val touchRadius = 28f * resources.displayMetrics.density
                if (distMoved <= touchRadius) {
                    val hit = renderedHits.minByOrNull { hit ->
                        hypot(event.x - hit.cx, event.y - hit.cy)
                    }
                    if (hit != null && hypot(event.x - hit.cx, event.y - hit.cy) <= 32f * resources.displayMetrics.density) {
                        CyberFeedbackManager.getInstance(context).playBeepProgress()
                        performClick()
                        onNodeSelected?.invoke(hit.node)
                        return true
                    }
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }
}
