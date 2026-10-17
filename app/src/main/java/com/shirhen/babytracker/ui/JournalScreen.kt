package com.shirhen.babytracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.shirhen.babytracker.data.Event
import com.shirhen.babytracker.data.EventType
import com.shirhen.babytracker.data.Repo
import com.shirhen.babytracker.data.toLocalDate
import com.shirhen.babytracker.guide.FeedingGuide
import java.time.LocalDate

private sealed interface JournalItem {
    data class Bubble(val event: Event, val sincePrevFeed: Long?) : JournalItem
    data class Day(val day: LocalDate) : JournalItem
}

private val mineBg = Color(0xFFE6F1FB)
private val mineInk = Color(0xFF0C447C)
private val otherBg = Color(0xFFFAECE7)
private val otherInk = Color(0xFF712B13)

/**
 * The shared log as a conversation: newest at the bottom, each parent on their own side,
 * with quick chips and a free-text box that understands short phrases ("תמל 90", "רטוב").
 */
@Composable
fun JournalScreen(
    events: List<Event>,
    guide: FeedingGuide.Result?,
    now: Long,
    onNew: (EventType) -> Unit,
    onEdit: (Event) -> Unit,
    onQuickSave: (Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    val me by Repo.prefs.userName.collectAsState()
    val items = remember(events) { buildItems(events) }
    var text by rememberSaveable { mutableStateOf("") }
    val parsed = remember(text, now) { QuickParser.parse(text, now) }

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(items, key = { if (it is JournalItem.Bubble) it.event.id else "day-${(it as JournalItem.Day).day}" }) { item ->
                when (item) {
                    is JournalItem.Day -> DaySeparator(item.day)
                    is JournalItem.Bubble -> {
                        val mine = me.isBlank() || item.event.createdBy.isBlank() || item.event.createdBy == me
                        Bubble(item.event, item.sincePrevFeed, mine) { onEdit(item.event) }
                    }
                }
            }
            if (items.isEmpty()) {
                item { Muted("היומן ריק. כתבו למטה למשל \"תמל 90\" או לחצו על אחד הכפתורים.", Modifier.padding(16.dp)) }
            }
        }

        HorizontalDivider()
        Column(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val lastFeed = events.firstOrNull { it.type == EventType.FEED }
            val status = buildList {
                lastFeed?.let { add("עברו ${Fmt.since(it.time, now)} מההאכלה") }
                guide?.perFeedMinMl?.let { add("יעד $it–${guide.perFeedMaxMl} מ״ל") }
            }.joinToString(" · ")
            if (status.isNotEmpty()) Muted(status)

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                EventType.entries.forEach { t ->
                    AssistChip(onClick = { onNew(t) }, label = { Text("${Fmt.emoji(t)} ${Fmt.title(t)}") })
                }
            }

            val send = {
                parsed?.let {
                    onQuickSave(it)
                    text = ""
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("תמל 90 · רטוב · הנקה 15") },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    modifier = Modifier.weight(1f),
                )
                FilledIconButton(onClick = { send() }, enabled = parsed != null, modifier = Modifier.padding(start = 8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "רישום")
                }
            }
            when {
                parsed != null -> Text(
                    "יירשם: ${Fmt.emoji(parsed.type)} ${Fmt.describe(parsed)} · ${Fmt.time(parsed.time)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
                text.isNotBlank() -> Muted("לא זוהה. דוגמאות: ${QuickParser.EXAMPLES}")
            }
        }
    }
}

/** Newest first (the list is reversed on screen), with a day header above each day's entries. */
private fun buildItems(events: List<Event>): List<JournalItem> {
    val feeds = events.filter { it.type == EventType.FEED }
    val prevFeedGap = feeds.zipWithNext { newer, older -> newer.id to (newer.time - older.time) }.toMap()
    val out = mutableListOf<JournalItem>()
    events.forEachIndexed { i, e ->
        out += JournalItem.Bubble(e, prevFeedGap[e.id])
        val day = e.time.toLocalDate()
        val nextDay = events.getOrNull(i + 1)?.time?.toLocalDate()
        if (nextDay != day) out += JournalItem.Day(day)
    }
    return out
}

@Composable
private fun DaySeparator(day: LocalDate) {
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(
            "${Fmt.dayLabel(day)} · ${Fmt.date(day)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun Bubble(e: Event, sincePrevFeed: Long?, mine: Boolean, onClick: () -> Unit) {
    // In the right-to-left layout, Start is the right edge: mine on the right, the other parent on the left.
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterStart else Alignment.CenterEnd) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (mine) 4.dp else 16.dp, bottomEnd = if (mine) 16.dp else 4.dp,
                    ),
                )
                .background(if (mine) mineBg else otherBg)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            val ink = if (mine) mineInk else otherInk
            Text("${Fmt.emoji(e.type)} ${Fmt.title(e.type)}", color = ink, fontWeight = FontWeight.Bold)
            Text(Fmt.describe(e), color = ink, style = MaterialTheme.typography.bodyMedium)
            val meta = buildList {
                if (e.createdBy.isNotBlank()) add(e.createdBy)
                add(Fmt.time(e.time))
                sincePrevFeed?.let { add("${Fmt.since(0, it)} מהקודמת") }
            }.joinToString(" · ")
            Text(meta, color = ink.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
        }
    }
}
