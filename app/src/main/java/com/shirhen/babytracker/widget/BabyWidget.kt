package com.shirhen.babytracker.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.shirhen.babytracker.data.Event
import com.shirhen.babytracker.data.EventType
import com.shirhen.babytracker.data.Repo
import com.shirhen.babytracker.data.lastBreastSide
import com.shirhen.babytracker.data.startMillis
import com.shirhen.babytracker.data.summarize
import com.shirhen.babytracker.guide.FeedingGuide
import com.shirhen.babytracker.ui.Fmt
import com.shirhen.babytracker.ui.MainActivity
import com.shirhen.babytracker.ui.QuickEntryActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

object WidgetRefresher {
    suspend fun refresh(context: Context) {
        runCatching { BabyWidget().updateAll(context) }
    }
}

class BabyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BabyWidget()
}

private data class WidgetData(
    val lastFeed: Event?,
    val lastDiaper: Event?,
    val lastPump: Event?,
    val nextSide: String?,
    val perFeed: String?,
    val todayMl: Int,
    val feedsToday: Int,
)

private val bg = ColorProvider(Color(0xFFFFF8F3))
private val ink = ColorProvider(Color(0xFF3B2F2A))
private val muted = ColorProvider(Color(0xFF7A6A62))
private val feedColor = ColorProvider(Color(0xFFF6DCC8))
private val diaperColor = ColorProvider(Color(0xFFDCE9F5))
private val pumpColor = ColorProvider(Color(0xFFE6DFF3))
private val bathColor = ColorProvider(Color(0xFFD9EEE1))

class BabyWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = withContext(Dispatchers.IO) { load() }
        provideContent { Content(context, data) }
    }

    private suspend fun load(): WidgetData {
        val now = System.currentTimeMillis()
        val dao = Repo.db.events()
        val recent = dao.since(now - 3 * 24 * 3_600_000L)
        val last24 = recent.filter { it.time >= now - 24 * 3_600_000L }
        val today = recent.filter { it.time >= LocalDate.now().startMillis() }
        val todaySummary = summarize(today)
        val guide = FeedingGuide.compute(
            Repo.db.profile().get(),
            Repo.db.weights().latest()?.grams,
            last24.count { it.type == EventType.FEED },
        )
        val perFeed = guide?.perFeedMinMl?.let { "${it}–${guide.perFeedMaxMl} מ״ל" }
        return WidgetData(
            lastFeed = dao.last(EventType.FEED),
            lastDiaper = dao.last(EventType.DIAPER),
            lastPump = dao.last(EventType.PUMP),
            nextSide = when (recent.lastBreastSide()) { "L" -> "ימין"; "R" -> "שמאל"; else -> null },
            perFeed = perFeed,
            todayMl = todaySummary.bottleMl,
            feedsToday = todaySummary.feeds,
        )
    }

    @Composable
    private fun Content(context: Context, d: WidgetData) {
        val big = TextStyle(color = ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        val small = TextStyle(color = muted, fontSize = 12.sp)
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(bg)
                .cornerRadius(20.dp)
                .padding(10.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            val feed = d.lastFeed
            Text(
                text = if (feed == null) "🍼 עוד לא תועדה האכלה" else "🍼 ${Fmt.time(feed.time)} · ${Fmt.ago(feed.time)}",
                style = big,
                maxLines = 1,
            )
            val feedLine = buildList {
                feed?.let { add(Fmt.describe(it)) }
                d.nextSide?.let { add("צד הבא: $it") }
            }.joinToString(" · ")
            if (feedLine.isNotEmpty()) Text(feedLine, style = small, maxLines = 1)
            val statusLine = buildList {
                d.lastDiaper?.let { add("🧷 ${Fmt.ago(it.time)}") }
                d.lastPump?.let { add("🫧 ${Fmt.ago(it.time)}") }
                add("היום: ${d.feedsToday} האכלות, ${d.todayMl} מ״ל")
            }.joinToString(" · ")
            Text(statusLine, style = small, maxLines = 1)
            d.perFeed?.let { Text("יעד משוער לארוחה: $it", style = small, maxLines = 1) }
            Spacer(GlanceModifier.height(6.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                QuickButton("🍼", "האכלה", feedColor, actionStartActivity(quickIntent(context, "FEED")), GlanceModifier.defaultWeight())
                QuickButton("🧷", "חיתול", diaperColor, actionStartActivity(quickIntent(context, "DIAPER")), GlanceModifier.defaultWeight())
                QuickButton("🫧", "שאיבה", pumpColor, actionStartActivity(quickIntent(context, "PUMP")), GlanceModifier.defaultWeight())
                QuickButton("🛁", "מקלחת", bathColor, actionRunCallback<LogBathAction>(), GlanceModifier.defaultWeight())
            }
        }
    }

    @Composable
    private fun QuickButton(emoji: String, label: String, color: ColorProvider, action: Action, modifier: GlanceModifier) {
        Box(modifier = modifier.padding(horizontal = 3.dp)) {
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(color)
                    .cornerRadius(14.dp)
                    .padding(vertical = 6.dp)
                    .clickable(action),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(emoji, style = TextStyle(fontSize = 20.sp))
                Text(label, style = TextStyle(color = ink, fontSize = 11.sp))
            }
        }
    }

    private fun quickIntent(context: Context, type: String) =
        Intent(context, QuickEntryActivity::class.java)
            .setData(Uri.parse("babytracker://quick/$type"))
            .putExtra(QuickEntryActivity.EXTRA_TYPE, type)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
}

/** Bath is logged straight from the widget, no dialog. */
class LogBathAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Repo.save(Event(type = EventType.BATH, time = System.currentTimeMillis()))
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "🛁 מקלחת נרשמה", Toast.LENGTH_SHORT).show()
        }
    }
}
