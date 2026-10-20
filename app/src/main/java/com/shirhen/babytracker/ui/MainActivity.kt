package com.shirhen.babytracker.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.shirhen.babytracker.data.Event
import com.shirhen.babytracker.data.EventType
import com.shirhen.babytracker.data.FeedKind
import com.shirhen.babytracker.data.Repo
import com.shirhen.babytracker.data.lastBreastSide
import com.shirhen.babytracker.data.nextSideOf
import com.shirhen.babytracker.guide.FeedingGuide
import com.shirhen.babytracker.sync.FamilySync
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppRoot() }
    }

    override fun onResume() {
        super.onResume()
        // Re-attach listeners in case the process was idle for a long time.
        if (Repo.prefs.familyId.value != null) FamilySync.start()
    }
}

private data class Editing(val event: Event, val isNew: Boolean)

private object Tab {
    const val CLOCK = 0
    const val JOURNAL = 1
    const val DATA = 2
    const val SETTINGS = 3
}

@Composable
private fun AppRoot() {
    val eventsFlow = remember { Repo.db.events().observeAll() }
    val profileFlow = remember { Repo.db.profile().observe() }
    val weightsFlow = remember { Repo.db.weights().observeAll() }
    val events by eventsFlow.collectAsState(initial = emptyList())
    val profile by profileFlow.collectAsState(initial = null)
    val weights by weightsFlow.collectAsState(initial = emptyList())
    val nightAuto by Repo.prefs.nightAuto.collectAsState()

    var tab by rememberSaveable { mutableIntStateOf(Tab.CLOCK) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var editing by remember { mutableStateOf<Editing?>(null) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    // Night mode: automatic during night hours, or switched by hand. A manual choice lasts
    // until the next day/night boundary.
    val nightHours = isNightHour(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalTime())
    val autoNight = nightAuto && nightHours
    var nightOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(nightHours) { nightOverride = null }
    val night = nightOverride ?: autoNight

    val guide = remember(events, profile, weights, now) {
        FeedingGuide.compute(
            profile,
            weights.firstOrNull()?.grams,
            events.count { it.type == EventType.FEED && it.time >= now - DAY_MS },
        )
    }
    val guideHint = guide?.perFeedMinMl?.let { "יעד משוער לארוחה: $it–${guide.perFeedMaxMl} מ״ל" }
    val suggestedSide = nextSideOf(events.lastBreastSide())

    val startNew: (EventType) -> Unit = { type -> editing = Editing(newEvent(type, events), true) }
    val startEdit: (Event) -> Unit = { e -> editing = Editing(e, false) }

    /** Saves right away and offers undo. Used by the journal text box and night-mode long press. */
    val quickSave: (Event) -> Unit = { e ->
        scope.launch {
            Repo.save(e)
            val result = snackbar.showSnackbar(
                message = "נרשם: ${Fmt.emoji(e.type)} ${Fmt.describe(e)} · ${Fmt.time(e.time)}",
                actionLabel = "ביטול",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) Repo.delete(e)
        }
    }
    val repeatLast: (EventType) -> Unit = { type ->
        var e = newEvent(type, events)
        if (type == EventType.FEED && (e.feedKind == FeedKind.BREAST || e.feedKind == FeedKind.MIXED)) {
            e = e.copy(lastSide = suggestedSide)
        }
        quickSave(e)
    }

    BabyTheme(dark = night) {
        if (night) {
            NightScreen(
                events = events,
                now = now,
                suggestedSide = suggestedSide,
                snackbar = snackbar,
                onOpen = startNew,
                onQuickLog = repeatLast,
                onExit = { nightOverride = false },
            )
        } else {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                floatingActionButton = { if (tab == Tab.CLOCK) ActionFab(startNew) },
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(selected = tab == Tab.CLOCK, onClick = { tab = Tab.CLOCK }, icon = { Icon(Icons.Filled.Schedule, null) }, label = { Text("שעון") })
                        NavigationBarItem(selected = tab == Tab.JOURNAL, onClick = { tab = Tab.JOURNAL }, icon = { Icon(Icons.AutoMirrored.Filled.Chat, null) }, label = { Text("יומן") })
                        NavigationBarItem(selected = tab == Tab.DATA, onClick = { tab = Tab.DATA }, icon = { Icon(Icons.Filled.BarChart, null) }, label = { Text("נתונים") })
                        NavigationBarItem(selected = tab == Tab.SETTINGS, onClick = { tab = Tab.SETTINGS }, icon = { Icon(Icons.Filled.Settings, null) }, label = { Text("הגדרות") })
                    }
                },
            ) { pad ->
                val m = Modifier.padding(pad)
                when (tab) {
                    Tab.CLOCK -> HomeScreen(
                        events = events, profile = profile, guide = guide, now = now, suggestedSide = suggestedSide,
                        onNew = startNew, onEdit = startEdit,
                        onOpenSettings = { tab = Tab.SETTINGS },
                        onOpenJournal = { tab = Tab.JOURNAL },
                        onNightMode = { nightOverride = true },
                        modifier = m,
                    )
                    Tab.JOURNAL -> JournalScreen(events, guide, now, startNew, startEdit, quickSave, m)
                    Tab.DATA -> HistoryScreen(events, weights, profile, now, startEdit, m)
                    else -> SettingsScreen(profile, weights, m)
                }
            }
        }

        editing?.let { ed ->
            key(ed) {
                EventEditorDialog(
                    initial = ed.event,
                    isNew = ed.isNew,
                    suggestedSide = suggestedSide,
                    guideHint = guideHint,
                    onDismiss = { editing = null },
                    onSave = { e ->
                        scope.launch { Repo.save(e) }
                        editing = null
                    },
                    onDelete = if (ed.isNew) null else {
                        {
                            scope.launch { Repo.delete(ed.event) }
                            editing = null
                        }
                    },
                )
            }
        }
    }
}

const val DAY_MS = 24 * 3_600_000L
