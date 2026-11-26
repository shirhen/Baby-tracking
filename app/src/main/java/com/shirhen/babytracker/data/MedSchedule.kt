package com.shirhen.babytracker.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Where one medication stands right now. */
data class MedStatus(
    val med: Medication,
    val lastGiven: Event?,
    /** The next scheduled time that has not been covered by a dose yet. Null for "as needed". */
    val nextDue: Long?,
    /** A scheduled time that has passed without a dose (within its window). */
    val overdueSlot: Long?,
)

object MedSchedule {
    private const val HOUR = 3_600_000L

    /**
     * How far from a scheduled time a dose still counts for it: half the gap to the neighbouring
     * times, but at most 2 hours. A medication given once a day gets the full 2 hours either side.
     */
    private fun windowMs(med: Medication): Long {
        val t = med.timeList
        if (t.size < 2) return 2 * HOUR
        val mins = t.map { it.toSecondOfDay() / 60 }
        val gaps = mins.zipWithNext { a, b -> b - a } + (24 * 60 - mins.last() + mins.first())
        return minOf(2 * HOUR, gaps.min() * 60_000L / 2)
    }

    /** Scheduled times from yesterday to tomorrow, as epoch millis, ascending. */
    private fun slots(med: Medication, now: Long, zone: ZoneId): List<Long> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return (-1L..1L).flatMap { d ->
            med.timeList.map { t -> ZonedDateTime.of(today.plusDays(d), t, zone).toInstant().toEpochMilli() }
        }.sorted()
    }

    fun status(
        med: Medication,
        events: List<Event>,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): MedStatus {
        val doses = events.filter { it.type == EventType.MED && it.medId == med.id }
        val last = doses.maxByOrNull { it.time }
        if (med.timeList.isEmpty()) return MedStatus(med, last, null, null)

        val w = windowMs(med)
        fun covered(slot: Long) = doses.any { it.time in (slot - w)..(slot + w) }

        val all = slots(med, now, zone)
        // Overdue: a time that has passed, still inside its window, with no dose.
        val overdue = all.lastOrNull { it <= now && now <= it + w && !covered(it) }
        // Next: the first upcoming time not already covered (a dose given a bit early covers it).
        val next = all.firstOrNull { it > now && !covered(it) }
        return MedStatus(med, last, next, overdue)
    }

    fun formatTimes(med: Medication): String =
        med.timeList.joinToString(", ") { "%02d:%02d".format(it.hour, it.minute) }.ifEmpty { "לפי הצורך" }

    fun parseTimes(list: List<LocalTime>): String =
        list.distinct().sorted().joinToString(",") { "%02d:%02d".format(it.hour, it.minute) }

    @Suppress("unused")
    private fun LocalDate.atTime(t: LocalTime, zone: ZoneId) = ZonedDateTime.of(this, t, zone)
}
