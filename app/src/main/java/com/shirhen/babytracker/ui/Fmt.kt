package com.shirhen.babytracker.ui

import com.shirhen.babytracker.data.Event
import com.shirhen.babytracker.data.EventType
import com.shirhen.babytracker.data.FeedKind
import com.shirhen.babytracker.data.pumpedMl
import com.shirhen.babytracker.data.toLocalDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Fmt {
    private val hm = DateTimeFormatter.ofPattern("HH:mm")
    private val dm = DateTimeFormatter.ofPattern("d.M")
    private val dmy = DateTimeFormatter.ofPattern("d.M.yyyy")
    private val dayNames = listOf("ב׳", "ג׳", "ד׳", "ה׳", "ו׳", "שבת", "א׳") // Monday..Sunday

    fun time(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(hm)

    fun date(day: LocalDate): String = day.format(dmy)

    fun ago(ms: Long, now: Long = System.currentTimeMillis()): String {
        val min = ((now - ms) / 60_000).toInt()
        return when {
            min < 1 -> "עכשיו"
            min < 60 -> "לפני $min ד׳"
            min < 48 * 60 -> "לפני ${min / 60}:${"%02d".format(min % 60)}"
            else -> "לפני ${min / (60 * 24)} ימים"
        }
    }

    fun dayLabel(day: LocalDate, today: LocalDate = LocalDate.now()): String = when (day) {
        today -> "היום"
        today.minusDays(1) -> "אתמול"
        else -> "יום ${dayNames[day.dayOfWeek.value - 1]} ${day.format(dm)}"
    }

    fun dayLabel(ms: Long) = dayLabel(ms.toLocalDate())

    /** Short elapsed time for big displays: "45 ד׳", "2:15", "3 ימים". */
    fun since(ms: Long, now: Long = System.currentTimeMillis()): String {
        val min = ((now - ms) / 60_000).toInt().coerceAtLeast(0)
        return when {
            min < 60 -> "$min ד׳"
            min < 48 * 60 -> "${min / 60}:${"%02d".format(min % 60)}"
            else -> "${min / (60 * 24)} ימים"
        }
    }

    fun age(days: Int): String = when {
        days < 14 -> if (days == 1) "יום אחד" else "$days ימים"
        days < 60 -> {
            val w = days / 7
            val d = days % 7
            "$w שבועות" + if (d > 0) " ו-$d ימים" else ""
        }
        else -> {
            val m = (days / 30.44).toInt()
            "$m חודשים"
        }
    }

    fun emoji(type: EventType) = when (type) {
        EventType.FEED -> "🍼"
        EventType.DIAPER -> "🧷"
        EventType.BATH -> "🛁"
        EventType.PUMP -> "🫧"
    }

    fun title(type: EventType) = when (type) {
        EventType.FEED -> "האכלה"
        EventType.DIAPER -> "חיתול"
        EventType.BATH -> "מקלחת"
        EventType.PUMP -> "שאיבה"
    }

    fun feedKind(kind: FeedKind?) = when (kind) {
        FeedKind.BREAST -> "הנקה"
        FeedKind.FORMULA -> "תמ״ל"
        FeedKind.EXPRESSED -> "שאוב"
        FeedKind.MIXED -> "שילוב"
        null -> "האכלה"
    }

    fun side(s: String?) = when (s) {
        "L" -> "שמאל"
        "R" -> "ימין"
        else -> ""
    }

    /** One-line description, e.g. "תמ״ל 90 מ״ל" or "הנקה · ש׳ 10 ד׳ · י׳ 8 ד׳". */
    fun describe(e: Event): String = when (e.type) {
        EventType.FEED -> buildList {
            add(feedKind(e.feedKind))
            if (e.leftMin > 0) add("ש׳ ${e.leftMin} ד׳")
            if (e.rightMin > 0) add("י׳ ${e.rightMin} ד׳")
            if (e.leftMin == 0 && e.rightMin == 0 && e.breastTotalMin > 0) add("${e.breastTotalMin} ד׳")
            if (e.leftMin == 0 && e.rightMin == 0 && e.lastSide != null) add("צד ${side(e.lastSide)}")
            if (e.formulaMl > 0) add("תמ״ל ${e.formulaMl} מ״ל")
            if (e.expressedMl > 0) add("שאוב ${e.expressedMl} מ״ל")
        }.joinToString(" · ")
        EventType.DIAPER -> when {
            e.wet && e.dirty -> "רטוב + מלוכלך"
            e.dirty -> "מלוכלך"
            e.wet -> "רטוב"
            else -> "יבש"
        }
        EventType.BATH -> "מקלחת"
        EventType.PUMP -> buildList {
            add("${e.pumpedMl} מ״ל")
            if (e.pumpLeftMl > 0 && e.pumpRightMl > 0) add("ש׳ ${e.pumpLeftMl} · י׳ ${e.pumpRightMl}")
            if (e.durationMin > 0) add("${e.durationMin} ד׳")
        }.joinToString(" · ")
    }.let { base -> if (e.note.isNotBlank()) "$base · ${e.note}" else base }
}
