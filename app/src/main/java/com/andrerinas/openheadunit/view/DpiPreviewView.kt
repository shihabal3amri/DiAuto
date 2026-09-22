package com.andrerinas.openheadunit.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * A schematic, value-driven preview of the head-unit layout. It is NOT a real Android Auto
 * render; it draws a mock launcher (a grid of fixed-size cards plus a bottom bar) inside a
 * frame with the panel's aspect ratio. The number of cards that fit is derived from how many
 * density-independent pixels the panel has at the chosen DPI, so:
 *   higher DPI -> fewer, larger cards (less fits);  lower DPI -> more, smaller cards.
 */
class DpiPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var dpi: Int = 160
    private var panelWidthPx: Int = 0
    private var panelHeightPx: Int = 0

    private val accent = resolveAttrColor(com.google.android.material.R.attr.colorPrimary, Color.parseColor("#009688"))
    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(accent, 60) }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = 0x33808080
    }
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = 0x55808080
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(accent, 130) }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    init {
        val m = readRealMetrics()
        panelWidthPx = m.widthPixels
        panelHeightPx = m.heightPixels
    }

    fun setDpi(value: Int) {
        if (value != dpi) {
            dpi = value
            invalidate()
        }
    }

    /** Override the panel resolution used to compute how much content fits (e.g. the
     * resolution detected by the wizard). */
    fun setPanelResolution(widthPx: Int, heightPx: Int) {
        if (widthPx > 0 && heightPx > 0) {
            panelWidthPx = widthPx
            panelHeightPx = heightPx
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = dp(6f)
        val availW = width - pad * 2
        val availH = height - pad * 2
        if (availW <= 0 || availH <= 0) return

        // Always draw the frame in landscape (wide) orientation, even on a portrait panel:
        // a wide grid makes the DPI effect clearly visible, whereas a tall narrow frame barely
        // changes. Uses the panel's true aspect but forced to its landscape form.
        val aspect = if (panelWidthPx > 0 && panelHeightPx > 0)
            max(panelWidthPx, panelHeightPx).toFloat() / min(panelWidthPx, panelHeightPx).toFloat()
        else 16f / 9f
        var screenW = availW
        var screenH = screenW / aspect
        if (screenH > availH) {
            screenH = availH
            screenW = screenH * aspect
        }
        val left = pad + (availW - screenW) / 2f
        val top = pad + (availH - screenH) / 2f
        val screen = RectF(left, top, left + screenW, top + screenH)
        val radius = dp(10f)
        canvas.drawRoundRect(screen, radius, radius, framePaint)

        val inset = dp(8f)
        val cLeft = screen.left + inset
        val cTop = screen.top + inset
        val cRight = screen.right - inset
        val cBottom = screen.bottom - inset

        // Bottom mock nav bar with three buttons.
        val barHeight = (cBottom - cTop) * 0.18f
        val barRect = RectF(cLeft, cBottom - barHeight, cRight, cBottom)
        val barRadius = barHeight / 2f
        canvas.drawRoundRect(barRect, barRadius, barRadius, barPaint)
        val cy = barRect.centerY()
        val dotR = barHeight * 0.18f
        val spacing = (cRight - cLeft) / 4f
        for (i in 1..3) canvas.drawCircle(cLeft + spacing * i, cy, dotR, dotPaint)

        // Grid of cards above the bar. The card size grows clearly with DPI (and shrinks at
        // low DPI) so the effect stays visible even on narrow portrait previews; the number
        // that fit changes accordingly.
        val gridBottom = barRect.top - dp(8f)
        val gridW = cRight - cLeft
        val gridH = gridBottom - cTop
        if (gridW <= 0 || gridH <= 0) return

        val norm = ((dpi - 110f) / 530f).coerceIn(0f, 1f)
        val cell = (min(gridW, gridH) * (0.16f + norm * 0.34f)).coerceAtLeast(dp(22f))
        val gap = dp(6f)
        val cols = max(1, floor((gridW + gap) / (cell + gap)).toInt())
        val rows = max(1, floor((gridH + gap) / (cell + gap)).toInt())
        val totalW = cols * cell + (cols - 1) * gap
        val totalH = rows * cell + (rows - 1) * gap
        val startX = cLeft + (gridW - totalW) / 2f
        val startY = cTop + (gridH - totalH) / 2f
        val tileRadius = dp(6f)
        var y = startY
        for (r in 0 until rows) {
            var x = startX
            for (c in 0 until cols) {
                val tile = RectF(x, y, x + cell, y + cell)
                canvas.drawRoundRect(tile, tileRadius, tileRadius, tilePaint)
                canvas.drawRoundRect(tile, tileRadius, tileRadius, strokePaint)
                x += cell + gap
            }
            y += cell + gap
        }
    }

    private fun readRealMetrics(): DisplayMetrics {
        val m = DisplayMetrics()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display?.getRealMetrics(m)
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(m)
            }
        } catch (_: Exception) {
        }
        return m
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private fun resolveAttrColor(attr: Int, fallback: Int): Int {
        val tv = TypedValue()
        return if (context.theme.resolveAttribute(attr, tv, true)) tv.data else fallback
    }
}
