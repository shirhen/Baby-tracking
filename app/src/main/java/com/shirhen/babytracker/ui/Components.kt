@file:OptIn(ExperimentalMaterial3Api::class)

package com.shirhen.babytracker.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shirhen.babytracker.data.toLocalDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Number field with − / + buttons. */
@Composable
fun Stepper(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    step: Int = 10,
    unit: String = "מ״ל",
    min: Int = 0,
    max: Int = 2000,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        FilledTonalIconButton(onClick = { onChange((value - step).coerceAtLeast(min)) }) {
            Icon(Icons.Filled.Remove, contentDescription = "פחות")
        }
        OutlinedTextField(
            value = if (value == 0) "" else value.toString(),
            onValueChange = { s -> onChange(s.filter(Char::isDigit).take(4).toIntOrNull()?.coerceIn(min, max) ?: 0) },
            placeholder = { Text("0") },
            suffix = { Text(unit, fontSize = 12.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(110.dp),
        )
        FilledTonalIconButton(onClick = { onChange((value + step).coerceAtMost(max)) }) {
            Icon(Icons.Filled.Add, contentDescription = "יותר")
        }
    }
}

/** "14:05 · היום" — tap the time for a time picker, the day for a date picker. */
@Composable
fun TimeRow(time: Long, onChange: (Long) -> Unit) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("מתי:", style = MaterialTheme.typography.bodyLarge)
        AssistChip(onClick = { pickTime(context, time, onChange) }, label = { Text(Fmt.time(time), fontSize = 18.sp) })
        AssistChip(onClick = { pickDate(context, time, onChange) }, label = { Text(Fmt.dayLabel(time)) })
        AssistChip(onClick = { onChange(System.currentTimeMillis()) }, label = { Text("עכשיו") })
    }
}

fun pickTime(context: Context, current: Long, onPicked: (Long) -> Unit) {
    val zone = ZoneId.systemDefault()
    val dt = Instant.ofEpochMilli(current).atZone(zone)
    TimePickerDialog(context, { _, h, m ->
        onPicked(dt.withHour(h).withMinute(m).withSecond(0).toInstant().toEpochMilli())
    }, dt.hour, dt.minute, true).show()
}

fun pickDate(context: Context, current: Long, onPicked: (Long) -> Unit) {
    val zone = ZoneId.systemDefault()
    val dt = Instant.ofEpochMilli(current).atZone(zone)
    DatePickerDialog(context, { _, y, mo, d ->
        onPicked(dt.withYear(y).withMonth(mo + 1).withDayOfMonth(d).toInstant().toEpochMilli())
    }, dt.year, dt.monthValue - 1, dt.dayOfMonth).apply {
        datePicker.maxDate = System.currentTimeMillis()
    }.show()
}

fun pickDay(context: Context, current: LocalDate?, onPicked: (LocalDate) -> Unit) {
    val d = current ?: LocalDate.now()
    DatePickerDialog(context, { _, y, mo, day -> onPicked(LocalDate.of(y, mo + 1, day)) }, d.year, d.monthValue - 1, d.dayOfMonth)
        .apply { datePicker.maxDate = System.currentTimeMillis() }
        .show()
}

/** Large rounded tile used for the quick-log buttons. */
@Composable
fun QuickTile(emoji: String, label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 26.sp)
        Spacer(Modifier.size(2.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
    )
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, align: TextAlign? = null) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = modifier, textAlign = align)
}

fun Long.dayOf(): LocalDate = toLocalDate()
