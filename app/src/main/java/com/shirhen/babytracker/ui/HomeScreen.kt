package com.shirhen.babytracker.ui

import com.shirhen.babytracker.data.Repo
import com.shirhen.babytracker.data.PatternAlert
import com.shirhen.babytracker.data.Medication
import com.shirhen.babytracker.data.Alerts
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shirhen.babytracker.data.BabyProfile
import com.shirhen.babytracker.data.Event
import com.shirhen.babytracker.data.EventType
import com.shirhen.babytracker.data.summarize
import com.shirhen.babytracker.guide.FeedingGuide

/** Home: the 24-hour clock, then the small "since" cards, guide and the last few entries. */
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
    onOpenJournal: () -> Unit,
    onNightMode: () -> Unit,
    meds: List<Medication>,
    onGiveMed: (String?) -> Unit,
    onQuickMed: (Medication) -> Unit,
    onCheckIn: (Event?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val patternOn by Repo.prefs.patternAlerts.collectAsState()
    val remindersOn by Repo.prefs.medReminders.collectAsState()
    val diaperWarningOn by Repo.prefs.diaperWarning.collectAsState()
    val alerts = remember(events, meds, now / 60_000, patternOn, remindersOn) {
        (if (remindersOn) Alerts.medReminders(meds, events, now) else emptyList()) +
            (if (patternOn) Alerts.pattern(events, now) else emptyList())
    }
    val last24 = events.filter { it.time >= now - DAY_MS }
    val s24 = summarize(last24)
    fun last(type: EventType) = events.firstOrNull { it.type == type }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        profile?.name?.takeIf { it.isNotBlank() } ?: "התינוק שלנו",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    guide?.let { g ->
                        val corrected = g.correctedDays?.let { if (it >= 0) " · מתוקן: ${Fmt.age(it)}" else "" } ?: ""
                        Muted("גיל: ${Fmt.age(g.chronoDays)}$corrected")
                    }
                }
                IconButton(onClick = onNightMode) { Icon(Icons.Filled.Bedtime, contentDescription = "מצב לילה") }
            }
        }

        if (alerts.isNotEmpty()) {
            item { AlertsCard(alerts) { onGiveMed(null) } }
        }

        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                ClockDial(
                    events = last24,
                    now = now,
                    lastFeed = last(EventType.FEED),
                    suggestedSide = suggestedSide,
                    onCenterClick = { onNew(EventType.FEED) },
                    modifier = Modifier.widthIn(max = 340.dp),
                )
                DialLegend()
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniSince("🧷 חיתול", Palette.diaper, last(EventType.DIAPER), now, Modifier.weight(1f)) { onNew(EventType.DIAPER) }
                    MiniSince(
                        "💩 קקי אחרון", Palette.feed,
                        events.firstOrNull { it.type == EventType.DIAPER && it.dirty }, now, Modifier.weight(1f),
                    ) { onNew(EventType.DIAPER) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniSince("🫧 שאיבה", Palette.pump, last(EventType.PUMP), now, Modifier.weight(1f)) { onNew(EventType.PUMP) }
                    MiniSince("🛁 מקלחת", Palette.bath, last(EventType.BATH), now, Modifier.weight(1f)) { onNew(EventType.BATH) }
                }
            }
        }

        item { MedsCard(meds, events, now, onGive = onGiveMed, onQuick = onQuickMed) }

        item { CheckInCard(events, onOpen = onCheckIn) }

        item { GuideCard(guide, s24.bottleMl, s24.breastMin, onOpenSettings) }

        item {
            val haveFullDay = events.any { it.time < now - DAY_MS }
            DiaperCard(s24.wet, s24.dirty, guide?.chronoDays, haveFullDay, diaperWarningOn)
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("אחרונים")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onOpenJournal) { Text("ליומן המלא") }
            }
        }
        if (events.isEmpty()) {
            item { Muted("עוד לא תועד כלום. הכפתור ＋ למטה, הלחיצה על מרכז השעון, או הווידג'ט.") }
        }
        items(events.take(3), key = { it.id }) { e -> EventRow(e, onClick = { onEdit(e) }) }
    }
}

@Composable
private fun MiniSince(label: String, color: Color, last: Event?, now: Long, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = color, contentColor = Palette.ink),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(if (last == null) "—" else Fmt.since(last.time, now), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(last?.let { Fmt.time(it.time) } ?: " ", style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** ＋ button that fans out the four actions. */
@Composable
fun ActionFab(onPick: (EventType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AnimatedVisibility(visible = open) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(EventType.CHECKIN, EventType.MED, EventType.BATH, EventType.PUMP, EventType.DIAPER, EventType.FEED).forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(10.dp), tonalElevation = 2.dp, shadowElevation = 2.dp) {
                            Text(Fmt.title(t), modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        SmallFloatingActionButton(
                            onClick = { open = false; onPick(t) },
                            containerColor = Palette.light(t),
                            contentColor = Palette.ink,
                        ) { Text(Fmt.emoji(t), fontSize = 18.sp) }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { open = !open }) {
            Icon(if (open) Icons.Filled.Close else Icons.Filled.Add, contentDescription = if (open) "סגירה" else "הוספה")
        }
    }
}

/** Changes from the usual pattern and overdue medications, shown at the top of the home screen. */
@Composable
private fun AlertsCard(alerts: List<PatternAlert>, onGiveMed: () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Palette.warn, contentColor = Palette.ink)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            alerts.forEach { a ->
                Column(Modifier.fillMaxWidth().let { if (a.isMedReminder) it.clickable(onClick = onGiveMed) else it }) {
                    Text(a.title, fontWeight = FontWeight.Bold)
                    Text(a.text, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (alerts.any { !it.isMedReminder }) {
                Text(Alerts.NOTE, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
