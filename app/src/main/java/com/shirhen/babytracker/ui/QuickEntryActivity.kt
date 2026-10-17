package com.shirhen.babytracker.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.shirhen.babytracker.data.Event
import com.shirhen.babytracker.data.EventType
import com.shirhen.babytracker.data.Repo
import com.shirhen.babytracker.data.lastBreastSide
import com.shirhen.babytracker.data.nextSideOf
import com.shirhen.babytracker.guide.FeedingGuide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Dialog-only screen opened from the widget buttons. */
class QuickEntryActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TYPE = "type"
    }

    private data class Ctx(val history: List<Event>, val hint: String?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val type = intent.getStringExtra(EXTRA_TYPE)
            ?.let { runCatching { EventType.valueOf(it) }.getOrNull() }
            ?: EventType.FEED

        setContent {
            BabyTheme(dark = Repo.prefs.nightAuto.value && isNightHour()) {
                var ctx by remember { mutableStateOf<Ctx?>(null) }
                LaunchedEffect(Unit) {
                    ctx = withContext(Dispatchers.IO) {
                        val now = System.currentTimeMillis()
                        val history = Repo.db.events().since(now - 7 * DAY_MS)
                        val guide = FeedingGuide.compute(
                            Repo.db.profile().get(),
                            Repo.db.weights().latest()?.grams,
                            history.count { it.type == EventType.FEED && it.time >= now - DAY_MS },
                        )
                        Ctx(history, guide?.perFeedMinMl?.let { "יעד משוער לארוחה: $it–${guide.perFeedMaxMl} מ״ל" })
                    }
                }
                ctx?.let { c ->
                    EventEditorDialog(
                        initial = newEvent(type, c.history),
                        isNew = true,
                        suggestedSide = nextSideOf(c.history.lastBreastSide()),
                        guideHint = c.hint,
                        onDismiss = { finish() },
                        onSave = { e ->
                            // Save on the app scope so it completes even after this screen closes.
                            Repo.scope.launch { Repo.save(e) }
                            finish()
                        },
                        onDelete = null,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }
}
