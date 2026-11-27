package com.shirhen.babytracker.data

import com.shirhen.babytracker.ui.Fmt

/**
 * Notices about changes from the baby's own usual pattern, plus medication reminders.
 * The baseline is the 7 days before the last 24 hours, so a change is measured against
 * this baby, not against a general norm. Nothing is reported until there are a few days of data.
 */
data class PatternAlert(
    /** Stable key: the same situation produces the same key, so it is notified only once. */
    val key: String,
    val title: String,
    val text: String,
    val isMedReminder: Boolean = false,
)

object Alerts {
    private const val HOUR = 3_600_000L
    private const val DAY = 24 * HOUR
    private const val MIN_BASELINE_DAYS = 3

    private fun percentile(values: List<Long>, p: Double): Long {
        val s = values.sorted()
        return s[((s.size - 1) * p).toInt().coerceIn(0, s.size - 1)]
    }

    private fun gaps(times: List<Long>) = times.sorted().zipWithNext { a, b -> b - a }

    private fun hm(ms: Long): String {
        val min = (ms / 60_000).toInt()
        return if (min < 60) "$min דקות" else "${min / 60}:${"%02d".format(min % 60)} שעות"
    }

    fun pattern(events: List<Event>, now: Long = System.currentTimeMillis()): List<PatternAlert> {
        val first = events.minOfOrNull { it.time } ?: return emptyList()
        val recentFrom = now - DAY
        val baseFrom = maxOf(first, now - 8 * DAY)
        val baseDays = (recentFrom - baseFrom) / DAY.toDouble()
        if (baseDays < MIN_BASELINE_DAYS) return emptyList()

        val base = events.filter { it.time in baseFrom until recentFrom }
        val recent = events.filter { it.time in recentFrom..now }
        val today = Fmt.date(now.toLocalDate())
        val out = mutableListOf<PatternAlert>()

        // 1. A long time since the last feed, compared with this baby's usual longest gaps.
        val feeds = events.filter { it.type == EventType.FEED }
        val baseFeedGaps = gaps(base.filter { it.type == EventType.FEED }.map { it.time })
        val lastFeed = feeds.maxByOrNull { it.time }
        if (lastFeed != null && baseFeedGaps.size >= 15) {
            val p90 = percentile(baseFeedGaps, 0.9)
            val limit = maxOf(p90 + p90 / 4, p90 + 45 * 60_000L)
            val since = now - lastFeed.time
            if (since > limit) {
                out += PatternAlert(
                    "feedgap:${lastFeed.id}",
                    "עבר זמן רב מההאכלה האחרונה",
                    "עברו ${hm(since)} מההאכלה האחרונה (${Fmt.time(lastFeed.time)}). בשבוע האחרון המרווח הארוך הרגיל היה עד כ-${hm(p90)}.",
                )
            }
        }

        // 2. Fewer feeds than usual in the last 24 hours.
        val baseFeedsPerDay = base.count { it.type == EventType.FEED } / baseDays
        val recentFeeds = recent.count { it.type == EventType.FEED }
        if (baseFeedsPerDay >= 5 && recentFeeds < baseFeedsPerDay * 0.65) {
            out += PatternAlert(
                "feeds:$today",
                "פחות האכלות מהרגיל",
                "ב-24 השעות האחרונות תועדו $recentFeeds האכלות, לעומת כ-${"%.0f".format(baseFeedsPerDay)} ביום בשבוע האחרון.",
            )
        }

        // 3. Less milk from bottles than usual (only when bottles are a regular part of feeding).
        val baseMlPerDay = base.filter { it.type == EventType.FEED }.sumOf { it.bottleMl } / baseDays
        val recentMl = recent.filter { it.type == EventType.FEED }.sumOf { it.bottleMl }
        if (baseMlPerDay >= 200 && recentMl < baseMlPerDay * 0.7) {
            out += PatternAlert(
                "ml:$today",
                "פחות מ״ל מהרגיל",
                "ב-24 השעות האחרונות $recentMl מ״ל בבקבוק, לעומת כ-${"%.0f".format(baseMlPerDay)} מ״ל ביום בשבוע האחרון.",
            )
        }

        // 4. Fewer wet diapers than usual.
        val baseWetPerDay = base.count { it.type == EventType.DIAPER && it.wet } / baseDays
        val recentWet = recent.count { it.type == EventType.DIAPER && it.wet }
        if (baseWetPerDay >= 4 && recentWet < baseWetPerDay * 0.6) {
            out += PatternAlert(
                "wet:$today",
                "פחות חיתולים רטובים מהרגיל",
                "ב-24 השעות האחרונות $recentWet חיתולים רטובים, לעומת כ-${"%.0f".format(baseWetPerDay)} ביום בשבוע האחרון.",
            )
        }

        // 5. Longer than usual without a dirty diaper. Poop rhythm varies a lot, so the bar is high.
        val dirty = events.filter { it.type == EventType.DIAPER && it.dirty }
        val dirtyGaps = gaps(dirty.filter { it.time >= now - 15 * DAY }.map { it.time })
        val lastDirty = dirty.maxByOrNull { it.time }
        if (lastDirty != null && dirtyGaps.size >= 5) {
            val limit = maxOf(percentile(dirtyGaps, 0.9) * 3 / 2, DAY)
            val since = now - lastDirty.time
            if (since > limit) {
                out += PatternAlert(
                    "poop:${lastDirty.id}",
                    "עבר זמן רב מהקקי האחרון",
                    "הקקי האחרון היה לפני ${Fmt.since(lastDirty.time, now)}. זה ארוך מהמרווחים הרגילים שלו בשבועיים האחרונים.",
                )
            }
        }
        return out
    }

    fun medReminders(meds: List<Medication>, events: List<Event>, now: Long = System.currentTimeMillis()): List<PatternAlert> =
        meds.filter { it.active && !it.deleted }.mapNotNull { m ->
            val slot = MedSchedule.status(m, events, now).overdueSlot ?: return@mapNotNull null
            PatternAlert(
                "med:${m.id}:$slot",
                "הגיע הזמן ל${m.name}",
                "השעה ${Fmt.time(slot)} עברה ועוד לא נרשם מתן" + if (m.dose.isNotBlank()) " (${m.dose})." else ".",
                isMedReminder = true,
            )
        }

    const val NOTE = "ההשוואה היא לדפוס של התינוק עצמו בשבוע האחרון, ולא אבחנה רפואית. בכל דאגה – רופא/ת ילדים או טיפת חלב."
}
