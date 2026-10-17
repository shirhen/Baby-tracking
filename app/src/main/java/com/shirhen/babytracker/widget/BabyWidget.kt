package com.shirhen.babytracker.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.widget.RemoteViews
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
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
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
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.shirhen.babytracker.R
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
    val lastBath: Event?,
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

/**
 * Four tiles, like the cards in the app. Each tile shows a live count-up timer since the last
 * entry (a native Chronometer, so it ticks without waking the app) and opens quick entry on tap.
 */
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
        val todaySummary = summarize(recent.filter { it.time >= LocalDate.now().startMillis() })
        val guide = FeedingGuide.compute(
            Repo.db.profile().get(),
            Repo.db.weights().latest()?.grams,
            last24.count { it.type == EventType.FEED },
        )
        return WidgetData(
            lastFeed = dao.last(EventType.FEED),
            lastDiaper = dao.last(EventType.DIAPER),
            lastPump = dao.last(EventType.PUMP),
            lastBath = dao.last(EventType.BATH),
            nextSide = when (recent.lastBreastSide()) { "L" -> "ימין"; "R" -> "שמאל"; else -> null },
            perFeed = guide?.perFeedMinMl?.let { "${it}–${guide.perFeedMaxMl}" },
            todayMl = todaySummary.bottleMl,
            feedsToday = todaySummary.feeds,
        )
    }

    @Composable
    private fun Content(context: Context, d: WidgetData) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(bg)
                .cornerRadius(20.dp)
                .padding(8.dp),
        ) {
            val header = buildList {
                add("היום: ${d.feedsToday} האכלות · ${d.todayMl} מ״ל")
                d.perFeed?.let { add("יעד $it מ״ל") }
            }.joinToString(" · ")
            Text(
                header,
                style = TextStyle(color = muted, fontSize = 11.sp),
                maxLines = 1,
                modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
                    .clickable(actionStartActivity<MainActivity>()),
            )
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                Tile(context, EventType.FEED, d.lastFeed, feedColor,
                    extra = listOfNotNull(d.lastFeed?.let { Fmt.describe(it) }, d.nextSide?.let { "הבא: $it" }).joinToString(" · "),
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight())
                Tile(context, EventType.DIAPER, d.lastDiaper, diaperColor,
                    extra = d.lastDiaper?.let { Fmt.describe(it) },
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight())
            }
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                Tile(context, EventType.PUMP, d.lastPump, pumpColor,
                    extra = d.lastPump?.let { Fmt.describe(it) },
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight())
                Tile(context, EventType.BATH, d.lastBath, bathColor,
                    extra = null,
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight())
            }
        }
    }

    @Composable
    private fun Tile(context: Context, type: EventType, last: Event?, color: ColorProvider, extra: String?, modifier: GlanceModifier) {
        Box(modifier = modifier.padding(3.dp)) {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(color)
                    .cornerRadius(16.dp)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .clickable(quickAction(context, type)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${Fmt.emoji(type)} ${Fmt.title(type)}  ＋",
                    style = TextStyle(color = ink, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
                if (last == null) {
                    Text("—", style = TextStyle(color = ink, fontSize = 22.sp, fontWeight = FontWeight.Bold))
                } else {
                    AndroidRemoteViews(chronometer(context, last.time))
                }
                val sub = listOfNotNull(last?.let { "מ-${Fmt.time(it.time)}" }, extra?.takeIf { it.isNotBlank() }).joinToString(" · ")
                if (sub.isNotEmpty()) {
                    Spacer(GlanceModifier.height(1.dp))
                    Text(sub, style = TextStyle(color = muted, fontSize = 11.sp), maxLines = 1)
                }
            }
        }
    }

    /** Native count-up timer: the launcher redraws it every second on its own. */
    private fun chronometer(context: Context, since: Long): RemoteViews {
        val elapsed = (System.currentTimeMillis() - since).coerceAtLeast(0)
        return RemoteViews(context.packageName, R.layout.widget_chronometer).apply {
            setChronometer(R.id.chronometer, SystemClock.elapsedRealtime() - elapsed, null, true)
        }
    }

    private fun quickAction(context: Context, type: EventType): Action =
        actionStartActivity(
            Intent(context, QuickEntryActivity::class.java)
                .setData(Uri.parse("babytracker://quick/${type.name}"))
                .putExtra(QuickEntryActivity.EXTRA_TYPE, type.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
}

/** Kept so widgets placed with an older version still have a valid action. */
class LogBathAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Repo.save(Event(type = EventType.BATH, time = System.currentTimeMillis()))
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "🛁 מקלחת נרשמה", Toast.LENGTH_SHORT).show()
        }
    }
}
