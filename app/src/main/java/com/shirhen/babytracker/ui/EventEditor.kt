@file:OptIn(ExperimentalMaterial3Api::class)

package com.shirhen.babytracker.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shirhen.babytracker.data.Event
import com.shirhen.babytracker.data.EventType
import com.shirhen.babytracker.data.FeedKind
import kotlinx.coroutines.delay

/** A new entry pre-filled from the last one of the same kind, so a repeat feed is one tap. */
fun newEvent(type: EventType, history: List<Event>): Event {
    val now = System.currentTimeMillis()
    return when (type) {
        EventType.FEED -> {
            val last = history.firstOrNull { it.type == EventType.FEED }
            Event(
                type = type,
                time = now,
                feedKind = last?.feedKind ?: FeedKind.BREAST,
                formulaMl = last?.formulaMl ?: 0,
                expressedMl = last?.expressedMl ?: 0,
            )
        }
        EventType.DIAPER -> Event(type = type, time = now, wet = true)
        EventType.PUMP -> {
            val last = history.firstOrNull { it.type == EventType.PUMP }
            Event(type = type, time = now, pumpLeftMl = last?.pumpLeftMl ?: 0, pumpRightMl = last?.pumpRightMl ?: 0, durationMin = last?.durationMin ?: 0)
        }
        EventType.BATH -> Event(type = type, time = now)
    }
}

private fun FeedKind?.usesBreast() = this == FeedKind.BREAST || this == FeedKind.MIXED
private fun FeedKind?.usesFormula() = this == FeedKind.FORMULA || this == FeedKind.MIXED
private fun FeedKind?.usesExpressed() = this == FeedKind.EXPRESSED || this == FeedKind.MIXED

