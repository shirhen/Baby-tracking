package com.shirhen.babytracker.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import com.shirhen.babytracker.R
import com.shirhen.babytracker.data.Role

import java.time.LocalDate
import com.shirhen.babytracker.data.Medication
import com.shirhen.babytracker.alerts.Notifier
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build
import android.Manifest
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
import com.shirhen.babytracker.data.nextBreastSide
import com.shirhen.babytracker.data.nextSideOf
import com.shirhen.babytracker.guide.FeedingGuide
import com.shirhen.babytracker.sync.FamilySync
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

class MainActivity : ComponentActivity() {
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppRoot() }
        // Pattern alerts and medication reminders need permission to notify (Android 13+). Ask once.
        val prefs = Repo.prefs
        if (Build.VERSION.SDK_INT >= 33 && !prefs.askedNotificationPermission &&
            (prefs.patternAlerts.value || prefs.medReminders.value) && !Notifier.canNotify(this)
        ) {
            prefs.askedNotificationPermission = true
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-attach listeners in case the process was idle for a long time.
        if (Repo.prefs.familyId.value != null) FamilySync.start()
    }
}

private data class Editing(val event: Event, val isNew: Boolean)

/** Which medication dialog is open. [preselect] is a medication id to tick in advance. */
private data class GiveMed(val preselect: String?)
private data class EditMed(val med: Medication?)
private data class CheckIn(val event: Event?)

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
    val medsFlow = remember { Repo.db.meds().observeAll() }
    val meds by medsFlow.collectAsState(initial = emptyList())
    var giveMed by remember { mutableStateOf<GiveMed?>(null) }
    var editMed by remember { mutableStateOf<EditMed?>(null) }
    var checkIn by remember { mutableStateOf<CheckIn?>(null) }
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
    val guideHint = guide?.perFeedMinMl?.let { "יעד משוער לארוחה: $it\u200E–\u200E${guide.perFeedMaxMl} מ״ל" }
    val suggestedSide = events.nextBreastSide()

    val startNew: (EventType) -> Unit = { type ->
        when (type) {
            EventType.MED -> giveMed = GiveMed(null)
            EventType.CHECKIN -> checkIn = CheckIn(null)
            else -> editing = Editing(newEvent(type, events), true)
        }
    }
    val role by FamilySync.myRole.collectAsState()
    val myName by Repo.prefs.userName.collectAsState()
    val startEdit: (Event) -> Unit = { e ->
        // Recorders change only what they logged themselves.
        val allowed = role == Role.PARENT || (e.createdBy.isNotBlank() && e.createdBy == myName)
        when {
            !allowed -> scope.launch { snackbar.showSnackbar("רק הורה יכול לערוך רשומה שמישהו אחר הוסיף") }
            e.type == EventType.CHECKIN -> checkIn = CheckIn(e)
            else -> editing = Editing(e, false)
        }
    }
    val ageDays = profile?.birthEpochDay?.let { (LocalDate.now().toEpochDay() - it).toInt() }

    /** Saves right away and offers undo. Used by the journal text box and night-mode long press. */
    val quickSave: (Event) -> Unit = { e ->
        scope.launch {
            Repo.save(e)
            val result = snackbar.showSnackbar(
                message = "נרשם: ${Fmt.title(e.type)} · ${Fmt.describe(e)} · ${Fmt.time(e.time)}",
                actionLabel = "ביטול",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) Repo.delete(e)
        }
    }
    val repeatLast: (EventType) -> Unit = { type ->
        var e = newEvent(type, events)
        if (type == EventType.FEED && (e.feedKind == FeedKind.BREAST || e.feedKind == FeedKind.MIXED)) {
            e = e.copy(lastSide = suggestedSide, breastSides = suggestedSide)
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
                // Medications and the daily summary need a choice, so a long press opens them.
                onQuickLog = { t -> if (t == EventType.MED || t == EventType.CHECKIN) startNew(t) else repeatLast(t) },
                onExit = { nightOverride = false },
            )
        } else {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                floatingActionButton = { if (tab == Tab.CLOCK) ActionFab(startNew) },
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        listOf(
                            Triple(Tab.CLOCK, R.drawable.ic_nav_home, "בית"),
                            Triple(Tab.JOURNAL, R.drawable.ic_nav_journal, "יומן"),
                            Triple(Tab.DATA, R.drawable.ic_nav_stats, "נתונים"),
                            Triple(Tab.SETTINGS, R.drawable.ic_nav_settings, "הגדרות"),
                        ).forEach { (t, icon, label) ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = { AppIcon(icon) },
                                label = { Text(label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Palette.primary,
                                    selectedTextColor = Palette.primary,
                                    indicatorColor = Palette.primarySoft,
                                ),
                            )
                        }
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
                        meds = meds,
                        onGiveMed = { id -> giveMed = GiveMed(id) },
                        onQuickMed = { m ->
                            quickSave(Event(type = EventType.MED, time = System.currentTimeMillis(), medId = m.id, medName = m.name))
                        },
                        onCheckIn = { e -> checkIn = CheckIn(e) },
                        modifier = m,
                    )
                    Tab.JOURNAL -> JournalScreen(events, guide, now, startNew, startEdit, quickSave, m)
                    Tab.DATA -> HistoryScreen(events, weights, profile, now, startEdit, m)
                    else -> SettingsScreen(profile, weights, meds, events, onEditMed = { editMed = EditMed(it) }, modifier = m)
                }
            }
        }

        giveMed?.let { g ->
            key(g) {
                MedGiveDialog(
                    meds = meds,
                    events = events,
                    preselect = g.preselect,
                    onDismiss = { giveMed = null },
                    onSave = { list ->
                        giveMed = null
                        scope.launch {
                            list.forEach { Repo.save(it) }
                            val result = snackbar.showSnackbar(
                                "נרשם: ${list.joinToString(", ") { it.medName ?: "" }} · ${Fmt.time(list.first().time)}",
                                actionLabel = "ביטול",
                                duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) list.forEach { Repo.delete(it) }
                        }
                    },
                    onAddMed = { editMed = EditMed(null) },
                )
            }
        }

        editMed?.let { em ->
            key(em) {
                MedEditDialog(
                    initial = em.med,
                    onDismiss = { editMed = null },
                    onSave = { m ->
                        editMed = null
                        scope.launch { Repo.saveMed(m) }
                    },
                    onDelete = { m ->
                        editMed = null
                        scope.launch { Repo.deleteMed(m) }
                    },
                )
            }
        }

        checkIn?.let { c ->
            key(c) {
                CheckInDialog(
                    initial = c.event,
                    ageDays = ageDays,
                    onDismiss = { checkIn = null },
                    onSave = { e ->
                        checkIn = null
                        scope.launch { Repo.save(e) }
                    },
                    onDelete = c.event?.let { ev -> { checkIn = null; scope.launch { Repo.delete(ev) } } },
                )
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
