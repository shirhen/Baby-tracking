@file:OptIn(ExperimentalMaterial3Api::class)

package com.shirhen.babytracker.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shirhen.babytracker.data.Event
import com.shirhen.babytracker.data.EventType
import com.shirhen.babytracker.data.MedSchedule
import com.shirhen.babytracker.data.MedStatus
import com.shirhen.babytracker.data.Medication
import com.shirhen.babytracker.data.timeList
import java.time.LocalTime

private val overdueRed = Color(0xFFB3261E)

/** One line about where a medication stands: next time, or overdue. */
fun medStatusLine(s: MedStatus, now: Long): String = when {
    s.overdueSlot != null -> "⚠️ לא ניתן ב-${Fmt.time(s.overdueSlot)}"
    s.nextDue != null -> "הבא: ${Fmt.time(s.nextDue)}" + if (Fmt.dayLabel(s.nextDue) != "היום") " (${Fmt.dayLabel(s.nextDue)})" else ""
    else -> "לפי הצורך"
}

/**
 * "Give medication": tick one or more medications, adjust the time if needed, save.
 * Each ticked medication becomes its own entry, so every one has its own timer.
 */
@Composable
fun MedGiveDialog(
    meds: List<Medication>,
    events: List<Event>,
    preselect: String?,
    onDismiss: () -> Unit,
    onSave: (List<Event>) -> Unit,
    onAddMed: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val active = meds.filter { it.active && !it.deleted }
    val selected = remember {
        mutableStateListOf<String>().apply {
            when {
                preselect != null -> add(preselect)
                active.size == 1 -> add(active.first().id)
                else -> active.filter { MedSchedule.status(it, events, now).overdueSlot != null }.forEach { add(it.id) }
            }
        }
    }
    var time by remember { mutableStateOf(now) }
    var note by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(24.dp), modifier = Modifier.systemBarsPadding().fillMaxWidth(0.94f).padding(vertical = 12.dp)) {
            Column(Modifier.padding(20.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("💊 מתן תרופה", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                TimeRow(time) { time = it }
                HorizontalDivider()
                if (active.isEmpty()) {
                    Muted("עוד לא הוגדרו תרופות. מוסיפים תרופה עם השם, המינון והשעות שבהן נותנים אותה.")
                }
                active.forEach { m ->
                    val st = MedSchedule.status(m, events, now)
                    val on = m.id in selected
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (on) Palette.med else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { if (on) selected.remove(m.id) else selected.add(m.id) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Checkbox(checked = on, onCheckedChange = { if (it) selected.add(m.id) else selected.remove(m.id) })
                        Column(Modifier.weight(1f)) {
                            Text(m.name + if (m.dose.isNotBlank()) " · ${m.dose}" else "", fontWeight = FontWeight.Bold, color = Palette.ink)
                            Text(
                                (st.lastGiven?.let { "ניתן ${Fmt.ago(it.time, now)} (${Fmt.time(it.time)})" } ?: "עוד לא ניתן") +
                                    " · " + medStatusLine(st, now),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (st.overdueSlot != null) overdueRed else Palette.muted,
                            )
                        }
                    }
                }
                TextButton(onClick = onAddMed) { Text("＋ תרופה חדשה") }
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("הערה (לא חובה)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("ביטול") }
                    Spacer(Modifier.width(8.dp))
                    Button(enabled = selected.isNotEmpty(), onClick = {
                        onSave(active.filter { it.id in selected }.map { m ->
                            Event(type = EventType.MED, time = time, medId = m.id, medName = m.name, note = note.trim())
                        })
                    }) { Text("נתתי") }
                }
            }
        }
    }
}

