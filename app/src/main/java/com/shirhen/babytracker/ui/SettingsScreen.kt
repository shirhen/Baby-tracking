package com.shirhen.babytracker.ui

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

@Composable
fun SettingsScreen(profile: BabyProfile?, weights: List<Weight>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("הגדרות", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        UserCard()
        NightCard()
        ProfileCard(profile)
        SettingsCard("שקילות") { Muted("הוספה ומעקב משקל: בלשונית נתונים ← משקל.") }
        FamilyCard()
        BackupCard()
        SettingsCard("אודות") {
            Muted("גרסה ${BuildConfig.VERSION_NAME}")
            Muted(FeedingGuide.DISCLAIMER)
            Muted(FeedingGuide.SOURCES)
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun UserCard() {
    val name by Repo.prefs.userName.collectAsState()
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
private fun ProfileCard(profile: BabyProfile?) {
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
        OutlinedTextField(value = name, onValueChange = { name = it; saved = false }, label = { Text("שם") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("תאריך לידה:", modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { pickDay(context, birth) { birth = it; saved = false } }) {
                Text(birth?.let { Fmt.date(it) } ?: "בחירה")
            }
        }
        Stepper("שבוע לידה", weeks, { weeks = it; saved = false }, step = 1, unit = "שב׳", min = 22, max = 43)
        Stepper("ועוד ימים", days, { days = it; saved = false }, step = 1, unit = "ימים", min = 0, max = 6)
        Stepper("משקל לידה", birthWeight, { birthWeight = it; saved = false }, step = 50, unit = "גרם", max = 6000)
        Row(verticalAlignment = Alignment.CenterVertically) {
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
private fun FamilyCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val familyId by Repo.prefs.familyId.collectAsState()
    val status by FamilySync.status.collectAsState()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }

    SettingsCard("שיתוף עם בן/בת הזוג") {
        if (!FamilySync.available) {
            Muted("הסנכרון עוד לא הופעל בגרסה הזו: חסר קובץ ההגדרות של Firebase (google-services.json). ההוראות ב-README של הריפו.")
            return@SettingsCard
        }
        val fid = familyId
        if (fid == null) {
            Muted("טלפון אחד יוצר משפחה ומקבל קוד. בטלפון השני מזינים את הקוד.")
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

@Composable
private fun NightCard() {
    val auto by Repo.prefs.nightAuto.collectAsState()
    SettingsCard("מצב לילה") {
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
private fun BackupCard() {
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
        uri?.let { run { Backup.writeJson(context, it); "הגיבוי נשמר ✓" } }
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
        OutlinedButton(onClick = { openJson.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
            Text("שחזור מגיבוי")
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
