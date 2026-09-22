package com.andrerinas.openheadunit.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class DebugOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // View-Rand (ROT)
        paint.color = Color.RED
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Mittelpunkt (BLAU)
        paint.color = Color.BLUE
        canvas.drawLine(
            width / 2f - 40, height / 2f,
            width / 2f + 40, height / 2f,
            paint
        )
        canvas.drawLine(
            width / 2f, height / 2f - 40,
            width / 2f, height / 2f + 40,
            paint
        )

        // Ursprung (GRÜN)
        paint.color = Color.GREEN
        canvas.drawCircle(0f, 0f, 12f, paint)
    }
}
