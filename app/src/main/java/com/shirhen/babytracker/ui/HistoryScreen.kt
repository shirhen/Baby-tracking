package com.shirhen.babytracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shirhen.babytracker.data.BabyProfile
import com.shirhen.babytracker.data.Event
import com.shirhen.babytracker.data.Weight
import com.shirhen.babytracker.data.summarize
import com.shirhen.babytracker.data.toLocalDate
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    events: List<Event>,
    weights: List<Weight>,
    profile: BabyProfile?,
    onEdit: (Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    var view by rememberSaveable { mutableIntStateOf(0) }
    val byDay = remember(events) { events.groupBy { it.time.toLocalDate() } }
    val days = remember(byDay) { byDay.keys.sortedDescending() }
    val expanded = remember { mutableStateListOf<LocalDate>() }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("נתונים", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = view == 0, onClick = { view = 0 }, label = { Text("🍼 יומן") })
                FilterChip(selected = view == 1, onClick = { view = 1 }, label = { Text("⚖️ משקל") })
            }
        }
        if (view == 1) {
            item { WeightSection(weights, profile) }
            return@LazyColumn
        }
        item { BottleChart(byDay) }
        if (days.isEmpty()) item { Muted("אין עדיין נתונים.") }
        items(days, key = { it.toEpochDay() }) { day ->
            val dayEvents = byDay[day].orEmpty()
            val s = summarize(dayEvents)
            val isOpen = day in expanded
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.clickable { if (isOpen) expanded.remove(day) else expanded.add(day) },
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${Fmt.dayLabel(day)} · ${Fmt.date(day)}", fontWeight = FontWeight.Bold)
                    val bottleParts = listOfNotNull(
                        s.formulaMl.takeIf { it > 0 }?.let { "תמ״ל $it" },
                        s.expressedMl.takeIf { it > 0 }?.let { "שאוב $it" },
                    ).joinToString(" · ")
                    Text("🍼 ${s.feeds} האכלות · ${s.bottleMl} מ״ל" + (if (bottleParts.isNotEmpty()) " ($bottleParts)" else "") +
                        (if (s.breastMin > 0) " · ${s.breastMin} ד׳ הנקה" else ""))
                    Text("🧷 ${s.wet} רטובים · ${s.dirty} מלוכלכים")
                    if (s.baths > 0 || s.pumps > 0) {
                        Text(listOfNotNull(
                            s.baths.takeIf { it > 0 }?.let { "🛁 $it" },
                            s.pumps.takeIf { it > 0 }?.let { "🫧 $it שאיבות · ${s.pumpedMl} מ״ל" },
                        ).joinToString(" · "))
                    }
                    Muted(if (isOpen) "הסתרת פירוט ▲" else "פירוט ▼")
                    if (isOpen) {
                        dayEvents.forEach { e -> EventRow(e, onClick = { onEdit(e) }) }
                    }
                }
            }
        }
    }
}

/** Bottle millilitres per day for the last 14 days. Drawn left-to-right like any time axis. */
@Composable
private fun BottleChart(byDay: Map<LocalDate, List<Event>>) {
    val today = LocalDate.now()
    val days = (13 downTo 0).map { today.minusDays(it.toLong()) }
    val values = days.map { d -> summarize(byDay[d].orEmpty()).bottleMl }
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)

    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp)) {
            Text("מ״ל בבקבוק ביום – 14 ימים", fontWeight = FontWeight.Medium)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(140.dp).padding(top = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    days.forEachIndexed { i, d ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (values[i] > 0) Text("${values[i]}", fontSize = 8.sp, maxLines = 1)
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height((100f * values[i] / max).dp.coerceAtLeast(2.dp))
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(if (d == today) MaterialTheme.colorScheme.primary else Palette.feed),
                            )
                            Text("${d.dayOfMonth}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
