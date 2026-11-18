package com.chronos.app.ui.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.chronos.app.R

class TimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val linePaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.design_default_color_primary)
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 32f
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val widthFloat = width.toFloat()
        val centerY = height / 2f

        // Dibuja la barra base de 24 horas estilo HarmonyOS
        canvas.drawLine(50f, centerY, widthFloat - 50f, centerY, linePaint)

        // Distribución alternada de etiquetas para evitar solapamientos
        canvas.drawText("00:00 - Inicio", 50f, centerY - 60f, textPaint)
        canvas.drawText("12:00 - Mitad", widthFloat / 2 - 50f, centerY + 80f, textPaint)
    }
}
