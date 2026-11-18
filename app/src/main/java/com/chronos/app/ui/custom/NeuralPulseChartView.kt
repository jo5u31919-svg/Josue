package com.chronos.app.ui.custom

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.core.content.res.ResourcesCompat
import com.chronos.app.R
import com.chronos.app.data.local.PerformanceCategory
import com.chronos.app.domain.NeuralDayPoint
import com.chronos.app.domain.NeuralStatsCalculator
import com.chronos.app.theme.CyberThemeId
import com.chronos.app.theme.ResolvedCyberPalette
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

class NeuralPulseChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var palette: ResolvedCyberPalette = CyberThemeId.CYBERPUNK_CLASSIC.resolve(true)
    private var dayPoints: List<NeuralDayPoint> = emptyList()
    private var highlightedCategory: PerformanceCategory? = null
    private var selectedDayIndex: Int = -1
    private var maxScaleMinutes: Float = 60f

    private var revealProgress: Float = 0f
    private var flowPhase: Float = 0f

    private var downX: Float = 0f
    private var downY: Float = 0f

    private val monoTypeface = ResourcesCompat.getFont(context, R.font.jetbrains_mono)

    private val gridLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
    }

    private val dashedGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.1f
        pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
    }

    private val axisLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = monoTypeface
        textAlign = Paint.Align.CENTER
    }

    private val yAxisLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = monoTypeface
        textAlign = Paint.Align.RIGHT
    }

    private val scanBeamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val glowOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val glowMidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val coreLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val whiteFilamentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
    }

    private val areaFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val nodeRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val beadGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val tooltipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val tooltipBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f
    }

    private val tooltipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = monoTypeface
        textAlign = Paint.Align.LEFT
    }

    private val categoryPaths = mutableMapOf<PerformanceCategory, Path>()
    private val categoryPeakPoints = mutableMapOf<PerformanceCategory, List<Pair<Float, Float>>>()
    private val partialPath = Path()
    private val areaPath = Path()
    private val pathMeasure = PathMeasure()
    private val beadPos = FloatArray(2)
    private val tooltipRect = RectF()
    private val dayColumnX = FloatArray(7)

    private var plotLeft = 0f
    private var plotRight = 0f
    private var plotTop = 0f
    private var plotBottom = 0f

    private val revealAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1050L
        interpolator = DecelerateInterpolator(1.7f)
        addUpdateListener {
            revealProgress = it.animatedValue as Float
            invalidate()
        }
    }

    private val flowAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2800L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            flowPhase = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!flowAnimator.isStarted) {
            flowAnimator.start()
        }
        if (revealProgress <= 0f && dayPoints.isNotEmpty()) {
            replayWaveAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        revealAnimator.cancel()
        flowAnimator.cancel()
        super.onDetachedFromWindow()
    }

    fun setChartData(
        days: List<NeuralDayPoint>,
        palette: ResolvedCyberPalette,
        highlightedCategory: PerformanceCategory? = null,
        animateWaves: Boolean = false
    ) {
        val periodChanged = this.dayPoints.map { it.dateKey } != days.map { it.dateKey }
        this.dayPoints = days
        this.palette = palette
        this.highlightedCategory = highlightedCategory

        if (selectedDayIndex !in days.indices) {
            selectedDayIndex = days.indexOfFirst { it.isToday }.takeIf { it >= 0 } ?: (days.size - 1)
        }

        val maxObserved = days.maxOfOrNull { day ->
            day.minutesByCategory.values.maxOrNull() ?: 0f
        } ?: 0f
        maxScaleMinutes = max(30f, ceil((maxObserved * 1.18f) / 15f) * 15f)

        rebuildGeometry()
        if (animateWaves || periodChanged || revealProgress == 0f) {
            replayWaveAnimation()
        } else {
            invalidate()
        }
    }

    fun replayWaveAnimation() {
        revealAnimator.cancel()
        revealProgress = 0f
        if (isAttachedToWindow) {
            revealAnimator.start()
        } else {
            revealProgress = 1f
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildGeometry()
    }

    private fun rebuildGeometry() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f || dayPoints.isEmpty()) return

        val density = resources.displayMetrics.density
        plotLeft = 40f * density
        plotRight = w - 16f * density
        plotTop = 36f * density
        plotBottom = h - 30f * density

        val plotW = (plotRight - plotLeft).coerceAtLeast(1f)
        val plotH = (plotBottom - plotTop).coerceAtLeast(1f)
        val count = dayPoints.size
        val stepX = if (count > 1) plotW / (count - 1).toFloat() else plotW

        for (i in 0 until minOf(count, dayColumnX.size)) {
            dayColumnX[i] = plotLeft + i * stepX
        }

        categoryPaths.clear()
        categoryPeakPoints.clear()

        PerformanceCategory.entries.forEachIndexed { catIdx, category ->
            val path = Path()
            val peaks = mutableListOf<Pair<Float, Float>>()

            // Micro-offset resting baseline so all 4 neon ECG waves remain distinct at 0m
            val baselineOffset = (3 - catIdx) * 2.4f * density
            val baseY = plotBottom - baselineOffset
            val usableH = (plotH - 12f * density).coerceAtLeast(16f * density)

            val startVal = dayPoints.first().minutesByCategory[category] ?: 0f
            val firstAmp = (startVal / maxScaleMinutes).coerceIn(0f, 1f)
            val firstPeakY = if (startVal > 0f) {
                baseY - (firstAmp * usableH).coerceAtLeast(8f * density)
            } else {
                baseY - 2.5f * density
            }

            path.moveTo(plotLeft, baseY)
            if (count == 1) {
                peaks.add(plotLeft to firstPeakY)
                path.lineTo(plotRight, firstPeakY)
            } else {
                for (i in 0 until count) {
                    val xCenter = dayColumnX[i]
                    val mins = dayPoints[i].minutesByCategory[category] ?: 0f
                    val amp = (mins / maxScaleMinutes).coerceIn(0f, 1f)
                    val peakY = if (mins > 0f) {
                        baseY - (amp * usableH).coerceAtLeast(8f * density)
                    } else {
                        baseY - 2.6f * density
                    }
                    peaks.add(xCenter to peakY)

                    if (i == 0) {
                        // First node: rise cleanly into ECG peak and recover to baseline
                        val qX = (xCenter + stepX * 0.08f).coerceAtMost(plotRight)
                        val rX = (xCenter + stepX * 0.14f).coerceAtMost(plotRight)
                        val sX = (xCenter + stepX * 0.22f).coerceAtMost(plotRight)
                        val tX = (xCenter + stepX * 0.34f).coerceAtMost(plotRight)
                        val qDip = if (mins > 0f) 4f * density else 1f * density
                        path.lineTo(xCenter, peakY)
                        path.lineTo(qX, (baseY + qDip).coerceAtMost(plotBottom))
                        path.lineTo(rX, baseY - (baseY - peakY) * 0.28f)
                        path.lineTo(sX, baseY)
                        path.lineTo(tX, baseY)
                    } else {
                        val prevX = dayColumnX[i - 1]
                        val segW = xCenter - prevX
                        val leadStart = prevX + segW * 0.48f
                        val pStart = prevX + segW * 0.60f
                        val pMid = prevX + segW * 0.67f
                        val pEnd = prevX + segW * 0.74f
                        val qX = prevX + segW * 0.84f
                        val sX = xCenter + segW * 0.08f
                        val tMid = xCenter + segW * 0.18f
                        val tEnd = xCenter + segW * 0.30f

                        val pLift = if (mins > 0f) max(3.5f * density, (baseY - peakY) * 0.14f) else 1.2f * density
                        val qDip = if (mins > 0f) 4.5f * density else 1.1f * density
                        val sDip = if (mins > 0f) 5.5f * density else 1.2f * density

                        path.lineTo(leadStart, baseY)
                        path.cubicTo(pStart, baseY, pMid, baseY - pLift, pEnd, baseY)
                        path.lineTo(qX, (baseY + qDip).coerceAtMost(plotBottom))
                        path.lineTo(xCenter, peakY)

                        if (i < count - 1) {
                            path.lineTo(sX.coerceAtMost(plotRight), (baseY + sDip).coerceAtMost(plotBottom))
                            path.cubicTo(
                                (xCenter + segW * 0.12f).coerceAtMost(plotRight),
                                baseY,
                                tMid.coerceAtMost(plotRight),
                                baseY - pLift * 0.8f,
                                tEnd.coerceAtMost(plotRight),
                                baseY
                            )
                        } else {
                            path.lineTo(xCenter, peakY)
                        }
                    }
                }
            }
            categoryPaths[category] = path
            categoryPeakPoints[category] = peaks
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f || dayPoints.isEmpty()) return

        val density = resources.displayMetrics.density
        if (plotRight <= plotLeft) {
            rebuildGeometry()
        }

        drawTerminalGrid(canvas, density)
        drawScannerBeam(canvas, density)
        drawNeuralWaveforms(canvas, density)
        drawDayNodesAndTooltip(canvas, density)
    }

    private fun drawTerminalGrid(canvas: Canvas, density: Float) {
        gridLinePaint.color = Color.argb(
            if (palette.isDark) 34 else 28,
            Color.red(palette.textMuted),
            Color.green(palette.textMuted),
            Color.blue(palette.textMuted)
        )
        dashedGridPaint.color = Color.argb(
            if (palette.isDark) 45 else 34,
            Color.red(palette.primaryNeon),
            Color.green(palette.primaryNeon),
            Color.blue(palette.primaryNeon)
        )

        yAxisLabelPaint.textSize = 9.5f * density
        yAxisLabelPaint.color = palette.textMuted

        // Horizontal telemetry levels: 0%, 50%, 100%
        val levels = listOf(0f, 0.5f, 1f)
        levels.forEach { frac ->
            val y = plotBottom - (plotBottom - plotTop) * frac
            if (frac == 0f) {
                canvas.drawLine(plotLeft, y, plotRight, y, gridLinePaint)
            } else {
                canvas.drawLine(plotLeft, y, plotRight, y, dashedGridPaint)
            }
            val labelMinutes = (maxScaleMinutes * frac)
            val labelText = NeuralStatsCalculator.formatMinutesCompact(labelMinutes)
            canvas.drawText(labelText, plotLeft - 7f * density, y + 3f * density, yAxisLabelPaint)
        }

        // Vertical day terminal columns + labels
        axisLabelPaint.textSize = 10f * density
        dayPoints.forEachIndexed { idx, point ->
            if (idx >= dayColumnX.size) return@forEachIndexed
            val x = dayColumnX[idx]
            val isSelected = idx == selectedDayIndex

            if (isSelected) {
                gridLinePaint.color = Color.argb(
                    if (palette.isDark) 115 else 95,
                    Color.red(palette.primaryNeon),
                    Color.green(palette.primaryNeon),
                    Color.blue(palette.primaryNeon)
                )
                gridLinePaint.strokeWidth = 1.6f * density
            } else {
                gridLinePaint.color = Color.argb(
                    if (palette.isDark) 30 else 24,
                    Color.red(palette.textMuted),
                    Color.green(palette.textMuted),
                    Color.blue(palette.textMuted)
                )
                gridLinePaint.strokeWidth = 1f * density
            }
            canvas.drawLine(x, plotTop, x, plotBottom, gridLinePaint)

            axisLabelPaint.color = when {
                isSelected -> palette.primaryNeon
                point.isToday -> palette.secondaryNeon
                else -> palette.textSecondary
            }
            val suffix = if (point.isToday) "•" else ""
            canvas.drawText("${point.dayLabel}$suffix", x, plotBottom + 18f * density, axisLabelPaint)
        }
    }

    private fun drawScannerBeam(canvas: Canvas, density: Float) {
        val beamWidth = 42f * density
        val beamCenter = plotLeft + (plotRight - plotLeft) * flowPhase
        val beamLeft = (beamCenter - beamWidth / 2f).coerceAtLeast(plotLeft)
        val beamRight = (beamCenter + beamWidth / 2f).coerceAtMost(plotRight)
        if (beamRight <= beamLeft) return

        val beamAlpha = if (palette.isDark) 28 else 20
        val accent = highlightedCategory?.let { colorForCategory(it, palette) } ?: palette.primaryNeon
        scanBeamPaint.shader = LinearGradient(
            beamLeft,
            plotTop,
            beamRight,
            plotTop,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(beamAlpha, Color.red(accent), Color.green(accent), Color.blue(accent)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(beamLeft, plotTop, beamRight, plotBottom, scanBeamPaint)
    }

    private fun drawNeuralWaveforms(canvas: Canvas, density: Float) {
        val drawOrder = if (highlightedCategory != null) {
            PerformanceCategory.entries.filter { it != highlightedCategory } + listOfNotNull(highlightedCategory)
        } else {
            PerformanceCategory.entries
        }

        drawOrder.forEach { category ->
            val fullPath = categoryPaths[category] ?: return@forEach
            val color = colorForCategory(category, palette)
            val isHighlighted = highlightedCategory == null || highlightedCategory == category
            val focusMultiplier = if (isHighlighted) 1f else 0.24f

            pathMeasure.setPath(fullPath, false)
            val length = pathMeasure.length
            if (length <= 0f) return@forEach

            partialPath.reset()
            val targetLen = (length * revealProgress.coerceIn(0f, 1f)).coerceAtLeast(1f)
            pathMeasure.getSegment(0f, targetLen, partialPath, true)

            if (highlightedCategory == category && revealProgress > 0.05f) {
                areaPath.set(partialPath)
                if (pathMeasure.getPosTan(targetLen, beadPos, null)) {
                    areaPath.lineTo(beadPos[0], plotBottom)
                    areaPath.lineTo(plotLeft, plotBottom)
                    areaPath.close()
                    areaFillPaint.shader = LinearGradient(
                        0f,
                        plotTop,
                        0f,
                        plotBottom,
                        Color.argb(if (palette.isDark) 64 else 42, Color.red(color), Color.green(color), Color.blue(color)),
                        Color.TRANSPARENT,
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawPath(areaPath, areaFillPaint)
                }
            }

            // 1. Wide ambient neon halo
            val outerAlpha = ((if (palette.isDark) 42 else 26) * focusMultiplier).toInt()
            glowOuterPaint.color = Color.argb(outerAlpha, Color.red(color), Color.green(color), Color.blue(color))
            glowOuterPaint.strokeWidth = 9f * density
            canvas.drawPath(partialPath, glowOuterPaint)

            // 2. Medium neon bloom
            val midAlpha = ((if (palette.isDark) 95 else 65) * focusMultiplier).toInt()
            glowMidPaint.color = Color.argb(midAlpha, Color.red(color), Color.green(color), Color.blue(color))
            glowMidPaint.strokeWidth = 4.5f * density
            canvas.drawPath(partialPath, glowMidPaint)

            // 3. Crisp ECG core
            val coreAlpha = (255 * focusMultiplier).toInt()
            coreLinePaint.color = Color.argb(coreAlpha, Color.red(color), Color.green(color), Color.blue(color))
            coreLinePaint.strokeWidth = (if (isHighlighted) 2.2f else 1.5f) * density
            canvas.drawPath(partialPath, coreLinePaint)

            // 4. White-hot inner filament for active waves
            if (isHighlighted) {
                whiteFilamentPaint.alpha = if (palette.isDark) 175 else 115
                whiteFilamentPaint.strokeWidth = 0.85f * density
                canvas.drawPath(partialPath, whiteFilamentPaint)
            }

            // 5. Flowing energy pulse bead traveling along the revealed ECG segment
            if (isHighlighted && targetLen > 8f) {
                val offsetShift = category.ordinal * 0.17f
                val wavePosFraction = ((flowPhase + offsetShift) % 1f)
                val beadDistance = targetLen * wavePosFraction
                if (pathMeasure.getPosTan(beadDistance, beadPos, null)) {
                    val haloR = 9.5f * density
                    beadGlowPaint.shader = RadialGradient(
                        beadPos[0],
                        beadPos[1],
                        haloR,
                        intArrayOf(
                            Color.WHITE,
                            Color.argb(if (palette.isDark) 210 else 170, Color.red(color), Color.green(color), Color.blue(color)),
                            Color.TRANSPARENT
                        ),
                        floatArrayOf(0f, 0.35f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawCircle(beadPos[0], beadPos[1], haloR, beadGlowPaint)
                }
            }
        }
    }

    private fun drawDayNodesAndTooltip(canvas: Canvas, density: Float) {
        if (selectedDayIndex !in dayPoints.indices) return
        val day = dayPoints[selectedDayIndex]

        // Glowing nodes on the selected day column
        if (revealProgress >= 0.92f || selectedDayIndex < (dayPoints.size * revealProgress)) {
            PerformanceCategory.entries.forEach { category ->
                val isHighlighted = highlightedCategory == null || highlightedCategory == category
                if (!isHighlighted) return@forEach
                val pt = categoryPeakPoints[category]?.getOrNull(selectedDayIndex) ?: return@forEach
                val color = colorForCategory(category, palette)

                nodePaint.color = color
                canvas.drawCircle(pt.first, pt.second, 4.2f * density, nodePaint)
                nodeRingPaint.color = if (palette.isDark) Color.WHITE else palette.surfaceColor
                nodeRingPaint.strokeWidth = 1.6f * density
                canvas.drawCircle(pt.first, pt.second, 4.2f * density, nodeRingPaint)
            }
        }

        // Frosted HUD tooltip pill at top of chart showing day's exact breakdown
        val mentalTxt = "M:${NeuralStatsCalculator.formatMinutesCompact(day.minutesByCategory[PerformanceCategory.MENTAL] ?: 0f)}"
        val physTxt = "F:${NeuralStatsCalculator.formatMinutesCompact(day.minutesByCategory[PerformanceCategory.PHYSICAL] ?: 0f)}"
        val exprTxt = "E:${NeuralStatsCalculator.formatMinutesCompact(day.minutesByCategory[PerformanceCategory.EXPRESSIVE] ?: 0f)}"
        val occTxt = "O:${NeuralStatsCalculator.formatMinutesCompact(day.minutesByCategory[PerformanceCategory.OCCUPATIONAL] ?: 0f)}"
        val hudLine = "${day.dayLabel}${if (day.isToday) " (HOY)" else ""} · $mentalTxt  $physTxt  $exprTxt  $occTxt"

        tooltipTextPaint.textSize = 9.2f * density
        tooltipTextPaint.color = palette.textPrimary
        val textW = tooltipTextPaint.measureText(hudLine)
        val padH = 10f * density
        val pillW = (textW + padH * 2f).coerceAtMost(plotRight - plotLeft)
        val pillH = 22f * density
        val pillLeft = ((plotLeft + plotRight) / 2f - pillW / 2f).coerceAtLeast(plotLeft)
        val pillTop = 5f * density

        tooltipRect.set(pillLeft, pillTop, pillLeft + pillW, pillTop + pillH)
        tooltipBgPaint.color = palette.glassSurfaceColor
        tooltipBorderPaint.color = palette.rimHighlightColor
        canvas.drawRoundRect(tooltipRect, pillH / 2f, pillH / 2f, tooltipBgPaint)
        canvas.drawRoundRect(tooltipRect, pillH / 2f, pillH / 2f, tooltipBorderPaint)

        canvas.drawText(
            hudLine,
            pillLeft + padH,
            pillTop + pillH / 2f + 3.2f * density,
            tooltipTextPaint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                val moved = kotlin.math.hypot(event.x - downX, event.y - downY)
                if (moved <= 24f * resources.displayMetrics.density && dayPoints.isNotEmpty()) {
                    var bestIdx = selectedDayIndex
                    var bestDist = Float.MAX_VALUE
                    for (i in dayPoints.indices) {
                        val d = abs(event.x - dayColumnX[i])
                        if (d < bestDist) {
                            bestDist = d
                            bestIdx = i
                        }
                    }
                    if (bestIdx in dayPoints.indices) {
                        selectedDayIndex = bestIdx
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        performClick()
                        invalidate()
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

    companion object {
        fun colorForCategory(category: PerformanceCategory, palette: ResolvedCyberPalette): Int {
            return if (palette.isDark) {
                when (category) {
                    PerformanceCategory.MENTAL -> 0xFF00F0FF.toInt()       // Cian neón
                    PerformanceCategory.PHYSICAL -> 0xFFFF0055.toInt()     // Magenta neón
                    PerformanceCategory.EXPRESSIVE -> 0xFF00FF9F.toInt()   // Verde neón
                    PerformanceCategory.OCCUPATIONAL -> 0xFFB54BFF.toInt() // Púrpura neón
                }
            } else {
                when (category) {
                    PerformanceCategory.MENTAL -> 0xFF007ACC.toInt()       // Cian eléctrico día
                    PerformanceCategory.PHYSICAL -> 0xFFD60044.toInt()     // Magenta profundo día
                    PerformanceCategory.EXPRESSIVE -> 0xFF008A3E.toInt()   // Verde neón día
                    PerformanceCategory.OCCUPATIONAL -> 0xFF7E22CE.toInt() // Púrpura neón día
                }
            }
        }
    }
}
