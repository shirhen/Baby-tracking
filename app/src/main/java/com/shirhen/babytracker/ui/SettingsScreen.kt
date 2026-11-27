package com.shirhen.babytracker.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.shirhen.babytracker.R
import com.shirhen.babytracker.data.Event
import com.shirhen.babytracker.data.NIGHT_CHOICES
import com.shirhen.babytracker.data.NIGHT_MAX
import com.shirhen.babytracker.data.Role
import com.shirhen.babytracker.data.Tracker
import com.shirhen.babytracker.widget.WidgetRefresher
import kotlinx.coroutines.delay

import com.shirhen.babytracker.data.Medication
import com.shirhen.babytracker.alerts.Notifier
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shirhen.babytracker.BuildConfig
import com.shirhen.babytracker.data.BabyProfile
import com.shirhen.babytracker.data.Backup
import com.shirhen.babytracker.data.Repo
import com.shirhen.babytracker.data.Weight
import com.shirhen.babytracker.data.startMillis
import com.shirhen.babytracker.data.toLocalDate
import com.shirhen.babytracker.guide.FeedingGuide
import com.shirhen.babytracker.sync.FamilySync
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The pages behind the settings menu. Each opens as its own screen. */
private enum class SettingsPage(val title: String, @DrawableRes val icon: Int, val bg: Color, val ink: Color) {
    PROFILE("פרופיל התינוק", R.drawable.ic_baby, Palette.feed, Palette.feedInk),
    FAMILY("משפחה ותפקידים", R.drawable.ic_family, Palette.bath, Palette.bathInk),
    TRACKERS("מעקבים ומצב לילה", R.drawable.ic_trackers, Palette.diaper, Palette.diaperInk),
    MEDS("תרופות", R.drawable.ic_med, Palette.med, Palette.medInk),
    ALERTS("התראות", R.drawable.ic_bell, Palette.poop, Palette.poopInk),
    DISPLAY("תצוגה", R.drawable.ic_moon, Palette.pump, Palette.pumpInk),
    BACKUP("גיבוי וייצוא", R.drawable.ic_backup, Palette.bath, Palette.bathInk),
    ABOUT("אודות", R.drawable.ic_about, Palette.neutral, Palette.neutralInk),
}

@Composable
fun SettingsScreen(
    profile: BabyProfile?,
    weights: List<Weight>,
    meds: List<Medication>,
    events: List<Event>,
    onEditMed: (Medication?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pageName by rememberSaveable { mutableStateOf<String?>(null) }
    val page = pageName?.let { runCatching { SettingsPage.valueOf(it) }.getOrNull() }
    BackHandler(enabled = page != null) { pageName = null }

    if (page == null) {
        SettingsMenu(profile, weights, meds, events, modifier) { pageName = it.name }
        return
    }
    val role by FamilySync.myRole.collectAsState()
    val isParent = role == Role.PARENT
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { pageName = null }) { AppIcon(R.drawable.ic_back, description = "חזרה") }
            Text(page.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (page) {
                SettingsPage.PROFILE -> {
                    ProfileCard(profile, canEdit = isParent)
                    SettingsCard("שקילות") { Muted("הוספה ומעקב משקל: בלשונית נתונים ← משקל. החישוב של כמה לאכול משתמש בשקילה האחרונה.") }
                }
                SettingsPage.FAMILY -> {
                    UserCard()
                    FamilyCard(isParent)
                }
                SettingsPage.TRACKERS -> {
                    TrackersCard()
                    NightButtonsCard()
                }
                SettingsPage.MEDS -> SettingsCard(null) { MedsSettings(meds, onEditMed, canEdit = isParent) }
                SettingsPage.ALERTS -> AlertsSettingsCard()
                SettingsPage.DISPLAY -> NightCard()
                SettingsPage.BACKUP -> BackupCard(canRestore = isParent)
                SettingsPage.ABOUT -> SettingsCard(null) {
                    Text("גרסה ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Medium)
                    Muted(FeedingGuide.DISCLAIMER)
                    Muted(FeedingGuide.SOURCES)
                }
            }
        }
    }
}

