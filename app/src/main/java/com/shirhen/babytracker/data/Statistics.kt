package com.shirhen.babytracker.data

import java.time.Instant
import java.time.ZoneId

/** Averages over a period. "Per day" figures are per 24 hours of the period actually covered by data. */
data class Stats(
    val periodDays: Double,
    // Feeds
    val feeds: Int,
    val feedsPerDay: Double,
    val byKind: Map<FeedKind, Int>,
    val avgGapMin: Int?,
    val longestGapMin: Int?,
    val longestGapEndsAt: Long?,
    val nightFeedsPerNight: Double,
    // Bottle
    val bottleFeeds: Int,
    val bottleMlPerDay: Double,
    val avgMlPerBottle: Int?,
    val maxBottleMl: Int?,
    val formulaMlPerDay: Double,
    val expressedMlPerDay: Double,
    val avgFormulaPerFeed: Int?,
    val avgExpressedPerFeed: Int?,
    // Breast
    val breastFeeds: Int,
    val avgBreastMin: Int?,
    val breastMinPerDay: Double,
    val avgLeftMin: Int?,
    val avgRightMin: Int?,
    val lastSideLeft: Int,
    val lastSideRight: Int,
    // Diapers
    val diapers: Int,
    val diapersPerDay: Double,
    val wetPerDay: Double,
    val dirtyPerDay: Double,
    // Pump
    val pumps: Int,
    val pumpsPerDay: Double,
    val pumpMlPerDay: Double,
    val avgPumpMl: Int?,
    val avgPumpMin: Int?,
    val avgPumpLeftMl: Int?,
    val avgPumpRightMl: Int?,
    // Bath
    val baths: Int,
    val lastBath: Long?,
)

private fun List<Int>.avgOrNull(): Int? = if (isEmpty()) null else (sum().toDouble() / size).let { Math.round(it).toInt() }

private fun isNight(ms: Long): Boolean {
    val h = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).hour
    return h >= 22 || h < 6
}

/**
 * [all] is every event (newest first). Only events inside the last [periodMs] are counted.
 * If the log is younger than the period, the averages use the time since the first entry,
 * so the first days are not diluted by empty days.
 */
fun computeStats(all: List<Event>, periodMs: Long, now: Long = System.currentTimeMillis()): Stats {
    val from = now - periodMs
    val events = all.filter { it.time in from..now }
    val firstEver = all.minOfOrNull { it.time } ?: now
    val spanMs = (now - maxOf(from, firstEver)).coerceAtLeast(3_600_000L)
    // Less than a day of data is shown as-is (a count), not extrapolated to 24 hours.
    val days = (spanMs / 86_400_000.0).coerceAtLeast(1.0)
    fun perDay(n: Number) = n.toDouble() / days

    val feeds = events.filter { it.type == EventType.FEED }
    val feedTimes = feeds.map { it.time }.sorted()
    val gaps = feedTimes.zipWithNext { a, b -> ((b - a) / 60_000).toInt() to b }
    val longest = gaps.maxByOrNull { it.first }

    val bottle = feeds.filter { it.bottleMl > 0 }
    val withFormula = feeds.filter { it.formulaMl > 0 }
    val withExpressed = feeds.filter { it.expressedMl > 0 }
    val breast = feeds.filter { it.breastMin > 0 || it.feedKind == FeedKind.BREAST }
    val bySide = breast.filter { it.leftMin + it.rightMin > 0 }

    val diapers = events.filter { it.type == EventType.DIAPER }
    val pumps = events.filter { it.type == EventType.PUMP }
    val baths = events.filter { it.type == EventType.BATH }

    val nightFeeds = feeds.count { isNight(it.time) }

    return Stats(
        periodDays = days,
        feeds = feeds.size,
        feedsPerDay = perDay(feeds.size),
        byKind = feeds.groupingBy { it.feedKind ?: FeedKind.BREAST }.eachCount(),
        avgGapMin = gaps.map { it.first }.avgOrNull(),
        longestGapMin = longest?.first,
        longestGapEndsAt = longest?.second,
        nightFeedsPerNight = perDay(nightFeeds),
        bottleFeeds = bottle.size,
        bottleMlPerDay = perDay(bottle.sumOf { it.bottleMl }),
        avgMlPerBottle = bottle.map { it.bottleMl }.avgOrNull(),
        maxBottleMl = bottle.maxOfOrNull { it.bottleMl },
        formulaMlPerDay = perDay(withFormula.sumOf { it.formulaMl }),
        expressedMlPerDay = perDay(withExpressed.sumOf { it.expressedMl }),
        avgFormulaPerFeed = withFormula.map { it.formulaMl }.avgOrNull(),
        avgExpressedPerFeed = withExpressed.map { it.expressedMl }.avgOrNull(),
        breastFeeds = breast.size,
        avgBreastMin = breast.map { it.breastMin }.filter { it > 0 }.avgOrNull(),
        breastMinPerDay = perDay(breast.sumOf { it.breastMin }),
        avgLeftMin = bySide.map { it.leftMin }.avgOrNull(),
        avgRightMin = bySide.map { it.rightMin }.avgOrNull(),
        lastSideLeft = breast.count { it.lastSide == "L" },
        lastSideRight = breast.count { it.lastSide == "R" },
        diapers = diapers.size,
        diapersPerDay = perDay(diapers.size),
        wetPerDay = perDay(diapers.count { it.wet }),
        dirtyPerDay = perDay(diapers.count { it.dirty }),
        pumps = pumps.size,
        pumpsPerDay = perDay(pumps.size),
        pumpMlPerDay = perDay(pumps.sumOf { it.pumpedMl }),
        avgPumpMl = pumps.map { it.pumpedMl }.filter { it > 0 }.avgOrNull(),
        avgPumpMin = pumps.map { it.durationMin }.filter { it > 0 }.avgOrNull(),
        avgPumpLeftMl = pumps.filter { it.pumpLeftMl > 0 && it.pumpRightMl > 0 }.map { it.pumpLeftMl }.avgOrNull(),
        avgPumpRightMl = pumps.filter { it.pumpLeftMl > 0 && it.pumpRightMl > 0 }.map { it.pumpRightMl }.avgOrNull(),
        baths = baths.size,
        lastBath = all.firstOrNull { it.type == EventType.BATH }?.time,
    )
}
