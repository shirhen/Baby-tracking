package com.shirhen.babytracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shirhen.babytracker.data.Event
import com.shirhen.babytracker.data.EventType
import java.time.Instant
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.sin

/** Angle on a 24-hour dial: midnight at the top, clockwise. */
private fun angleOf(ms: Long): Float {
    val t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime()
    return (t.toSecondOfDay() / 86_400f) * 360f - 90f
}

/**
 * The last 24 hours as a clock face. Outer ring: feeds. Middle ring: diapers. Inner ring: pumping
 * and baths. The shaded arc runs from the last feed to now; entries older than the hand's
 * current position (i.e. from yesterday) are drawn faded.
 */
@Composable
fun ClockDial(
    events: List<Event>,
    now: Long,
    lastFeed: Event?,
    suggestedSide: String?,
    onCenterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val handColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val dayStart = now - DAY_MS
    val todayStart = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    Box(modifier = modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            val rFeed = r * 0.84f
            val rDiaper = r * 0.68f
            val rOther = r * 0.55f
            fun point(angle: Float, radius: Float): Offset {
                val rad = Math.toRadians(angle.toDouble())
                return Offset(c.x + radius * cos(rad).toFloat(), c.y + radius * sin(rad).toFloat())
            }

            // Rings
            drawCircle(Palette.feed, rFeed, c, style = Stroke(16.dp.toPx()))
            drawCircle(Palette.diaper, rDiaper, c, style = Stroke(10.dp.toPx()))
            drawCircle(trackColor, rOther, c, style = Stroke(6.dp.toPx()))

            // Time since the last feed, as a sweep on the feed ring.
            if (lastFeed != null && lastFeed.time > dayStart) {
                val start = angleOf(lastFeed.time)
                var sweep = angleOf(now) - start
                if (sweep < 0) sweep += 360f
                drawArc(
                    color = Palette.feedMark.copy(alpha = 0.35f),
                    startAngle = start, sweepAngle = sweep, useCenter = false,
                    topLeft = Offset(c.x - rFeed, c.y - rFeed), size = Size(rFeed * 2, rFeed * 2),
                    style = Stroke(16.dp.toPx(), cap = StrokeCap.Round),
                )
            }

            // Hour ticks and labels
            val paint = android.graphics.Paint().apply {
                color = labelColor
                textSize = 11.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }
            for (h in 0 until 24) {
                val a = h * 15f - 90f
                val long = h % 6 == 0
                drawLine(
                    handColor.copy(alpha = if (long) 0.6f else 0.25f),
                    point(a, r * 0.95f), point(a, r * (if (long) 0.99f else 0.97f)),
                    strokeWidth = (if (long) 2 else 1).dp.toPx(),
                )
                if (long) {
                    val p = point(a, r * 0.42f)
                    drawContext.canvas.nativeCanvas.drawText("$h", p.x, p.y + paint.textSize / 3, paint)
                }
            }

            // Entries
            events.filter { it.time > dayStart }.forEach { e ->
                val a = angleOf(e.time)
                // Yesterday's entries sit ahead of the hand; fade them so today reads clearly.
                val alpha = if (e.time < todayStart) 0.4f else 1f
                val color = Palette.mark(e).copy(alpha = alpha)
                when (e.type) {
                    EventType.FEED -> drawCircle(color, 7.dp.toPx(), point(a, rFeed))
                    EventType.DIAPER -> drawCircle(color, 5.dp.toPx(), point(a, rDiaper))
                    EventType.PUMP, EventType.BATH -> drawCircle(color, 4.dp.toPx(), point(a, rOther))
                }
            }

            // Hand
            val ah = angleOf(now)
            drawLine(handColor, point(ah, r * 0.30f), point(ah, r * 0.95f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(handColor, 4.dp.toPx(), point(ah, r * 0.95f))
        }

        // Center: time since the last feed. Tapping it logs a new feed.
        Column(
            modifier = Modifier
                .fillMaxWidth(0.52f)
                .aspectRatio(1f)
                .clip(CircleShape)
                .clickable(onClick = onCenterClick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Text(
                if (lastFeed == null) "—" else Fmt.since(lastFeed.time, now),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text("מאז האכלה", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            suggestedSide?.let {
                Text("צד הבא: ${Fmt.side(it)}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            }
            lastFeed?.let {
                Text(Fmt.time(it.time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Small legend under the dial. */
@Composable
fun DialLegend() {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        listOf(
            Palette.feedMark to "האכלה", Palette.wetMark to "רטוב", Palette.dirtyMark to "מלוכלך",
            Palette.pumpMark to "שאיבה", Palette.bathMark to "מקלחת",
        ).forEach { (color, label) ->
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Text(" $label", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