/** First settings screen: the baby, then short rows that each open a page. No long scrolling. */
@Composable
private fun SettingsMenu(
    profile: BabyProfile?,
    weights: List<Weight>,
    meds: List<Medication>,
    events: List<Event>,
    modifier: Modifier,
    onOpen: (SettingsPage) -> Unit,
) {
    val prefs = Repo.prefs
    val familyId by prefs.familyId.collectAsState()
    val members by FamilySync.members.collectAsState()
    val role by FamilySync.myRole.collectAsState()
    val hidden by prefs.hiddenTrackers.collectAsState()
    val night by prefs.nightButtons.collectAsState()
    val nightAuto by prefs.nightAuto.collectAsState()
    val pattern by prefs.patternAlerts.collectAsState()
    val reminders by prefs.medReminders.collectAsState()
    val diaper by prefs.diaperWarning.collectAsState()
    val lastBackup by prefs.lastBackupAt.collectAsState()

    fun summary(p: SettingsPage): String = when (p) {
        SettingsPage.PROFILE -> ""
        SettingsPage.FAMILY -> when {
            !FamilySync.available -> "השיתוף לא פעיל בגרסה הזו"
            familyId == null -> "לא מחובר · שיתוף עם בן/בת הזוג"
            members.isEmpty() -> "מחובר · ${role.title}"
            else -> members.take(3).joinToString(", ") { it.name.ifBlank { "ללא שם" } } + " · את/ה: ${role.title}"
        }
        SettingsPage.TRACKERS -> {
            val shownNight = night.count { Tracker.of(it) !in hidden }
            "${Tracker.entries.size - hidden.size} מתוך ${Tracker.entries.size} פעילים · $shownNight כפתורי לילה"
        }
        SettingsPage.MEDS -> meds.filter { it.active && !it.deleted }.takeIf { it.isNotEmpty() }
            ?.joinToString(", ") { it.name } ?: "אין תרופות"
        SettingsPage.ALERTS -> listOfNotNull(
            "שינוי בדפוס".takeIf { pattern },
            "תרופות".takeIf { reminders },
            "חיתולים".takeIf { diaper },
        ).joinToString(", ").ifEmpty { "כבויות" }
        SettingsPage.DISPLAY -> if (nightAuto) "מצב לילה אוטומטי 22:00\u200E–\u200E06:00" else "מצב לילה ידני"
        SettingsPage.BACKUP -> if (lastBackup == 0L) "עוד לא נשמר גיבוי" else "גיבוי אחרון ${Fmt.ago(lastBackup)}"
        SettingsPage.ABOUT -> "גרסה ${BuildConfig.VERSION_NAME} · מקורות להמלצות"
    }
    val backupDue = events.isNotEmpty() && System.currentTimeMillis() - lastBackup > 7 * DAY_MS

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("הגדרות", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        BabyCard(profile, weights) { onOpen(SettingsPage.PROFILE) }
        MenuGroup("משפחה ומעקב", listOf(SettingsPage.FAMILY, SettingsPage.TRACKERS, SettingsPage.MEDS, SettingsPage.ALERTS), ::summary, emptySet(), onOpen)
        MenuGroup("כללי", listOf(SettingsPage.DISPLAY, SettingsPage.BACKUP, SettingsPage.ABOUT), ::summary,
            if (backupDue) setOf(SettingsPage.BACKUP) else emptySet(), onOpen)
    }
}

@Composable
private fun BabyCard(profile: BabyProfile?, weights: List<Weight>, onClick: () -> Unit) {
    val ageDays = profile?.birthEpochDay?.let { (LocalDate.now().toEpochDay() - it).toInt() }
    val grams = weights.firstOrNull()?.grams ?: profile?.birthWeightG?.takeIf { it > 0 }
    val line = listOfNotNull(ageDays?.let { "בן/בת ${Fmt.age(it)}" }, grams?.let { "%,d ג׳".format(it) })
        .joinToString(" · ").ifEmpty { "הזינו תאריך לידה ומשקל" }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.feed).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(R.drawable.ic_baby, Color.White, Palette.feedInk, size = 56.dp, iconSize = 30.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(profile?.name?.takeIf { it.isNotBlank() } ?: "התינוק שלנו", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.ink)
            Text(line, style = MaterialTheme.typography.bodyMedium, color = Palette.feedInk)
        }
        AppIcon(R.drawable.ic_chevron, size = 18.dp, tint = Palette.feedInk)
    }
}

