package com.shirhen.babytracker.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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
import com.shirhen.babytracker.data.Repo
import com.shirhen.babytracker.data.lastBreastSide
import com.shirhen.babytracker.data.nextSideOf
import com.shirhen.babytracker.guide.FeedingGuide
import com.shirhen.babytracker.sync.FamilySync
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BabyTheme { AppRoot() } }
    }

    override fun onResume() {
        super.onResume()
        // Re-attach listeners in case the process was idle for a long time.
        if (Repo.prefs.familyId.value != null) FamilySync.start()
    }
}

private data class Editing(val event: Event, val isNew: Boolean)

@Composable
private fun AppRoot() {
    val eventsFlow = remember { Repo.db.events().observeAll() }
    val profileFlow = remember { Repo.db.profile().observe() }
    val weightsFlow = remember { Repo.db.weights().observeAll() }
    val events by eventsFlow.collectAsState(initial = emptyList())
    val profile by profileFlow.collectAsState(initial = null)
    val weights by weightsFlow.collectAsState(initial = emptyList())

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var editing by remember { mutableStateOf<Editing?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

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

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Filled.Home, null) }, label = { Text("בית") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Filled.History, null) }, label = { Text("היסטוריה") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Filled.Settings, null) }, label = { Text("הגדרות") })
            }
        },
    ) { pad ->
        val m = Modifier.padding(pad)
        when (tab) {
            0 -> HomeScreen(events, profile, guide, now, suggestedSide, startNew, startEdit, { tab = 2 }, m)
            1 -> HistoryScreen(events, weights, profile, startEdit, m)
            else -> SettingsScreen(profile, weights, m)
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

const val DAY_MS = 24 * 3_600_000L
