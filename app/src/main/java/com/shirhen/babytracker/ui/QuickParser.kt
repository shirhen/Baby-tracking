package com.shirhen.babytracker.ui

import com.shirhen.babytracker.data.Event
import com.shirhen.babytracker.data.EventType
import com.shirhen.babytracker.data.FeedKind
import java.time.Instant
import java.time.ZoneId

/**
 * Turns short free text from the journal into an entry, e.g.
 *   "תמל 90"            -> formula 90 ml
 *   "שאוב 60 תמל 30"    -> mixed bottle
 *   "ימין 10 שמאל 5"    -> breastfeed by side
 *   "הנקה 20"           -> breastfeed, 20 min total
 *   "רטוב" / "קקי"       -> diaper
 *   "שאיבה 120"         -> pump
 *   "14:30 מקלחת"       -> bath at 14:30 (a future time means yesterday)
 * Returns null when nothing is recognised.
 */
object QuickParser {
    private val timeRe = Regex("""(\d{1,2})[:.](\d{2})""")
    private val numRe = Regex("""\d{1,4}""")

    fun parse(input: String, now: Long = System.currentTimeMillis()): Event? {
        // Drop gershayim/geresh so "תמ״ל", 'תמ"ל' and "תמל" all match.
        var s = input.trim().replace(Regex("[\"״׳']"), "")
        if (s.isEmpty()) return null

        var time = now
        timeRe.find(s)?.let { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            if (h in 0..23 && min in 0..59) {
                val zone = ZoneId.systemDefault()
                var dt = Instant.ofEpochMilli(now).atZone(zone).withHour(h).withMinute(min).withSecond(0).withNano(0)
                if (dt.toInstant().toEpochMilli() > now + 5 * 60_000) dt = dt.minusDays(1)
                time = dt.toInstant().toEpochMilli()
                s = s.removeRange(m.range)
            }
        }

        fun has(vararg words: String) = words.any { s.contains(it) }
        val firstNum = numRe.find(s)?.value?.toInt() ?: 0

        /** The number written right after or right before [word], e.g. "ימין 10" or "10 ימין". */
        fun numNear(vararg words: String): Int? {
            for (w in words) {
                Regex("""$w\s*(\d{1,4})""").find(s)?.let { return it.groupValues[1].toInt() }
                Regex("""(\d{1,4})\s*(?:מל|ד|דק|דקות)?\s*$w""").find(s)?.let { return it.groupValues[1].toInt() }
            }
            return null
        }

        return when {
            has("מקלחת", "אמבטיה", "רחצה") -> Event(type = EventType.BATH, time = time)

            has("רטוב", "פיפי", "קקי", "מלוכלך", "חיתול") -> {
                val dirty = has("קקי", "מלוכלך")
                val wet = has("רטוב", "פיפי") || !dirty
                Event(type = EventType.DIAPER, time = time, wet = wet, dirty = dirty)
            }

            has("שאיבה", "שאבתי", "שאבנו", "שאבה") -> {
                val l = numNear("שמאל") ?: 0
                val r = numNear("ימין") ?: 0
                val total = if (l + r > 0) null else firstNum
                Event(
                    type = EventType.PUMP, time = time,
                    pumpLeftMl = if (total != null) total else l,
                    pumpRightMl = if (total != null) 0 else r,
                    durationMin = numNear("דקות", "דק") ?: 0,
                )
            }

            has("תמל", "פורמולה", "מטרנה", "סימילאק", "שאוב") -> {
                val formulaWord = has("תמל", "פורמולה", "מטרנה", "סימילאק")
                val expressedWord = has("שאוב")
                // With one kind, a lone number belongs to it; with both, each needs its own number.
                val f = when {
                    !formulaWord -> 0
                    else -> numNear("תמל", "פורמולה", "מטרנה", "סימילאק") ?: (if (expressedWord) 0 else firstNum)
                }
                val x = when {
                    !expressedWord -> 0
                    else -> numNear("שאוב") ?: (if (formulaWord) 0 else firstNum)
                }
                val kind = when {
                    formulaWord && expressedWord -> FeedKind.MIXED
                    formulaWord -> FeedKind.FORMULA
                    else -> FeedKind.EXPRESSED
                }
                Event(type = EventType.FEED, time = time, feedKind = kind, formulaMl = f, expressedMl = x)
            }

            has("הנקה", "ינק", "יניקה", "ציצי", "ימין", "שמאל") -> {
                val l = numNear("שמאל") ?: 0
                val r = numNear("ימין") ?: 0
                val iL = s.lastIndexOf("שמאל")
                val iR = s.lastIndexOf("ימין")
                // The side mentioned last is where the feed ended.
                val lastSide = when {
                    iL < 0 && iR < 0 -> null
                    iL > iR -> "L"
                    else -> "R"
                }
                Event(
                    type = EventType.FEED, time = time, feedKind = FeedKind.BREAST,
                    leftMin = l, rightMin = r,
                    breastTotalMin = if (l + r == 0) firstNum else 0,
                    lastSide = lastSide,
                )
            }

            else -> null
        }
    }

    const val EXAMPLES = "תמל 90 · שאוב 60 · ימין 10 שמאל 5 · הנקה 20 · רטוב · קקי · שאיבה 120 · 14:30 מקלחת"
}