@Composable
private fun MenuGroup(
    label: String,
    pages: List<SettingsPage>,
    summary: (SettingsPage) -> String,
    flagged: Set<SettingsPage>,
    onOpen: (SettingsPage) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface)) {
            pages.forEachIndexed { i, p ->
                if (i > 0) HorizontalDivider(color = Palette.line)
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(p) }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(p.icon, p.bg, p.ink)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        val sub = summary(p)
                        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (p in flagged) {
                        Text(
                            "לגבות",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Palette.medInk,
                            modifier = Modifier.clip(RoundedCornerShape(11.dp)).background(Palette.med).padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    AppIcon(R.drawable.ic_chevron, size = 18.dp, tint = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

/** Which trackers this phone shows. Hidden ones leave the home screen, widget, buttons and night screen. */
@Composable
private fun TrackersCard() {
    val context = LocalContext.current
    val hidden by Repo.prefs.hiddenTrackers.collectAsState()
    SettingsCard("מה עוקבים") {
        Tracker.entries.forEach { t ->
            val on = t !in hidden
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                val (bg, ink) = t.type?.let { Palette.light(it) to Palette.inkOf(it) } ?: (Palette.neutral to Palette.neutralInk)
                IconBadge(t.type?.let { AppIcons.of(it) } ?: R.drawable.ic_weight, bg, ink, size = 36.dp, iconSize = 19.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title)
                    if (!t.canHide) Muted("תמיד פעיל – השעון והחישובים מבוססים עליו")
                }
                Switch(
                    checked = on,
                    enabled = t.canHide,
                    onCheckedChange = {
                        Repo.prefs.setTrackerShown(t, it)
                        Repo.scope.launch { WidgetRefresher.refresh(context) }
                    },
                )
            }
        }
        Muted("ההגדרה הזו לטלפון הזה בלבד. מעקב מוסתר יורד מהבית, מהווידג'ט, מהכפתורים וממצב לילה. מה שכבר נרשם נשמר.")
    }
}

/** Up to six buttons for the night screen. */
@Composable
private fun NightButtonsCard() {
    val chosen by Repo.prefs.nightButtons.collectAsState()
    val hidden by Repo.prefs.hiddenTrackers.collectAsState()
    val options = NIGHT_CHOICES.filter { Tracker.of(it) !in hidden }
    val selected = chosen.filter { it in options }
    SettingsCard("כפתורים במצב לילה") {
        Muted("${selected.size} נבחרו · עד $NIGHT_MAX. פחות כפתורים = כפתורים גדולים יותר בחושך.")
        options.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { t ->
                    val on = t in selected
                    Column(
                        Modifier
                            .weight(1f)
                            .height(84.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (on) Palette.light(t) else Color.Transparent)
                            .border(2.dp, if (on) Palette.primary else Palette.line, RoundedCornerShape(20.dp))
                            .clickable {
                                val next = if (on) selected - t else selected + t
                                // At least one button must stay.
                                if (next.isNotEmpty()) Repo.prefs.setNightButtons(NIGHT_CHOICES.filter { it in next })
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        AppIcon(AppIcons.of(t), size = 26.dp, tint = if (on) Palette.inkOf(t) else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            Fmt.title(t),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            color = if (on) Palette.inkOf(t) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Muted("לחיצה ארוכה על כפתור במצב לילה רושמת \"כמו בפעם הקודמת\". בתרופה ובסיכום היא פותחת את החלון.")
    }
}

@Composable
private fun SettingsCard(title: String?, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            title?.let { Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            content()
        }
    }
}

@Composable
private fun UserCard() {
    val name by Repo.prefs.userName.collectAsState()
    LaunchedEffect(name) {
        delay(1500)
        FamilySync.updateMyName(name)
    }
    SettingsCard("מי משתמש בטלפון הזה") {
        OutlinedTextField(
            value = name,
            onValueChange = { Repo.prefs.setUserName(it) },
            label = { Text("השם שלך (יופיע ליד מה שתיעדת)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ProfileCard(profile: BabyProfile?, canEdit: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val key = profile?.updatedAt ?: 0L
    var name by remember(key) { mutableStateOf(profile?.name ?: "") }
    var birth by remember(key) { mutableStateOf(profile?.birthEpochDay?.let { LocalDate.ofEpochDay(it) }) }
    var weeks by remember(key) { mutableIntStateOf(profile?.gestWeeks ?: 40) }
    var days by remember(key) { mutableIntStateOf(profile?.gestDays ?: 0) }
    var birthWeight by remember(key) { mutableIntStateOf(profile?.birthWeightG ?: 0) }
    var saved by remember(key) { mutableStateOf(false) }

    SettingsCard("פרטי התינוק") {
        if (!canEdit) Muted("רק הורה יכול לשנות את פרטי התינוק.")
        OutlinedTextField(value = name, onValueChange = { name = it; saved = false }, label = { Text("שם") }, singleLine = true, enabled = canEdit, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("תאריך לידה:", modifier = Modifier.weight(1f))
            OutlinedButton(enabled = canEdit, onClick = { pickDay(context, birth) { birth = it; saved = false } }) {
                Text(birth?.let { Fmt.date(it) } ?: "בחירה")
            }
        }
        Stepper("שבוע לידה", weeks, { weeks = it; saved = false }, step = 1, unit = "שב׳", min = 22, max = 43)
        Stepper("ועוד ימים", days, { days = it; saved = false }, step = 1, unit = "ימים", min = 0, max = 6)
        Stepper("משקל לידה", birthWeight, { birthWeight = it; saved = false }, step = 50, unit = "גרם", max = 6000)
        if (canEdit) Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                scope.launch {
                    Repo.saveProfile(
                        BabyProfile(
                            name = name.trim(),
                            birthEpochDay = birth?.toEpochDay(),
                            gestWeeks = weeks,
                            gestDays = days,
                            birthWeightG = birthWeight,
                        ),
                    )
                    saved = true
                }
            }) { Text("שמירה") }
            Spacer(Modifier.width(12.dp))
            if (saved) Muted("נשמר ✓")
        }
    }
}

@Composable
private fun WeightsCard(weights: List<Weight>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var grams by remember { mutableIntStateOf(0) }
    var day by remember { mutableStateOf(LocalDate.now()) }

    SettingsCard("שקילות") {
        Muted("החישוב משתמש תמיד בשקילה האחרונה. אם אין – במשקל הלידה.")
        Stepper("משקל", grams, { grams = it }, step = 50, unit = "גרם", max = 15000)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { pickDay(context, day) { day = it } }) { Text(Fmt.dayLabel(day)) }
            Spacer(Modifier.width(8.dp))
            Button(enabled = grams > 0, onClick = {
                val w = Weight(time = day.startMillis() + 12 * 3_600_000L, grams = grams)
                scope.launch { Repo.saveWeight(w) }
                grams = 0
            }) { Text("הוספה") }
        }
        weights.forEach { w ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${Fmt.date(w.time.toLocalDate())}: ${w.grams} גרם", modifier = Modifier.weight(1f))
                TextButton(onClick = { scope.launch { Repo.deleteWeight(w) } }) { Text("מחיקה") }
            }
        }
    }
}

@Composable
private fun FamilyCard(isParent: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val familyId by Repo.prefs.familyId.collectAsState()
    val status by FamilySync.status.collectAsState()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }

    SettingsCard("שיתוף עם המשפחה") {
        if (!FamilySync.available) {
            Muted("הסנכרון עוד לא הופעל בגרסה הזו: חסר קובץ ההגדרות של Firebase (google-services.json). ההוראות ב-README של הריפו.")
            return@SettingsCard
        }
        val fid = familyId
        if (fid == null) {
            Muted("טלפון אחד יוצר משפחה ומקבל קוד. בטלפון השני מזינים את הקוד. מי שיוצר את המשפחה הוא הורה; מי שמצטרף מתחיל כמתעד/ת, והורה יכול לשנות.")
            Button(enabled = !busy, onClick = {
                busy = true; error = null
                scope.launch {
                    try { FamilySync.createFamily() } catch (e: Exception) { error = e.message ?: "שגיאה" }
                    busy = false
                }
            }) { Text("יצירת משפחה חדשה") }
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase() },
                label = { Text("קוד משפחה") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(enabled = !busy && code.length >= 8, onClick = {
                busy = true; error = null
                scope.launch {
                    try { FamilySync.joinFamily(code) } catch (e: Exception) { error = e.message ?: "שגיאה" }
                    busy = false
                }
            }) { Text("הצטרפות עם קוד") }
        } else {
            Text("קוד המשפחה:")
            SelectionContainer { Text(fid, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp) }
            Button(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "קוד המשפחה לאפליקציית מעקב התינוק: $fid")
                context.startActivity(Intent.createChooser(send, "שיתוף הקוד"))
            }) { Text("שיתוף הקוד") }
            if (status.isNotBlank()) Muted("מצב: $status")
            TextButton(onClick = { confirmLeave = true }) { Text("ניתוק מהמשפחה") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }

    if (familyId != null && FamilySync.available) MembersCard(isParent) { error = it }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("לנתק את הסנכרון?") },
            text = { Text("הנתונים נשארים בטלפון, אבל הם יפסיקו להתעדכן מול הטלפון השני. אפשר להצטרף שוב עם אותו קוד.") },
            confirmButton = { TextButton(onClick = { FamilySync.leave(); confirmLeave = false }) { Text("ניתוק") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("ביטול") } },
        )
    }
}

