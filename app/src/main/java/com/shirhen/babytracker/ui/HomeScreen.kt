package com.shirhen.babytracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shirhen.babytracker.data.BabyProfile
import com.shirhen.babytracker.data.Event
import com.shirhen.babytracker.data.EventType
import com.shirhen.babytracker.data.startMillis
import com.shirhen.babytracker.data.summarize
import com.shirhen.babytracker.guide.FeedingGuide
import java.time.LocalDate

@Composable
fun HomeScreen(
    events: List<Event>,
    profile: BabyProfile?,
    guide: FeedingGuide.Result?,
    now: Long,
    suggestedSide: String?,
    onNew: (EventType) -> Unit,
    onEdit: (Event) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val todayStart = LocalDate.now().startMillis()
    val today = events.filter { it.time >= todayStart }
    val last24 = events.filter { it.time >= now - DAY_MS }
    val s24 = summarize(last24)
    fun last(type: EventType) = events.firstOrNull { it.type == type }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(
                    profile?.name?.takeIf { it.isNotBlank() } ?: "התינוק שלנו",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                guide?.let { g ->
                    val corrected = g.correctedDays?.let { if (it >= 0) " · גיל מתוקן: ${Fmt.age(it)}" else " · ${-it} ימים לפני תאריך הלידה המשוער" } ?: ""
                    Muted("גיל: ${Fmt.age(g.chronoDays)}$corrected")
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickTile("🍼", "האכלה", Palette.feed, Modifier.weight(1f)) { onNew(EventType.FEED) }
                QuickTile("🧷", "חיתול", Palette.diaper, Modifier.weight(1f)) { onNew(EventType.DIAPER) }
                QuickTile("🫧", "שאיבה", Palette.pump, Modifier.weight(1f)) { onNew(EventType.PUMP) }
                QuickTile("🛁", "מקלחת", Palette.bath, Modifier.weight(1f)) { onNew(EventType.BATH) }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SinceCard(EventType.FEED, last(EventType.FEED), now, Palette.feed, Modifier.weight(1f),
                        extra = suggestedSide?.let { "צד הבא: ${Fmt.side(it)}" }) { onNew(EventType.FEED) }
                    SinceCard(EventType.DIAPER, last(EventType.DIAPER), now, Palette.diaper, Modifier.weight(1f)) { onNew(EventType.DIAPER) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SinceCard(EventType.PUMP, last(EventType.PUMP), now, Palette.pump, Modifier.weight(1f)) { onNew(EventType.PUMP) }
                    SinceCard(EventType.BATH, last(EventType.BATH), now, Palette.bath, Modifier.weight(1f)) { onNew(EventType.BATH) }
                }
            }
        }

        item { GuideCard(guide, s24.bottleMl, s24.breastMin, onOpenSettings) }

        item {
            val haveFullDay = events.any { it.time < now - DAY_MS }
            DiaperCard(s24.wet, s24.dirty, guide?.chronoDays, haveFullDay)
        }

        item {
            val st = summarize(today)
            SectionTitle("היום")
            Muted("${st.feeds} האכלות · ${st.bottleMl} מ״ל בבקבוק · ${st.breastMin} ד׳ הנקה · ${st.wet} רטובים · ${st.dirty} מלוכלכים · שאיבה ${st.pumpedMl} מ״ל")
        }
        if (today.isEmpty()) {
            item { Muted("עוד לא תועד כלום היום. הכפתורים למעלה, או הווידג'ט במסך הבית.") }
        }
        items(today, key = { it.id }) { e -> EventRow(e, onClick = { onEdit(e) }) }
    }
}

@Composable
private fun SinceCard(
    type: EventType,
    last: Event?,
    now: Long,
    color: Color,
    modifier: Modifier = Modifier,
    extra: String? = null,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("${Fmt.emoji(type)} ${Fmt.title(type)}", style = MaterialTheme.typography.labelLarge)
            Text(
                if (last == null) "—" else Fmt.ago(last.time, now),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            if (last != null) {
                Text("${Fmt.time(last.time)} · ${Fmt.describe(last)}", style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            extra?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium) }
        }
    }
}

@Composable
private fun GuideCard(guide: FeedingGuide.Result?, bottle24: Int, breast24: Int, onOpenSettings: () -> Unit) {
    var showSources by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("כמה לאכול – הערכה", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (guide == null) {
                Muted("כדי לחשב, הזינו תאריך לידה, שבוע לידה ומשקל.")
                Button(onClick = onOpenSettings) { Text("להגדרות") }
                return@Column
            }
            if (guide.perFeedMinMl != null) {
                Text("${guide.perFeedMinMl}–${guide.perFeedMaxMl} מ״ל לארוחה", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            val feedsSource = if (guide.feedsFromLog) "לפי התיעוד ב-24 השעות האחרונות" else "ממוצע מקובל לגיל"
            Muted("לפי ${guide.feedsPerDay} ארוחות ביממה ($feedsSource)")
            if (guide.dailyMinMl != null && guide.dailyMaxMl != null) {
                Text("ביממה: ${guide.dailyMinMl}–${guide.dailyMaxMl} מ״ל · ב-24 ש׳ האחרונות: $bottle24 מ״ל בבקבוק")
                LinearProgressIndicator(
                    progress = { (bottle24.toFloat() / guide.dailyMinMl).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                )
            }
            if (breast24 > 0) {
                Muted("הנקה ישירה לא נמדדת במ״ל ($breast24 ד׳ ב-24 ש׳), לכן הסכום למעלה כולל רק בקבוקים. החיתולים הם המדד בהנקה.")
            }
            Muted(guide.basis)
            guide.warning?.let {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.warn).padding(10.dp)) {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
            TextButton(onClick = { showSources = !showSources }, contentPadding = PaddingValues(0.dp)) {
                Text(if (showSources) "הסתרת מקורות" else "מקורות והסבר")
            }
            if (showSources) {
                Muted(FeedingGuide.DISCLAIMER)
                Muted(FeedingGuide.SOURCES)
            }
        }
    }
}

@Composable
private fun DiaperCard(wet24: Int, dirty24: Int, chronoDays: Int?, haveFullDay: Boolean) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("🧷 חיתולים ב-24 שעות: $wet24 רטובים · $dirty24 מלוכלכים", fontWeight = FontWeight.Medium)
            val low = chronoDays != null && chronoDays >= 5 && haveFullDay && wet24 < 6
            if (low) {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.warn).padding(10.dp)) {
                    Text("פחות מ-6 חיתולים רטובים ביממה אחרי יום 5 נחשב סימן אזהרה. כדאי לבדוק עם רופא/ת הילדים או טיפת חלב.")
                }
            } else {
                Muted("אחרי יום 5: מצופים לפחות 6 חיתולים רטובים ביממה.")
            }
        }
    }
}

@Composable
fun EventRow(e: Event, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = when (e.type) {
            EventType.FEED -> Palette.feed
            EventType.DIAPER -> Palette.diaper
            EventType.PUMP -> Palette.pump
            EventType.BATH -> Palette.bath
        }
        Box(Modifier.size(40.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Text(Fmt.emoji(e.type), fontSize = 20.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${Fmt.time(e.time)} · ${Fmt.title(e.type)}", fontWeight = FontWeight.Medium)
            Text(Fmt.describe(e), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (e.createdBy.isNotBlank()) Muted(e.createdBy)
    }
}
