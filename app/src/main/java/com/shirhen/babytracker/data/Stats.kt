package com.shirhen.babytracker.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class Summary(
    val feeds: Int = 0,
    val bottleMl: Int = 0,
    val formulaMl: Int = 0,
    val expressedMl: Int = 0,
    val breastMin: Int = 0,
    val wet: Int = 0,
    val dirty: Int = 0,
    val baths: Int = 0,
    val pumps: Int = 0,
    val pumpedMl: Int = 0,
    val meds: Int = 0,
)

fun summarize(events: List<Event>): Summary {
    var s = Summary()
    for (e in events) {
        s = when (e.type) {
            EventType.FEED -> s.copy(
                feeds = s.feeds + 1,
                bottleMl = s.bottleMl + e.bottleMl,
                formulaMl = s.formulaMl + e.formulaMl,
                expressedMl = s.expressedMl + e.expressedMl,
                breastMin = s.breastMin + e.breastMin,
            )
            EventType.DIAPER -> s.copy(wet = s.wet + if (e.wet) 1 else 0, dirty = s.dirty + if (e.dirty) 1 else 0)
            EventType.BATH -> s.copy(baths = s.baths + 1)
            EventType.PUMP -> s.copy(pumps = s.pumps + 1, pumpedMl = s.pumpedMl + e.pumpedMl)
            EventType.MED -> s.copy(meds = s.meds + 1)
            EventType.CHECKIN -> s
        }
    }
    return s
}

fun Long.toLocalDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

fun LocalDate.startMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
    atStartOfDay(zone).toInstant().toEpochMilli()

/** The side to start with next time: the opposite of where the last breastfeed ended. */
fun nextSideOf(lastSide: String?): String? = when (lastSide) {
    "L" -> "R"
    "R" -> "L"
    else -> null
}

/**
 * Side to start the next breastfeed on. The AAP advises alternating the breast offered first, and
 * Cleveland Clinic to start with the breast that was emptied less (it feels fuller):
 * after one side, offer the other; after both sides, offer the side the feed ended on.
 */
fun List<Event>.nextBreastSide(): String? {
    val e = firstOrNull { it.type == EventType.FEED && (it.usedLeft || it.usedRight) } ?: return null
    return when {
        e.usedLeft && e.usedRight -> e.lastSide?.takeIf { it == "L" || it == "R" }
        e.usedLeft -> "R"
        else -> "L"
    }
}

fun List<Event>.lastBreastSide(): String? =
    firstOrNull { it.type == EventType.FEED && it.lastSide != null }?.lastSide