/** Everyone connected, with their role. Parents can change roles. */
@Composable
private fun MembersCard(isParent: Boolean, onError: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val members by FamilySync.members.collectAsState()
    val me = FamilySync.myUid
    SettingsCard("בני המשפחה") {
        if (members.isEmpty()) Muted("טוען…")
        members.forEach { m ->
            var menu by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconBadge(R.drawable.ic_family, if (m.role == Role.PARENT) Palette.bath else Palette.neutral,
                    if (m.role == Role.PARENT) Palette.bathInk else Palette.neutralInk, size = 36.dp, iconSize = 19.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.name.ifBlank { "ללא שם" } + if (m.uid == me) " · את/ה" else "", fontWeight = FontWeight.Medium)
                    Muted(m.role.title)
                }
                if (isParent) {
                    Box {
                        TextButton(onClick = { menu = true }) { Text("שינוי") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            Role.entries.forEach { r ->
                                DropdownMenuItem(
                                    text = { Column { Text(r.title, fontWeight = FontWeight.Medium); Muted(r.description) } },
                                    onClick = {
                                        menu = false
                                        scope.launch {
                                            try { FamilySync.setRole(m.uid, r) } catch (e: Exception) { onError(e.message ?: "שגיאה") }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = Palette.line)
        Role.entries.forEach { r -> Muted("${r.title}: ${r.description}") }
        if (!isParent) Muted("רק הורה יכול לשנות תפקידים.")
        Muted("השם שמופיע כאן הוא השם שכל אחד כתב בטלפון שלו, למעלה.")
    }
}

@Composable
private fun NightCard() {
    val auto by Repo.prefs.nightAuto.collectAsState()
    SettingsCard("מצב לילה") {
        Muted("את הכפתורים שמופיעים במצב לילה בוחרים ב״מעקבים ומצב לילה״.")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("אוטומטי בין 22:00 ל-06:00")
                Muted("מסך כהה עם כפתורי ענק. אפשר תמיד לעבור ידנית מכפתור הירח במסך השעון.")
            }
            Switch(checked = auto, onCheckedChange = { Repo.prefs.setNightAuto(it) })
        }
    }
}

@Composable
private fun BackupCard(canRestore: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }

    fun run(block: suspend () -> String) {
        scope.launch {
            message = try { block() } catch (e: Exception) { "שגיאה: ${e.message ?: "לא ידוע"}" }
        }
    }

    val saveJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { run { Backup.writeJson(context, it); Repo.prefs.markBackup(); "הגיבוי נשמר ✓" } }
    }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { run { Backup.writeCsv(context, it); "קובץ הטבלה נשמר ✓" } }
    }
    val openJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        confirmRestore = uri
    }

    SettingsCard("גיבוי וייצוא") {
        Muted("הקובץ נשמר איפה שתבחרו: בטלפון, ב-Google Drive או לשליחה. כדאי לשמור גיבוי מדי פעם.")
        Button(onClick = { saveJson.launch("baby-backup_${Backup.fileStamp()}.json") }, modifier = Modifier.fillMaxWidth()) {
            Text("שמירת גיבוי מלא (JSON)")
        }
        OutlinedButton(onClick = { saveCsv.launch("baby-log_${Backup.fileStamp()}.csv") }, modifier = Modifier.fillMaxWidth()) {
            Text("ייצוא לטבלה (Excel / Sheets)")
        }
        if (canRestore) {
            OutlinedButton(onClick = { openJson.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Text("שחזור מגיבוי")
            }
        } else {
            Muted("שחזור מגיבוי – רק להורה.")
        }
        message?.let { Text(it, fontWeight = FontWeight.Medium) }
    }

    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("לשחזר מהגיבוי?") },
            text = { Text("רשומות מהגיבוי יתווספו, ורשומה שקיימת בשני המקומות תתעדכן לגרסה האחרונה שנערכה. שום דבר לא נמחק. אם אתם מחוברים למשפחה – גם הטלפון השני יתעדכן.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = null
                    run {
                        val r = Backup.restoreJson(context, uri)
                        "שוחזר ✓ · ${r.events} רשומות, ${r.weights} שקילות" + if (r.profile) ", פרטי התינוק" else ""
                    }
                }) { Text("שחזור") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("ביטול") } },
        )
    }
}