@Composable
fun EventEditorDialog(
    initial: Event,
    isNew: Boolean,
    suggestedSide: String?,
    guideHint: String?,
    onDismiss: () -> Unit,
    onSave: (Event) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var e by remember { mutableStateOf(initial) }
    // Breast time is kept in seconds so the live timer and the −/+ buttons share one value.
    var secL by remember { mutableIntStateOf(initial.leftMin * 60) }
    var secR by remember { mutableIntStateOf(initial.rightMin * 60) }
    var running by remember { mutableStateOf<String?>(null) }
    var totalMin by remember { mutableIntStateOf(initial.breastTotalMin) }
    // Total-only mode when editing an entry that was saved that way.
    var bySide by remember { mutableStateOf(!(initial.breastTotalMin > 0 && initial.leftMin + initial.rightMin == 0)) }

    LaunchedEffect(running) {
        val side = running ?: return@LaunchedEffect
        while (true) {
            delay(1000)
            if (side == "L") secL++ else secR++
        }
    }

    fun build(): Event = when (e.type) {
        EventType.FEED -> e.copy(
            leftMin = if (e.feedKind.usesBreast() && bySide) (secL + 30) / 60 else 0,
            rightMin = if (e.feedKind.usesBreast() && bySide) (secR + 30) / 60 else 0,
            breastTotalMin = if (e.feedKind.usesBreast() && !bySide) totalMin else 0,
            lastSide = if (e.feedKind.usesBreast()) e.lastSide else null,
            formulaMl = if (e.feedKind.usesFormula()) e.formulaMl else 0,
            expressedMl = if (e.feedKind.usesExpressed()) e.expressedMl else 0,
        )
        else -> e
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(0.94f).padding(vertical = 24.dp),
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "${Fmt.emoji(e.type)} ${Fmt.title(e.type)}" + if (isNew) "" else " – עריכה",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                TimeRow(e.time) { e = e.copy(time = it) }
                HorizontalDivider()

                when (e.type) {
                    EventType.FEED -> FeedFields(
                        e = e,
                        onChange = { e = it },
                        secL = secL, secR = secR,
                        setSecL = { secL = it.coerceAtLeast(0) }, setSecR = { secR = it.coerceAtLeast(0) },
                        running = running,
                        setRunning = { side ->
                            running = side
                            if (side != null) e = e.copy(lastSide = side)
                        },
                        bySide = bySide,
                        setBySide = { mode ->
                            if (!mode) running = null
                            // Carry the minutes over when switching, so nothing typed is lost.
                            if (!mode && totalMin == 0) totalMin = (secL + secR + 30) / 60
                            bySide = mode
                        },
                        totalMin = totalMin,
                        setTotalMin = { totalMin = it },
                        suggestedSide = suggestedSide,
                        guideHint = guideHint,
                    )
                    EventType.DIAPER -> DiaperButtons { wet, dirty -> onSave(e.copy(wet = wet, dirty = dirty)) }
                    EventType.PUMP -> PumpFields(e) { e = it }
                    EventType.BATH -> Muted("מקלחת נשמרת עם השעה שנבחרה.")
                }

                OutlinedTextField(
                    value = e.note,
                    onValueChange = { e = e.copy(note = it) },
                    label = { Text("הערה (לא חובה)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    if (onDelete != null) {
                        TextButton(onClick = onDelete) { Text("מחיקה", color = Color(0xFFB3261E)) }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("ביטול") }
                    if (e.type != EventType.DIAPER) {
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { running = null; onSave(build()) }) { Text("שמירה") }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedFields(
    e: Event,
    onChange: (Event) -> Unit,
    secL: Int,
    secR: Int,
    setSecL: (Int) -> Unit,
    setSecR: (Int) -> Unit,
    running: String?,
    setRunning: (String?) -> Unit,
    bySide: Boolean,
    setBySide: (Boolean) -> Unit,
    totalMin: Int,
    setTotalMin: (Int) -> Unit,
    suggestedSide: String?,
    guideHint: String?,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        FeedKind.entries.forEach { kind ->
            FilterChip(
                selected = e.feedKind == kind,
                onClick = { onChange(e.copy(feedKind = kind)) },
                label = { Text(Fmt.feedKind(kind)) },
            )
        }
    }

    if (e.feedKind.usesBreast()) {
        suggestedSide?.let { Muted("להתחיל בצד: ${Fmt.side(it)} (הפוך מהפעם הקודמת)") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("משך:")
            FilterChip(selected = bySide, onClick = { setBySide(true) }, label = { Text("לפי צד") })
            FilterChip(selected = !bySide, onClick = { setBySide(false) }, label = { Text("סה״כ") })
        }
        if (bySide) {
            Muted("מקלידים דקות, או מפעילים טיימר ▶ – מה שנוח. אפשר להשאיר צד ריק.")
            SideTimer("שמאל", secL, running == "L", { setRunning(if (running == "L") null else "L") }, setSecL)
            SideTimer("ימין", secR, running == "R", { setRunning(if (running == "R") null else "R") }, setSecR)
        } else {
            Stepper("משך כולל", totalMin, setTotalMin, step = 5, unit = "ד׳", max = 240)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("סיים בצד:")
            FilterChip(selected = e.lastSide == "L", onClick = { onChange(e.copy(lastSide = "L")) }, label = { Text("שמאל") })
            FilterChip(selected = e.lastSide == "R", onClick = { onChange(e.copy(lastSide = "R")) }, label = { Text("ימין") })
        }
    }
    if (e.feedKind.usesFormula()) {
        Stepper("תמ״ל", e.formulaMl, { onChange(e.copy(formulaMl = it)) })
    }
    if (e.feedKind.usesExpressed()) {
        Stepper("חלב שאוב", e.expressedMl, { onChange(e.copy(expressedMl = it)) })
    }
    if ((e.feedKind.usesFormula() || e.feedKind.usesExpressed()) && guideHint != null) {
        Muted(guideHint)
    }
}

@Composable
private fun SideTimer(label: String, seconds: Int, isRunning: Boolean, onToggle: () -> Unit, setSeconds: (Int) -> Unit) {
    val minutes = (seconds + 30) / 60
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.width(52.dp), style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = if (minutes == 0) "" else minutes.toString(),
            onValueChange = { v -> setSeconds((v.filter(Char::isDigit).take(3).toIntOrNull() ?: 0) * 60) },
            placeholder = { Text("0") },
            suffix = { Text("ד׳", fontSize = 12.sp) },
            singleLine = true,
            enabled = !isRunning,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(96.dp),
        )
        Spacer(Modifier.width(8.dp))
        FilledIconButton(onClick = onToggle) {
            Icon(if (isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (isRunning) "עצירה" else "טיימר")
        }
        Spacer(Modifier.width(8.dp))
        if (isRunning || seconds % 60 != 0) {
            Text(
                "%d:%02d".format(seconds / 60, seconds % 60),
                fontSize = 18.sp,
                fontWeight = if (isRunning) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun DiaperButtons(onPick: (wet: Boolean, dirty: Boolean) -> Unit) {
    Muted("לחיצה אחת שומרת:")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        QuickTile("💧", "רטוב", Palette.diaper, Modifier.weight(1f)) { onPick(true, false) }
        QuickTile("💩", "מלוכלך", Palette.feed, Modifier.weight(1f)) { onPick(false, true) }
        QuickTile("💧💩", "שניהם", Palette.pump, Modifier.weight(1f)) { onPick(true, true) }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun PumpFields(e: Event, onChange: (Event) -> Unit) {
    Stepper("צד שמאל", e.pumpLeftMl, { onChange(e.copy(pumpLeftMl = it)) })
    Stepper("צד ימין", e.pumpRightMl, { onChange(e.copy(pumpRightMl = it)) })
    Stepper("משך", e.durationMin, { onChange(e.copy(durationMin = it)) }, step = 5, unit = "ד׳", max = 180)
    Text("סה״כ: ${e.pumpLeftMl + e.pumpRightMl} מ״ל", fontWeight = FontWeight.Bold)
}
