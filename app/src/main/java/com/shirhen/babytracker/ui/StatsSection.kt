package com.shirhen.babytracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shirhen.babytracker.data.Event
import com.shirhen.babytracker.data.EventType
import com.shirhen.babytracker.data.FeedKind
import com.shirhen.babytracker.data.Stats
import com.shirhen.babytracker.data.computeStats
import kotlin.math.roundToInt

private val periods = listOf(
    "24 ש׳" to 1L,
    "3 ימים" to 3L,
    "שבוע" to 7L,
    "שבועיים" to 14L,
    "חודש" to 30L,
)

private fun Double.fmt1(): String =
    if (this >= 10 || this == Math.floor(this)) roundToInt().toString() else "%.1f".format(this)

private fun minutes(m: Int?): String = when {
    m == null -> "—"
    m < 60 -> "$m ד׳"
    else -> "${m / 60}:${"%02d".format(m % 60)} ש׳"
}

@Composable
fun StatsSection(events: List<Event>, now: Long) {
    var period by rememberSaveable { mutableIntStateOf(2) }
    val days = periods[period].second
    val s = remember(events, period, now / 60_000) { computeStats(events, days * 86_400_000L, now) }
    val perLabel = if (days == 1L) "ב-24 ש׳" else "ביום (ממוצע)"

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            periods.forEachIndexed { i, (label, _) ->
                FilterChip(selected = period == i, onClick = { period = i }, label = { Text(label) })
            }
        }
        if (days > 1 && s.periodDays < days - 0.5) {
            Muted("יש נתונים רק מ-${s.periodDays.fmt1()} הימים האחרונים, והממוצעים מחושבים לפיהם.")
        }
        if (s.feeds + s.diapers + s.pumps + s.baths == 0) {
            Muted("אין רשומות בתקופה הזו.")
            return@Column
        }

        StatCard("🍼 האכלות", Palette.feed) {
            Tiles(
                "האכלות $perLabel" to s.feedsPerDay.fmt1(),
                "מרווח ממוצע" to minutes(s.avgGapMin),
                "המרווח הארוך ביותר" to minutes(s.longestGapMin),
                "בלילה (22–06) $perLabel" to s.nightFeedsPerNight.fmt1(),
            )
            if (s.feeds > 0) KindBar(s)
        }

        if (s.bottleFeeds > 0) {
            StatCard("🍼 בקבוק", Palette.feed) {
                Tiles(
                    "מ״ל $perLabel" to s.bottleMlPerDay.roundToInt().toString(),
                    "ממוצע לבקבוק" to "${s.avgMlPerBottle ?: "—"} מ״ל",
                    "תמ״ל – ממוצע לארוחה" to (s.avgFormulaPerFeed?.let { "$it מ״ל" } ?: "—"),
                    "שאוב – ממוצע לארוחה" to (s.avgExpressedPerFeed?.let { "$it מ״ל" } ?: "—"),
                )
                Muted("תמ״ל ${s.formulaMlPerDay.roundToInt()} · שאוב ${s.expressedMlPerDay.roundToInt()} מ״ל $perLabel · הבקבוק הגדול ביותר: ${s.maxBottleMl} מ״ל")
            }
        }

        if (s.breastFeeds > 0) {
            StatCard("🤱 הנקה", Palette.feed) {
                Tiles(
                    "משך ממוצע להנקה" to minutes(s.avgBreastMin),
                    "דקות הנקה $perLabel" to s.breastMinPerDay.roundToInt().toString(),
                    "ממוצע שמאל" to minutes(s.avgLeftMin),
                    "ממוצע ימין" to minutes(s.avgRightMin),
                )
                if (s.lastSideLeft + s.lastSideRight > 0) {
                    Muted("סיום בצד שמאל ${s.lastSideLeft} פעמים · בצד ימין ${s.lastSideRight} פעמים")
                }
            }
        }

        StatCard("🧷 חיתולים", Palette.diaper) {
            Tiles(
                "חיתולים $perLabel" to s.diapersPerDay.fmt1(),
                "רטובים $perLabel" to s.wetPerDay.fmt1(),
                "מלוכלכים $perLabel" to s.dirtyPerDay.fmt1(),
                "סה״כ בתקופה" to s.diapers.toString(),
            )
        }

        if (s.pumps > 0) {
            StatCard("🫧 שאיבה", Palette.pump) {
                Tiles(
                    "שאיבות $perLabel" to s.pumpsPerDay.fmt1(),
                    "מ״ל $perLabel" to s.pumpMlPerDay.roundToInt().toString(),
                    "ממוצע לשאיבה" to "${s.avgPumpMl ?: "—"} מ״ל",
                    "משך ממוצע" to minutes(s.avgPumpMin),
                )
                if (s.avgPumpLeftMl != null) Muted("ממוצע לצד: שמאל ${s.avgPumpLeftMl} · ימין ${s.avgPumpRightMl} מ״ל")
            }
        }

        StatCard("🛁 מקלחות", Palette.bath) {
            Tiles(
                "בתקופה" to s.baths.toString(),
                "האחרונה" to (s.lastBath?.let { Fmt.ago(it, now) } ?: "—"),
            )
        }
    }
}

@Composable
private fun StatCard(title: String, accent: Color, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** Metric tiles, two per row. */
@Composable
private fun Tiles(vararg items: Pair<String, String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.toList().chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value) ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

private val kindColors = mapOf(
    FeedKind.BREAST to Color(0xFFD85A30),
    FeedKind.FORMULA to Color(0xFF378ADD),
    FeedKind.EXPRESSED to Color(0xFF7F77DD),
    FeedKind.MIXED to Color(0xFF1D9E75),
)

/** Share of each feed type as one stacked bar, with counts and percentages below. */
@Composable
private fun KindBar(s: Stats) {
    val total = s.feeds.coerceAtLeast(1)
    val kinds = FeedKind.entries.filter { (s.byKind[it] ?: 0) > 0 }
    Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))) {
        kinds.forEach { k ->
            Box(Modifier.weight((s.byKind[k] ?: 0).toFloat()).height(12.dp).background(kindColors.getValue(k)))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        kinds.forEach { k ->
            val n = s.byKind[k] ?: 0
            Row {
                Box(Modifier.padding(top = 5.dp).size(8.dp).clip(RoundedCornerShape(4.dp)).background(kindColors.getValue(k)))
                Text(
                    "  ${Fmt.feedKind(k)}: $n (${(n * 100.0 / total).roundToInt()}%)",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
