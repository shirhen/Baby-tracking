package com.shirhen.babytracker.widget

import android.view.View
import androidx.glance.ColorFilter
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.TextAlign
import com.shirhen.babytracker.data.Tracker

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
import com.shirhen.babytracker.data.nextBreastSide
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
    val hidden: Set<Tracker>,
)

/** One tile: which entry it opens, what it shows and its colours. */
private class TileSpec(
    val type: EventType,
    val icon: Int,
    val label: String,
    val last: Event?,
    val bg: ColorProvider,
    val ink: ColorProvider,
    val extra: String?,
)

// "Botanical garden" colours, as in the app.
private val bg = ColorProvider(Color(0xFFF6F6EE))
private val ink = ColorProvider(Color(0xFF1F2A22))
private val muted = ColorProvider(Color(0xFF566259))
private val feedColor = ColorProvider(Color(0xFFFCE5C7))
private val diaperColor = ColorProvider(Color(0xFFDCE9F5))
private val pumpColor = ColorProvider(Color(0xFFE6DDF0))
private val bathColor = ColorProvider(Color(0xFFDDEDE2))
private val medColor = ColorProvider(Color(0xFFF8DCDD))
private val poopColor = ColorProvider(Color(0xFFEFE3CF))
private val feedInk = ColorProvider(Color(0xFF7A4A0A))
private val diaperInk = ColorProvider(Color(0xFF1F4466))
private val pumpInk = ColorProvider(Color(0xFF43306E))
private val bathInk = ColorProvider(Color(0xFF276B4E))
private val medInk = ColorProvider(Color(0xFF7A2328))
private val poopInk = ColorProvider(Color(0xFF5C4316))

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
                statuses.firstOrNull { it.overdueSlot != null }?.let { "באיחור: ${it.med.name} ${Fmt.time(it.overdueSlot!!)}" }
                    ?: statuses.filter { it.nextDue != null }.minByOrNull { it.nextDue!! }
                        ?.let { "הבא: ${it.med.name} ${Fmt.time(it.nextDue!!)}" }
            },
            nextSide = when (recent.nextBreastSide()) { "R" -> "ימין"; "L" -> "שמאל"; else -> null },
            perFeed = guide?.perFeedMinMl?.let { "${it}\u200E–\u200E${guide.perFeedMaxMl}" },
            todayMl = todaySummary.bottleMl,
            feedsToday = todaySummary.feeds,
            hidden = Repo.prefs.hiddenTrackers.value,
        )
    }

    @Composable
    private fun Content(context: Context, d: WidgetData) {
        // The app is Hebrew, so the widget is always right to left, even on a phone set to English.
        // Launchers lay out widgets by the phone's language, so on a left-to-right phone the order is flipped here.
        val rtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        fun shown(t: Tracker) = t !in d.hidden
        val tiles = buildList {
            add(TileSpec(EventType.FEED, R.drawable.ic_feed, "האכלה", d.lastFeed, feedColor, feedInk, d.lastFeed?.let { lastFeedLine(it) }))
            if (shown(Tracker.DIAPER)) add(TileSpec(EventType.DIAPER, R.drawable.ic_diaper, "חיתול", d.lastDiaper, diaperColor, diaperInk, d.lastDiaper?.let { Fmt.describe(it) }))
            if (shown(Tracker.PUMP)) add(TileSpec(EventType.PUMP, R.drawable.ic_pump, "שאיבה", d.lastPump, pumpColor, pumpInk, d.lastPump?.let { Fmt.describe(it) }))
            if (shown(Tracker.DIAPER)) add(TileSpec(EventType.DIAPER, R.drawable.ic_poop, "קקי אחרון", d.lastPoop, poopColor, poopInk, null))
            if (shown(Tracker.MED)) add(TileSpec(EventType.MED, R.drawable.ic_med, "תרופה", d.lastMed, medColor, medInk,
                listOfNotNull(d.lastMed?.medName, d.medLine).joinToString(" · ")))
            if (shown(Tracker.BATH)) add(TileSpec(EventType.BATH, R.drawable.ic_bath, "מקלחת", d.lastBath, bathColor, bathInk, null))
        }
        val rows = tiles.chunked(2)

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(bg)
                .cornerRadius(20.dp)
                .padding(8.dp),
        ) {
            // Rows of tiles below a one-line header; each tile has 3dp padding around it.
            val size = LocalSize.current
            val scale = scaleFor((size.height.value - 16f - 20f) / rows.size.coerceAtLeast(1) - 6f)
            val header = buildList {
                add("היום: ${d.feedsToday} האכלות · ${d.todayMl} מ״ל")
                d.perFeed?.let { add("יעד $it מ״ל") }
            }.joinToString(" · ")
            Text(
                header,
                style = TextStyle(color = muted, fontSize = 11.sp, textAlign = TextAlign.Right),
                maxLines = 1,
                modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)
                    .clickable(actionStartActivity<MainActivity>()),
            )
            // Right to left: feed | diaper, pump | poop, medication | bath (hidden trackers are skipped).
            rows.forEach { row ->
                Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    (if (rtl) row else row.reversed()).forEach { t ->
                        Tile(context, t, scale, rtl, GlanceModifier.defaultWeight().fillMaxHeight())
                    }
                }
            }
        }
    }

    @Composable
    private fun Tile(context: Context, t: TileSpec, scale: TileScale, rtl: Boolean, modifier: GlanceModifier) {
        val last = t.last
        Box(modifier = modifier.padding(3.dp)) {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(t.bg)
                    .cornerRadius(16.dp)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .clickable(quickAction(context, t.type)),
                verticalAlignment = Alignment.CenterVertically,
                // Start is the right edge only when the phone itself is right to left.
                horizontalAlignment = if (rtl) Alignment.Start else Alignment.End,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val iconSize = (scale.titleSp + 3).dp
                    val icon = @Composable {
                        Image(
                            provider = ImageProvider(t.icon),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(t.ink),
                            modifier = GlanceModifier.size(iconSize),
                        )
                    }
                    val label = @Composable {
                        Text(t.label, style = TextStyle(color = ink, fontSize = scale.titleSp.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                    }
                    // Icon on the right of the title.
                    if (rtl) { icon(); Spacer(GlanceModifier.width(4.dp)); label() } else { label(); Spacer(GlanceModifier.width(4.dp)); icon() }
                }
                val big = TextStyle(color = ink, fontSize = scale.numberSp.sp, fontWeight = FontWeight.Bold)
                when {
                    last == null -> Text("—", style = big, maxLines = 1)
                    // Over a day a ticking 57:45:01 is hard to read: show days and hours instead.
                    System.currentTimeMillis() - last.time >= DAY_MS -> Text(longSince(last.time), style = big, maxLines = 1)
                    else -> AndroidRemoteViews(chronometer(context, last.time, scale.numberSp))
                }
                val sub = listOfNotNull(last?.let { Fmt.time(it.time) }, t.extra?.takeIf { it.isNotBlank() }).joinToString(" · ")
                if (sub.isNotEmpty() && scale.showSub) {
                    Spacer(GlanceModifier.height(1.dp))
                    Text(sub, style = TextStyle(color = muted, fontSize = 11.sp, textAlign = TextAlign.Right), maxLines = 1)
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
            Toast.makeText(context, "מקלחת נרשמה", Toast.LENGTH_SHORT).show()
        }
    }
}
