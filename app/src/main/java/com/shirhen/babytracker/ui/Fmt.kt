package com.shirhen.babytracker.ui

import com.shirhen.babytracker.data.Event
import com.shirhen.babytracker.data.EventType
import com.shirhen.babytracker.data.FeedKind
import com.shirhen.babytracker.data.pumpedMl
import com.shirhen.babytracker.data.symptomList
import com.shirhen.babytracker.data.usedLeft
import com.shirhen.babytracker.data.usedRight
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
        EventType.MED -> "💊"
        EventType.CHECKIN -> "📝"
    }

    fun title(type: EventType) = when (type) {
        EventType.FEED -> "האכלה"
        EventType.DIAPER -> "חיתול"
        EventType.BATH -> "מקלחת"
        EventType.PUMP -> "שאיבה"
        EventType.MED -> "תרופה"
        EventType.CHECKIN -> "סיכום"
    }

    fun feedKind(kind: FeedKind?) = when (kind) {
        FeedKind.BREAST -> "הנקה"
        FeedKind.FORMULA -> "תמ״ל"
        FeedKind.EXPRESSED -> "שאוב"
        FeedKind.MIXED -> "שילוב"
        null -> "האכלה"
    }

    val cryLabels = listOf("כמעט לא", "מעט", "בינוני", "הרבה", "הרבה מאוד")
    val alertLabels = listOf("ישנוני", "רגוע", "ערני", "ערני מאוד", "לא נרגע")
    fun cryLabel(level: Int) = cryLabels.getOrElse(level - 1) { "?" }
    fun alertLabel(level: Int) = alertLabels.getOrElse(level - 1) { "?" }
    val gasLabels = listOf("בלי", "מעט", "הרבה", "המון")
    fun gasLabel(level: Int) = gasLabels.getOrElse(level - 1) { "?" }

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
            if (e.leftMin == 0 && e.rightMin == 0) {
                // No per-side minutes: name the breasts used, then the total time if there is one.
                when {
                    e.usedLeft && e.usedRight -> add("שני הצדדים")
                    e.usedLeft -> add("שמאל")
                    e.usedRight -> add("ימין")
                }
                if (e.breastTotalMin > 0) add("${e.breastTotalMin} ד׳")
            }
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
        EventType.MED -> e.medName?.takeIf { it.isNotBlank() } ?: "תרופה"
        EventType.CHECKIN -> buildList {
            add(if (e.checkScope == "DAY") "היום כולו" else "השעות האחרונות")
            e.cryLevel?.let { add("בכי: ${cryLabel(it)}") }
            e.alertLevel?.let { add("ערנות: ${alertLabel(it)}") }
            e.gasLevel?.let { add("גזים: ${gasLabel(it)}") }
            e.tummyMin?.let { add(if (it > 0) "זמן בטן $it ד׳" else "בלי זמן בטן") }
            e.symptomList.forEach { add(it.label) }
            e.tempC?.let { add("חום %.1f°".format(it)) }
            if (e.symptoms != null && e.symptomList.isEmpty() && e.tempC == null) add("בריא")
        }.joinToString(" · ")
    }.let { base -> if (e.note.isNotBlank()) "$base · ${e.note}" else base }
}
