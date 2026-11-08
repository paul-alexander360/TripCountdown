package com.example.tripcountdown

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.runtime.remember
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import java.time.LocalDate

class TripWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now().toEpochDay()
        val trip = TripStore.load(context)
            .filter { it.endEpochDay >= today }
            .minByOrNull { it.epochDay }
        val density = context.resources.displayMetrics.density

        provideContent {
            val size = LocalSize.current
            var w = size.width.value * density
            var h = size.height.value * density
            val k = minOf(1f, 900f / maxOf(w, h, 1f))
            w *= k
            h *= k
            val bmp = remember(trip, w, h) {
                renderWidgetBitmap(trip, w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1), density * k)
            }
            Image(
                provider = ImageProvider(bmp),
                contentDescription = "Next trip",
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
            )
        }
    }
}

class TripWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TripWidget()
}

private fun shadowPaint(size: Float, bold: Boolean): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.WHITE
    textSize = size
    typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    setShadowLayer(size * 0.12f, size * 0.07f, size * 0.07f, Color.BLACK)
}

private fun drawLine(canvas: Canvas, text: String, bold: Boolean, size: Float, x: Float, y: Float, availW: Float): Float {
    var s = size
    var p = shadowPaint(s, bold)
    val m = p.measureText(text)
    if (m > availW && m > 0f) {
        s = size * availW / m
        p = shadowPaint(s, bold)
    }
    canvas.drawText(text, x, y - p.descent(), p)
    canvas.drawText(text, x, y - p.descent(), p)
    return s * 1.2f
}

private fun renderWidgetBitmap(trip: Trip?, w: Int, h: Int, density: Float): Bitmap {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val radius = 16f * density
    val clip = Path().apply {
        addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, Path.Direction.CW)
    }
    canvas.clipPath(clip)

    if (trip == null) {
        canvas.drawColor(Color.parseColor("#1565C0"))
        val p = shadowPaint(h * 0.16f, true)
        val text = "No upcoming trips"
        val m = p.measureText(text)
        if (m > w * 0.9f) p.textSize = p.textSize * (w * 0.9f) / m
        canvas.drawText(text, (w - p.measureText(text)) / 2f, h / 2f + p.textSize / 3f, p)
        return bmp
    }

    val photo = trip.photoPath?.let { BitmapFactory.decodeFile(it) }
    if (photo != null) {
        val scale = maxOf(w.toFloat() / photo.width, h.toFloat() / photo.height) * trip.zoom
        val sw = w / scale
        val sh = h / scale
        val left = (photo.width - sw).coerceAtLeast(0f) * trip.focusX.coerceIn(0f, 1f)
        val top = (photo.height - sh).coerceAtLeast(0f) * trip.focusY.coerceIn(0f, 1f)
        canvas.drawBitmap(
            photo,
            Rect(left.toInt(), top.toInt(), (left + sw).toInt(), (top + sh).toInt()),
            Rect(0, 0, w, h),
            Paint(Paint.FILTER_BITMAP_FLAG)
        )
    } else {
        canvas.drawColor(Color.parseColor("#90A4AE"))
    }

    val margin = h * 0.08f
    val availW = w - 2 * margin
    var y = h - margin
    if (trip.todos.isNotEmpty()) {
        y -= drawLine(canvas, "To-do: ${trip.todos.count { it.done }} of ${trip.todos.size} done", false, h * 0.12f, margin, y, availW)
    }
    y -= drawLine(canvas, countdown(trip), true, h * 0.15f, margin, y, availW)
    drawLine(canvas, trip.name, true, h * 0.20f, margin, y, availW)
    return bmp
}