@Composable
private fun AlertsSettingsCard() {
    val context = LocalContext.current
    val prefs = Repo.prefs
    val pattern by prefs.patternAlerts.collectAsState()
    val meds by prefs.medReminders.collectAsState()
    val diaper by prefs.diaperWarning.collectAsState()
    var granted by remember { mutableStateOf(Notifier.canNotify(context)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    fun ensurePermission() {
        if (!granted && android.os.Build.VERSION.SDK_INT >= 33) ask.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    @Composable
    fun ToggleRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title)
                Muted(desc)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }

    SettingsCard(null) {
        ToggleRow(
            "שינוי בדפוס",
            "התראה כשה-24 שעות האחרונות שונות מהשבוע הקודם שלו: מרווח ארוך מהאכלה, פחות האכלות, פחות מ״ל, פחות חיתולים רטובים, הרבה זמן בלי קקי. מתחיל אחרי 3 ימי תיעוד.",
            pattern,
        ) { prefs.setPatternAlerts(it); if (it) ensurePermission() }
        ToggleRow(
            "תזכורות לתרופות",
            "התראה כשעברה שעת מתן ועוד לא נרשם שהתרופה ניתנה. נבדק כל כ-15 דקות, אז התזכורת יכולה להגיע באיחור קטן.",
            meds,
        ) { prefs.setMedReminders(it); if (it) ensurePermission() }
        ToggleRow(
            "אזהרת חיתולים רטובים",
            "הודעה במסך הבית כשיש פחות מ-6 חיתולים רטובים ב-24 שעות. אפשר לכבות אם מחליפים רק כשהחיתול מלא.",
            diaper,
        ) { prefs.setDiaperWarning(it) }
        if (!granted && (pattern || meds)) {
            Text("ההתראות חסומות בטלפון.", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = {
                if (android.os.Build.VERSION.SDK_INT >= 33) ensurePermission()
                else context.startActivity(
                    Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            }) { Text("לאפשר התראות") }
        }
    }
}
