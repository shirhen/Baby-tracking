@file:OptIn(ExperimentalFoundationApi::class)

package com.shirhen.babytracker.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shirhen.babytracker.data.Event
import com.shirhen.babytracker.data.EventType

/**
 * One-handed night screen: dark, huge targets, no small text to read.
 * Tap = open the entry with details. Long press = log "same as last time" right away (with undo).
 */
@Composable
fun NightScreen(
    events: List<Event>,
    now: Long,
    suggestedSide: String?,
    snackbar: SnackbarHostState,
    onOpen: (EventType) -> Unit,
    onQuickLog: (EventType) -> Unit,
    onExit: () -> Unit,
) {
    val lastFeed = events.firstOrNull { it.type == EventType.FEED }
    val lastDiaper = events.firstOrNull { it.type == EventType.DIAPER }

    Box(Modifier.fillMaxSize().background(Palette.nightBg).systemBarsPadding()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🌙 מצב לילה", color = Palette.nightMuted, fontSize = 16.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onExit) { Text("יציאה ☀️", color = Palette.nightText, fontSize = 16.sp) }
            }

            Column(
                Modifier.fillMaxWidth().padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (lastFeed == null) "—" else Fmt.since(lastFeed.time, now),
                    color = Palette.nightAmber,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    listOfNotNull("מאז האכלה", suggestedSide?.let { "צד הבא: ${Fmt.side(it)}" }).joinToString(" · "),
                    color = Palette.nightText,
                    fontSize = 20.sp,
                )
                lastDiaper?.let {
                    Spacer(Modifier.height(6.dp))
                    Text("חיתול: ${Fmt.since(it.time, now)}", color = Palette.nightMuted, fontSize = 18.sp)
                }
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NightButton(EventType.FEED, Modifier.weight(1f), onOpen, onQuickLog)
                    NightButton(EventType.DIAPER, Modifier.weight(1f), onOpen, onQuickLog)
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NightButton(EventType.PUMP, Modifier.weight(1f), onOpen, onQuickLog)
                    NightButton(EventType.BATH, Modifier.weight(1f), onOpen, onQuickLog)
                }
            }

            Text(
                "לחיצה = פרטים · לחיצה ארוכה = כמו בפעם הקודמת",
                color = Palette.nightMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@Composable
private fun NightButton(type: EventType, modifier: Modifier, onOpen: (EventType) -> Unit, onQuickLog: (EventType) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(28.dp))
            .background(Palette.nightTile)
            .combinedClickable(
                onClick = { onOpen(type) },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onQuickLog(type)
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(Fmt.emoji(type), fontSize = 44.sp)
        Text(Fmt.title(type), color = Palette.nightText, fontSize = 22.sp, fontWeight = FontWeight.Medium)
    }
}
