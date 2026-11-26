package com.shirhen.babytracker.widget

import android.content.Context
import com.shirhen.babytracker.ui.DAY_MS
import androidx.glance.appwidget.SizeMode
import androidx.glance.LocalSize
import android.util.TypedValue
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
import com.shirhen.babytracker.data.usedRight
import com.shirhen.babytracker.data.usedLeft
import com.shirhen.babytracker.data.bottleMl
import com.shirhen.babytracker.data.FeedKind
import com.shirhen.babytracker.data.MedSchedule
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
    val lastMed: Event?,
    val lastPoop: Event?,
    /** e.g. "הבא: אקמול 20:00" or "⚠️ ויטמין D 08:00". */
    val medLine: String?,
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
private val medColor = ColorProvider(Color(0xFFFBEAF0))
private val poopColor = ColorProvider(Color(0xFFFAEEDA))

/**
 * Six tiles, like the cards in the app. Each tile shows a live count-up timer since the last
 * entry (a native Chronometer, so it ticks without waking the app) and opens quick entry on tap.
 */
/** Text sizes for one tile, chosen from how tall the tiles actually are on this home screen. */
private data class TileScale(val numberSp: Float, val titleSp: Float, val showSub: Boolean)

private fun scaleFor(tileHeightDp: Float): TileScale = when {
    tileHeightDp >= 96 -> TileScale(26f, 13f, true)
    tileHeightDp >= 78 -> TileScale(22f, 12f, true)
    tileHeightDp >= 62 -> TileScale(19f, 12f, false)
    else -> TileScale(15f, 11f, false)
}

class BabyWidget : GlanceAppWidget() {

    // Exact size, so the layout can shrink text instead of cutting it off.
    override val sizeMode = SizeMode.Exact

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
            lastMed = dao.last(EventType.MED),
            lastPoop = recent.firstOrNull { it.type == EventType.DIAPER && it.dirty }
                ?: dao.since(0).firstOrNull { it.type == EventType.DIAPER && it.dirty },
            medLine = run {
                val statuses = Repo.db.meds().active().map { MedSchedule.status(it, recent, now) }
                statuses.firstOrNull { it.overdueSlot != null }?.let { "⚠️ ${it.med.name} ${Fmt.time(it.overdueSlot!!)}" }
                    ?: statuses.filter { it.nextDue != null }.minByOrNull { it.nextDue!! }
                        ?.let { "הבא: ${it.med.name} ${Fmt.time(it.nextDue!!)}" }
            },
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
            // 3 rows of tiles below a one-line header; each tile has 3dp padding around it.
            val size = LocalSize.current
            val scale = scaleFor((size.height.value - 16f - 20f) / 3f - 6f)
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
            // Right to left: feed | diaper, pump | poop, medication | bath.
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                Tile(context, EventType.FEED, d.lastFeed, feedColor,
                    extra = d.lastFeed?.let { lastFeedLine(it) },
                    scale = scale,
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight())
                Tile(context, EventType.DIAPER, d.lastDiaper, diaperColor,
                    extra = d.lastDiaper?.let { Fmt.describe(it) },
                    scale = scale,
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight())
            }
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                Tile(context, EventType.PUMP, d.lastPump, pumpColor,
                    extra = d.lastPump?.let { Fmt.describe(it) },
                    scale = scale,
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight())
                Tile(context, EventType.DIAPER, d.lastPoop, poopColor,
                    extra = null,
                    scale = scale,
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                    label = "💩 קקי אחרון")
            }
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                Tile(context, EventType.MED, d.lastMed, medColor,
                    extra = listOfNotNull(d.lastMed?.medName, d.medLine).joinToString(" · "),
                    scale = scale,
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight())
                Tile(context, EventType.BATH, d.lastBath, bathColor,
                    extra = null,
                    scale = scale,
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight())
            }
        }
    }

    @Composable
    private fun Tile(
        context: Context,
        type: EventType,
        last: Event?,
        color: ColorProvider,
        extra: String?,
        scale: TileScale,
        modifier: GlanceModifier,
        label: String = "${Fmt.emoji(type)} ${Fmt.title(type)}",
    ) {
        Box(modifier = modifier.padding(3.dp)) {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(color)
                    .cornerRadius(16.dp)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .clickable(quickAction(context, type)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    style = TextStyle(color = ink, fontSize = scale.titleSp.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
                val big = TextStyle(color = ink, fontSize = scale.numberSp.sp, fontWeight = FontWeight.Bold)
                when {
                    last == null -> Text("—", style = big, maxLines = 1)
                    // Over a day a ticking 57:45:01 is hard to read: show days and hours instead.
                    System.currentTimeMillis() - last.time >= DAY_MS -> Text(longSince(last.time), style = big, maxLines = 1)
                    else -> AndroidRemoteViews(chronometer(context, last.time, scale.numberSp))
                }
                val sub = listOfNotNull(last?.let { "מ-${Fmt.time(it.time)}" }, extra?.takeIf { it.isNotBlank() }).joinToString(" · ")
                if (sub.isNotEmpty() && scale.showSub) {
                    Spacer(GlanceModifier.height(1.dp))
                    Text(sub, style = TextStyle(color = muted, fontSize = 11.sp), maxLines = 1)
                }
            }
        }
    }

    /** Native count-up timer: the launcher redraws it every second on its own. */
    private fun chronometer(context: Context, since: Long, textSp: Float): RemoteViews {
        val elapsed = (System.currentTimeMillis() - since).coerceAtLeast(0)
        return RemoteViews(context.packageName, R.layout.widget_chronometer).apply {
            setChronometer(R.id.chronometer, SystemClock.elapsedRealtime() - elapsed, null, true)
            setTextViewTextSize(R.id.chronometer, TypedValue.COMPLEX_UNIT_SP, textSp)
        }
    }

    /** Last feed in a few words: "הנקה (ימין)", "תמ״ל 90 מ״ל", "שילוב (שני הצדדים) · 60 מ״ל". */
    private fun lastFeedLine(e: Event): String {
        val sides = when {
            e.usedLeft && e.usedRight -> "שני הצדדים"
            e.usedRight -> "ימין"
            e.usedLeft -> "שמאל"
            else -> null
        }
        val ml = e.bottleMl.takeIf { it > 0 }?.let { "$it מ״ל" }
        return when (e.feedKind) {
            FeedKind.BREAST, null -> "הנקה" + (sides?.let { " ($it)" } ?: "")
            FeedKind.FORMULA -> listOfNotNull("תמ״ל", ml).joinToString(" ")
            FeedKind.EXPRESSED -> listOfNotNull("שאוב", ml).joinToString(" ")
            FeedKind.MIXED -> "שילוב" + (sides?.let { " ($it)" } ?: "") + (ml?.let { " · $it" } ?: "")
        }
    }

    /** "יום ו-9 ש׳", "3 ימים". */
    private fun longSince(since: Long): String {
        val hours = ((System.currentTimeMillis() - since) / 3_600_000L).toInt()
        val days = hours / 24
        val h = hours % 24
        val d = if (days == 1) "יום" else "$days ימים"
        return if (h > 0 && days < 3) "$d ו-$h ש׳" else d
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