/** Add or edit a medication: name, dose, daily times, active. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedEditDialog(
    initial: Medication?,
    onDismiss: () -> Unit,
    onSave: (Medication) -> Unit,
    onDelete: ((Medication) -> Unit)?,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var dose by remember { mutableStateOf(initial?.dose ?: "") }
    var active by remember { mutableStateOf(initial?.active ?: true) }
    val times = remember { mutableStateListOf<LocalTime>().apply { addAll(initial?.timeList.orEmpty()) } }

    fun addTime() {
        val base = times.lastOrNull()?.plusHours(8) ?: LocalTime.of(8, 0)
        TimePickerDialog(context, { _, h, m ->
            val t = LocalTime.of(h, m)
            if (t !in times) { times.add(t); times.sort() }
        }, base.hour, base.minute, true).show()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(24.dp), modifier = Modifier.systemBarsPadding().fillMaxWidth(0.94f).padding(vertical = 12.dp)) {
            Column(Modifier.padding(20.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (initial == null) "💊 תרופה חדשה" else "💊 עריכת תרופה", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("שם התרופה") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = dose, onValueChange = { dose = it }, label = { Text("מינון (לא חובה), למשל 2.5 מ״ל") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("שעות מתן ביום", fontWeight = FontWeight.Medium)
                Muted("בלי שעות = לפי הצורך. לחיצה על שעה מוחקת אותה.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    times.forEach { t ->
                        InputChip(selected = true, onClick = { times.remove(t) }, label = { Text("%02d:%02d ✕".format(t.hour, t.minute)) })
                    }
                    AssistChip(onClick = { addTime() }, label = { Text("＋ שעה") })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("פעילה")
                        Muted("תרופה שהופסקה נשארת בהיסטוריה אבל לא מופיעה בכפתור ובתזכורות.")
                    }
                    Switch(checked = active, onCheckedChange = { active = it })
                }
            }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (initial != null && onDelete != null) {
                        TextButton(onClick = { onDelete(initial) }) { Text("מחיקה", color = overdueRed) }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("ביטול") }
                    Spacer(Modifier.width(8.dp))
                    Button(enabled = name.isNotBlank(), onClick = {
                        val base = initial ?: Medication(name = name.trim())
                        onSave(base.copy(name = name.trim(), dose = dose.trim(), times = MedSchedule.parseTimes(times), active = active))
                    }) { Text("שמירה") }
                }
            }
        }
    }
}

/** Home card: a timer per medication and when the next dose is due. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun MedsCard(meds: List<Medication>, events: List<Event>, now: Long, onGive: (String?) -> Unit, onQuick: (Medication) -> Unit) {
    val active = meds.filter { it.active && !it.deleted }
    if (active.isEmpty()) return
    val lastAny = events.firstOrNull { it.type == EventType.MED }
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("💊 תרופות", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    lastAny?.let { Muted("אחרונה: ${it.medName ?: "תרופה"} · לפני ${Fmt.since(it.time, now)}") }
                    Muted("לחיצה ארוכה על תרופה = ניתנה עכשיו")
                }
                Button(onClick = { onGive(null) }) { Text("מתן תרופה") }
            }
            active.forEach { m ->
                val st = MedSchedule.status(m, events, now)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Palette.med)
                        .combinedClickable(onClick = { onGive(m.id) }, onLongClick = { onQuick(m) })
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(m.name + if (m.dose.isNotBlank()) " · ${m.dose}" else "", fontWeight = FontWeight.Medium, color = Palette.ink)
                        Text(
                            medStatusLine(st, now),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (st.overdueSlot != null) overdueRed else Palette.muted,
                            fontWeight = if (st.overdueSlot != null) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(st.lastGiven?.let { Fmt.since(it.time, now) } ?: "—", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.ink)
                        Text(st.lastGiven?.let { "מאז ${Fmt.time(it.time)}" } ?: "עוד לא ניתן", style = MaterialTheme.typography.labelSmall, color = Palette.muted)
                    }
                }
            }
        }
    }
}

/** Settings list of medications with add / edit. */
@Composable
fun MedsSettings(meds: List<Medication>, onEdit: (Medication?) -> Unit) {
    if (meds.isEmpty()) Muted("אין תרופות. מוסיפים תרופה עם השעות שבהן נותנים אותה, ואז מופיעים טיימר, תזכורת וכפתור מתן.")
    meds.forEach { m ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { onEdit(m) }.padding(vertical = 6.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(m.name + if (m.dose.isNotBlank()) " · ${m.dose}" else "", fontWeight = FontWeight.Medium)
                Muted(MedSchedule.formatTimes(m) + if (!m.active) " · הופסקה" else "")
            }
            Text("עריכה", color = MaterialTheme.colorScheme.primary)
        }
    }
    OutlinedButton(onClick = { onEdit(null) }, modifier = Modifier.fillMaxWidth()) { Text("＋ תרופה חדשה") }
}
