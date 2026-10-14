package com.andrerinas.openheadunit.cluster

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.hud.BydGuidance
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The next-turn card drawn over the dashboard map, the same card DiPlay draws: a dark glass capsule,
 * the maneuver glyph in a chip on the left, distance and road on the right, and an arrival strip
 * below when the phone reports the route's remaining time and distance. Glyphs are Material Symbols
 * (Apache 2.0) keyed by the cluster icon codes in [BydGuidance.clusterIcon].
 */
@RequiresApi(android.os.Build.VERSION_CODES.P)
internal class ClusterTurnCardView(context: Context) : View(context) {
    private var guidance: BydGuidance? = null
    private var plan: ClusterLayout.Plan? = null
    private var night = false

    private val accent = Color.rgb(10, 132, 255)
    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val distancePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val roadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
    private val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val rect = RectF()
    private var glyph: Drawable? = null
    private var glyphRes = 0

    init { applyPalette() }

    fun setPlan(next: ClusterLayout.Plan?) {
        plan = next
        invalidate()
    }

    /** Day: dark glass on a light map; night: a lighter translucent panel for the dark map. */
    fun setNightMode(next: Boolean) {
        if (next == night) return
        night = next
        applyPalette()
        invalidate()
    }

    fun setGuidance(next: BydGuidance?) {
        visibility = if (next == null) GONE else VISIBLE
        if (guidance == next) return
        guidance = next
        invalidate()
    }

    private fun applyPalette() {
        val alpha = 217 // 85 %, DiPlay's default opacity
        glassPaint.color = if (night) Color.argb(alpha, 12, 14, 18) else Color.argb(alpha, 28, 28, 30)
        chipPaint.color = Color.argb(39, 255, 255, 255)
        strokePaint.color = Color.argb(if (night) 35 else 33, 255, 255, 255)
        distancePaint.color = if (night) Color.rgb(240, 242, 246) else Color.WHITE
        roadPaint.color = if (night) Color.argb(179, 176, 182, 192) else Color.argb(179, 199, 199, 204)
        infoPaint.color = if (night) Color.argb(224, 226, 228, 236) else Color.argb(224, 235, 235, 240)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val next = guidance ?: return
        val card = ClusterTurnCardPlacement.card(width, height, plan) ?: return
        val h = card.height.toFloat()
        val w = card.width.toFloat()
        val radius = h * 0.30f
        rect.set(card.left.toFloat(), card.top.toFloat(), card.left + w, card.top + h)
        canvas.drawRoundRect(rect, radius, radius, glassPaint)
        canvas.drawRoundRect(rect, radius, radius, strokePaint)

        val chip = h * 0.76f
        val chipLeft = card.left + h * 0.12f
        val chipTop = card.top + (h - chip) / 2f
        rect.set(chipLeft, chipTop, chipLeft + chip, chipTop + chip)
        canvas.drawRoundRect(rect, chip * 0.26f, chip * 0.26f, chipPaint)
        drawGlyph(canvas, next, chipLeft, chipTop, chip)

        val textLeft = chipLeft + chip + h * 0.14f
        val textWidth = card.left + w - h * 0.10f - textLeft
        if (textWidth <= 0f) return
        distancePaint.textSize = h * 0.30f
        roadPaint.textSize = h * 0.17f
        canvas.drawText(ellipsize(distanceLabel(next.distanceMeters), textWidth, distancePaint),
            textLeft, card.top + h * 0.44f, distancePaint)
        val road = if (next.roundaboutExit in 1..9) context.getString(R.string.turn_card_exit, next.roundaboutExit) else next.road
        if (road.isNotBlank()) {
            canvas.drawText(ellipsize(road, textWidth, roadPaint), textLeft, card.top + h * 0.72f, roadPaint)
        }
        drawInfoStrip(canvas, next, card)
    }

    /** The arrival/duration/distance pill under the card, like the stock navigation bar. */
    private fun drawInfoStrip(canvas: Canvas, next: BydGuidance, card: ClusterTurnCardPlacement.Box) {
        val parts = mutableListOf<String>()
        if (next.remainingSeconds > 0) {
            val arrival = Date(System.currentTimeMillis() + next.remainingSeconds * 1000L)
            parts += context.getString(R.string.turn_card_info_arrival, SimpleDateFormat("HH:mm", Locale.getDefault()).format(arrival))
            val hours = next.remainingSeconds / 3600
            val minutes = (next.remainingSeconds % 3600) / 60
            parts += if (hours > 0) context.getString(R.string.turn_card_info_duration_hm, hours, minutes)
                else context.getString(R.string.turn_card_info_duration_m, minutes)
        }
        if (next.remainingMeters > 0) parts += context.getString(R.string.turn_card_info_km, next.remainingMeters / 1000.0)
        if (parts.isEmpty()) return
        val h = (card.height * 0.40f).coerceAtLeast(34f)
        val top = (card.top + card.height * 1.05f).coerceAtMost(height - h)
        infoPaint.textSize = h * 0.42f
        val text = parts.joinToString("   ·   ")
        val textWidth = infoPaint.measureText(text)
        val stripWidth = maxOf(card.width.toFloat(), textWidth + h * 0.9f).coerceAtMost(width - 16f)
        val stripLeft = (card.left + card.width / 2f - stripWidth / 2f).coerceIn(4f, (width - stripWidth - 4f).coerceAtLeast(4f))
        rect.set(stripLeft, top, stripLeft + stripWidth, top + h)
        canvas.drawRoundRect(rect, h / 2f, h / 2f, glassPaint)
        canvas.drawRoundRect(rect, h / 2f, h / 2f, strokePaint)
        val shown = ellipsize(text, stripWidth - h * 0.6f, infoPaint)
        canvas.drawText(shown, stripLeft + stripWidth / 2f - infoPaint.measureText(shown) / 2f, top + h * 0.69f, infoPaint)
    }

