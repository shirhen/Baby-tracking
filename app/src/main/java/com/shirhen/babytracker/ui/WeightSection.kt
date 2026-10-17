package com.shirhen.babytracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shirhen.babytracker.data.BabyProfile
import com.shirhen.babytracker.data.Repo
import com.shirhen.babytracker.data.Weight
import com.shirhen.babytracker.data.startMillis
import com.shirhen.babytracker.data.toLocalDate
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

/** A weight point for the chart and the list; the birth weight comes from the profile. */
private data class WeightPoint(val day: LocalDate, val grams: Int, val weight: Weight?)

private fun grams(g: Int) = "%,d".format(g) + " ג׳"

@Composable
fun WeightSection(weights: List<Weight>, profile: BabyProfile?) {
    val points = remember(weights, profile) {
        val list = weights.map { WeightPoint(it.time.toLocalDate(), it.grams, it) }.toMutableList()
        val birthDay = profile?.birthEpochDay?.let { LocalDate.ofEpochDay(it) }
        if (profile != null && birthDay != null && profile.birthWeightG > 0) list += WeightPoint(birthDay, profile.birthWeightG, null)
        list.sortedBy { it.day }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AddWeightCard()
        if (points.isEmpty()) {
            Muted("עוד אין שקילות. מוסיפים כאן כל שקילה (טיפת חלב, רופאה, בבית).")
            return@Column
        }
        SummaryCard(points, profile)
        if (points.size >= 2) ChartCard(points)
        HistoryList(points)
        Muted(
            "לייחוס: עלייה ממוצעת בארבעת החודשים הראשונים היא כ-125–200 גרם לשבוע, ומיום 4 כ-28 גרם ליום " +
                "(הנחיית NHS Wales – Hywel Dda, לפי Mohrbacher & Kendall-Tackett). כל תינוק שונה – המעקב בטיפת חלב קובע.",
        )
    }
}

@Composable
private fun AddWeightCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var grams by remember { mutableIntStateOf(0) }
    var day by remember { mutableStateOf(LocalDate.now()) }
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("⚖️ שקילה חדשה", fontWeight = FontWeight.Bold)
            Stepper("משקל", grams, { grams = it }, step = 10, unit = "גרם", max = 15000)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { pickDay(context, day) { day = it } }) { Text(Fmt.dayLabel(day)) }
                Spacer(Modifier.width(8.dp))
                Button(enabled = grams > 0, onClick = {
                    val w = Weight(time = day.startMillis() + 12 * 3_600_000L, grams = grams)
                    scope.launch { Repo.saveWeight(w) }
                    grams = 0
                }) { Text("הוספה") }
            }
        }
    }
}

@Composable
private fun SummaryCard(points: List<WeightPoint>, profile: BabyProfile?) {
    val latest = points.last()
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Palette.feed)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("משקל אחרון", style = MaterialTheme.typography.labelLarge)
            Text(grams(latest.grams), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            val ageDays = profile?.birthEpochDay?.let { (latest.day.toEpochDay() - it).toInt() }
            Text(Fmt.date(latest.day) + (ageDays?.let { " · גיל ${Fmt.age(it)}" } ?: ""), style = MaterialTheme.typography.bodySmall)

            // Average gain over roughly the last two weeks of weighings.
            val since = latest.day.minusDays(14)
            val base = points.firstOrNull { it.day >= since && it.day < latest.day } ?: points.getOrNull(points.size - 2)
            if (base != null && base.day < latest.day) {
                val days = (latest.day.toEpochDay() - base.day.toEpochDay()).toInt()
                val diff = latest.grams - base.grams
                val perDay = diff.toDouble() / days
                Text(
                    "${if (diff >= 0) "+" else ""}${grams(diff)} ב-$days ימים · " +
                        "${perDay.roundToInt()} ג׳ ליום · ${(perDay * 7).roundToInt()} ג׳ לשבוע",
                    fontWeight = FontWeight.Medium,
                )
            }
            val birth = points.firstOrNull { it.weight == null }
            if (birth != null && birth !== latest) {
                val pct = (latest.grams - birth.grams) * 100.0 / birth.grams
                Muted("ממשקל הלידה: ${if (pct >= 0) "+" else ""}${"%.1f".format(pct)}%")
            }
        }
    }
}

@Composable
private fun ChartCard(points: List<WeightPoint>) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.surfaceVariant
    val minG = points.minOf { it.grams }
    val maxG = points.maxOf { it.grams }
    val pad = ((maxG - minG) * 0.15).toInt().coerceAtLeast(50)
    val lo = minG - pad
    val hi = maxG + pad
    val d0 = points.first().day.toEpochDay()
    val d1 = points.last().day.toEpochDay().coerceAtLeast(d0 + 1)

    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp)) {
            Text("עקומת משקל", fontWeight = FontWeight.Medium)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column {
                    Muted(grams(hi))
                    Canvas(Modifier.fillMaxWidth().height(160.dp).padding(vertical = 4.dp)) {
                        fun x(day: LocalDate) = (day.toEpochDay() - d0).toFloat() / (d1 - d0) * size.width
                        fun y(g: Int) = size.height - (g - lo).toFloat() / (hi - lo) * size.height
                        for (i in 0..3) {
                            val gy = size.height * i / 3f
                            drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
                        }
                        val path = Path()
                        points.forEachIndexed { i, p ->
                            val o = Offset(x(p.day), y(p.grams))
                            if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                        }
                        drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx()))
                        points.forEach { p -> drawCircle(lineColor, radius = 5.dp.toPx(), center = Offset(x(p.day), y(p.grams))) }
                    }
                    Muted(grams(lo))
                    Row(Modifier.fillMaxWidth()) {
                        Muted(Fmt.date(points.first().day))
                        Spacer(Modifier.weight(1f))
                        Muted(Fmt.date(points.last().day))
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryList(points: List<WeightPoint>) {
    val scope = rememberCoroutineScope()
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("כל השקילות", fontWeight = FontWeight.Medium)
            points.asReversed().forEachIndexed { idx, p ->
                val prev = points.asReversed().getOrNull(idx + 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${Fmt.date(p.day)} · ${grams(p.grams)}" + if (p.weight == null) " (לידה)" else "")
                        if (prev != null) {
                            val days = (p.day.toEpochDay() - prev.day.toEpochDay()).toInt().coerceAtLeast(1)
                            val diff = p.grams - prev.grams
                            Muted("${if (diff >= 0) "+" else ""}${grams(diff)} ב-$days ימים (${(diff.toDouble() / days).roundToInt()} ג׳ ליום)")
                        }
                    }
                    p.weight?.let { w -> TextButton(onClick = { scope.launch { Repo.deleteWeight(w) } }) { Text("מחיקה") } }
                }
            }
        }
    }
}
