@file:OptIn(ExperimentalLayoutApi::class)

package com.shirhen.babytracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shirhen.babytracker.data.Event
import com.shirhen.babytracker.data.EventType
import com.shirhen.babytracker.data.Symptom
import com.shirhen.babytracker.data.startMillis
import com.shirhen.babytracker.data.symptomList
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

private val cryEmoji = listOf("😌", "🙂", "😐", "😢", "😭")
private val alertEmoji = listOf("😴", "😊", "👀", "🤩", "😫")
private val gasEmoji = listOf("—", "💨", "💨💨", "💨💨💨")
private val tummyOptions = listOf(0 to "לא היה", 5 to "קצת", 10 to "כ-10 ד׳", 20 to "20+ ד׳")

/** In the evening the summary is about the whole day; earlier it's about the last few hours. */
private fun defaultScope() = if (LocalTime.now().hour >= 19) "DAY" else "RECENT"

/**
 * "How was it?" — every row is one tap, every row is optional. Tap a selected option again to clear it.
 * Saving with only one row answered is fine.
 */
@Composable
fun CheckInDialog(
    initial: Event?,
    ageDays: Int?,
    onDismiss: () -> Unit,
    onSave: (Event) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var e by remember {
        mutableStateOf(initial ?: Event(type = EventType.CHECKIN, time = System.currentTimeMillis(), checkScope = defaultScope()))
    }
    var symptoms by remember { mutableStateOf(initial?.symptomList?.toSet() ?: emptySet()) }
    var healthy by remember { mutableStateOf(initial?.symptoms != null && initial.symptomList.isEmpty()) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth(0.96f).padding(vertical = 20.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("📝 איך היה?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = e.checkScope != "DAY", onClick = { e = e.copy(checkScope = "RECENT") }, label = { Text("השעות האחרונות") })
                    FilterChip(selected = e.checkScope == "DAY", onClick = { e = e.copy(checkScope = "DAY") }, label = { Text("היום כולו") })
                }
                Muted("הכל לא חובה – מסמנים רק מה שרוצים ושומרים.")

                ScaleRow("בכי", Fmt.cryLabels, cryEmoji, e.cryLevel) { e = e.copy(cryLevel = it) }
                ScaleRow("ערנות", Fmt.alertLabels, alertEmoji, e.alertLevel) { e = e.copy(alertLevel = it) }
                ScaleRow("גזים", Fmt.gasLabels, gasEmoji, e.gasLevel) { e = e.copy(gasLevel = it) }

                Text("זמן בטן", fontWeight = FontWeight.Medium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tummyOptions.forEach { (min, label) ->
                        FilterChip(
                            selected = e.tummyMin == min,
                            onClick = { e = e.copy(tummyMin = if (e.tummyMin == min) null else min) },
                            label = { Text(label) },
                        )
                    }
                }

                HorizontalDivider()
                Text("בריאות", fontWeight = FontWeight.Medium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = healthy,
                        onClick = {
                            healthy = !healthy
                            if (healthy) { symptoms = emptySet(); e = e.copy(tempC = null) }
                        },
                        label = { Text("✓ הכל בסדר") },
                    )
                    Symptom.entries.forEach { s ->
                        FilterChip(
                            selected = s in symptoms,
                            onClick = {
                                symptoms = if (s in symptoms) symptoms - s else symptoms + s
                                if (symptoms.isNotEmpty()) healthy = false
                            },
                            label = { Text(s.label) },
                        )
                    }
                }
                if (Symptom.FEVER in symptoms) {
                    Text("כמה מעלות? (לא חובה)", fontWeight = FontWeight.Medium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(37.5, 38.0, 38.5, 39.0, 39.5, 40.0).forEach { t ->
                            FilterChip(
                                selected = e.tempC == t,
                                onClick = { e = e.copy(tempC = if (e.tempC == t) null else t) },
                                label = { Text("%.1f°".format(t)) },
                            )
                        }
                    }
                    e.tempC?.let { t ->
                        if (t >= 38.0 && (ageDays ?: 999) < 90) {
                            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.warn).padding(10.dp)) {
                                Text(
                                    "בתינוק מתחת לגיל 3 חודשים, חום של 38° ומעלה (מדידה רקטלית) מצריך פנייה מיידית לרופא/ה או למוקד, גם אם הוא נראה בסדר.",
                                    color = Palette.ink,
                                )
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (onDelete != null) TextButton(onClick = onDelete) { Text("מחיקה", color = Color(0xFFB3261E)) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("ביטול") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val sym = when {
                            symptoms.isNotEmpty() -> symptoms.sortedBy { it.ordinal }.joinToString(",") { it.name }
                            healthy -> "" // answered: nothing wrong
                            else -> null // not answered
                        }
                        onSave(e.copy(symptoms = sym, tempC = if (Symptom.FEVER in symptoms) e.tempC else null))
                    }) { Text("שמירה") }
                }
            }
        }
    }
}

/** A row of big emoji buttons with the label of the chosen one underneath. */
@Composable
private fun ScaleRow(title: String, labels: List<String>, emoji: List<String>, value: Int?, onPick: (Int?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title + (value?.let { " · ${labels[it - 1]}" } ?: ""), fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                val level = i + 1
                val on = value == level
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (on) Palette.checkinMark.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onPick(if (on) null else level) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(emoji[i], fontSize = 22.sp)
                    Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }
    }
}

/** Home card: today's latest summary, or an invitation to add one. */
@Composable
fun CheckInCard(events: List<Event>, onOpen: (Event?) -> Unit) {
    val todayStart = LocalDate.now().startMillis()
    val today = events.filter { it.type == EventType.CHECKIN && it.time >= todayStart }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.clickable { onOpen(null) },
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📝 איך היה היום?", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { onOpen(null) }) { Text(if (today.isEmpty()) "לסכם" else "＋ עוד") }
            }
            if (today.isEmpty()) {
                Muted("בכי, ערנות, גזים, זמן בטן ובריאות – כמה לחיצות.")
            } else {
                today.take(3).forEach { c ->
                    Text(
                        "${Fmt.time(c.time)} · ${Fmt.describe(c)}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.clickable { onOpen(c) },
                    )
                }
            }
        }
    }
}