    private fun drawGlyph(canvas: Canvas, next: BydGuidance, left: Float, top: Float, side: Float) {
        val res = glyphRes(next.clusterIcon)
        if (res != glyphRes) {
            glyph = ContextCompat.getDrawable(context, res)?.mutate()?.apply { setTint(accent) }
            glyphRes = res
        }
        val inset = side * 0.10f
        glyph?.setBounds((left + inset).toInt(), (top + inset).toInt(), (left + side - inset).toInt(), (top + side - inset).toInt())
        glyph?.draw(canvas)
        val exit = next.roundaboutExit.takeIf { it in 1..9 } ?: return
        val d = side * 0.42f
        val cx = left + side - d / 2f
        val cy = top + side - d / 2f
        canvas.drawCircle(cx, cy, d / 2f, badgePaint)
        badgeTextPaint.textSize = d * 0.62f
        canvas.drawText(exit.toString(), cx, cy - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f, badgeTextPaint)
    }

    private fun distanceLabel(meters: Int): String = when {
        meters <= 20 -> context.getString(R.string.turn_card_now)
        meters < 1000 -> context.getString(R.string.turn_card_distance_m, meters)
        else -> context.getString(R.string.turn_card_distance_km, meters / 100 / 10f)
    }

    private fun ellipsize(text: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text.take(end) + "…") > maxWidth) end--
        return if (end == 0) "…" else text.take(end) + "…"
    }

    companion object {
        /** DiPlay's glyph table for the cluster icon codes. */
        fun glyphRes(icon: Int): Int = when (icon) {
            2 -> R.drawable.ic_turn_card_turn_left
            3 -> R.drawable.ic_turn_card_turn_right
            4 -> R.drawable.ic_turn_card_turn_slight_left
            5 -> R.drawable.ic_turn_card_turn_slight_right
            6 -> R.drawable.ic_turn_card_turn_sharp_left
            7 -> R.drawable.ic_turn_card_turn_sharp_right
            8 -> R.drawable.ic_turn_card_u_turn_left
            19 -> R.drawable.ic_turn_card_u_turn_right
            11, 12, 17, 18 -> R.drawable.ic_turn_card_roundabout_right
            15 -> R.drawable.ic_turn_card_sports_score
            else -> R.drawable.ic_turn_card_straight
        }
    }
}

/** Where the card sits on the cluster panel. Pure, so the geometry is testable. */
internal object ClusterTurnCardPlacement {
    // DiPlay's measured clear centre of a DiLink 5.0 cluster, in percent of the panel.
    private const val SAFE_LEFT = 35
    private const val SAFE_RIGHT = 36
    private const val ASPECT = 2.64f

    data class Box(val left: Int, val top: Int, val width: Int, val height: Int)

    fun card(panelWidth: Int, panelHeight: Int, plan: ClusterLayout.Plan?): Box? {
        if (panelWidth <= 0 || panelHeight <= 0) return null
        val (width, centerX, centerY) = when {
            // Side window of Scenario/Simple: across the top of the narrow map.
            plan != null && !plan.fullMap ->
                Triple(plan.width * 80 / 100, plan.left + plan.width / 2, plan.top + plan.height * 22 / 100)
            // Full map: left of the marker, clear of the trip readouts shaded at the outer edges.
            plan != null -> Triple(panelWidth * 16 / 100, panelWidth * 35 / 100, plan.top + plan.height * 25 / 100)
            // DiLink 5.0 full panel: DiPlay's defaults (55 % of the clear centre, at 76 % / 30 %).
            else -> Triple(panelWidth * (100 - SAFE_LEFT - SAFE_RIGHT) / 100 * 55 / 100,
                panelWidth * 76 / 100, panelHeight * 30 / 100)
        }
        val cardWidth = width.coerceIn(150, panelWidth)
        val cardHeight = (cardWidth / ASPECT).toInt().coerceAtLeast(58).coerceAtMost(panelHeight)
        val left = (centerX - cardWidth / 2).coerceIn(0, panelWidth - cardWidth)
        val top = (centerY - cardHeight / 2).coerceIn(0, panelHeight - cardHeight)
        return Box(left, top, cardWidth, cardHeight)
    }
}
