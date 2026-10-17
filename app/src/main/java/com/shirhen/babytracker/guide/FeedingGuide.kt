package com.shirhen.babytracker.guide

import com.shirhen.babytracker.data.BabyProfile
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * General intake estimate. Not medical advice — the UI always shows the sources and the caveat.
 *
 * Sources:
 *  - AAP / HealthyChildren.org "Amount and Schedule of Baby Formula Feedings":
 *      first week no more than ~30–60 ml per feed; about 75 ml per 453 g body weight per day
 *      (= ~165 ml/kg/day); usually no more than ~960 ml per 24 h.
 *  - UK practice (Mother&Baby / midwife guidance): 150 ml/kg/day as a low estimate.
 *  - Newborn stomach capacity by day (Blueberry Pediatrics): day 1 ~5–7 ml, day 3 ~22–30 ml, day 7 ~45–60 ml.
 *  - Preterm (<37 weeks): hospital protocols (e.g. GOSH NHS) use 150–180 ml/kg/day; corrected age is
 *    used for age-based milestones (CHOP discharge algorithm). Post-discharge plans are individual.
 */
object FeedingGuide {

    const val ML_PER_KG_LOW = 150
    const val ML_PER_KG_HIGH = 165          // AAP: 2.5 oz per lb per day
    const val ML_PER_KG_PRETERM_HIGH = 180
    const val DAILY_CAP_ML = 960            // AAP: ~32 oz per 24 h

    data class Result(
        val chronoDays: Int,
        val correctedDays: Int?,
        val preterm: Boolean,
        val weightG: Int?,
        val dailyMinMl: Int?,
        val dailyMaxMl: Int?,
        val perFeedMinMl: Int?,
        val perFeedMaxMl: Int?,
        val feedsPerDay: Int,
        val feedsFromLog: Boolean,
        val basis: String,
        val warning: String?,
    )

    // Stomach-capacity anchors for the first week: (day index where 0 = birth day, min ml, max ml).
    private val firstWeekAnchors = listOf(Triple(0, 5, 7), Triple(2, 22, 30), Triple(6, 45, 60))

    fun compute(
        profile: BabyProfile?,
        latestWeightG: Int?,
        feedsLast24h: Int,
        today: LocalDate = LocalDate.now(),
    ): Result? {
        val birthDay = profile?.birthEpochDay ?: return null
        val chrono = (today.toEpochDay() - birthDay).toInt().coerceAtLeast(0)
        val gestationDays = profile.gestWeeks * 7 + profile.gestDays
        val preterm = profile.gestWeeks < 37
        val corrected = if (preterm) chrono - (40 * 7 - gestationDays) else null

        val defaultFeeds = when {
            chrono < 30 -> 8
            chrono < 60 -> 7
            chrono < 120 -> 6
            else -> 5
        }
        // Use the real rhythm once there is enough logged data; otherwise a typical number for the age.
        val feedsFromLog = feedsLast24h >= 4
        val feeds = if (feedsFromLog) feedsLast24h else defaultFeeds

        val pretermWarning = if (preterm) {
            "נולד בשבוע ${profile.gestWeeks}+${profile.gestDays}. לפגים תוכנית ההזנה נקבעת אישית בפגייה – ההוראות שקיבלתם קודמות לחישוב הזה."
        } else null

        if (chrono < 7) {
            val (min, max) = firstWeek(chrono)
            return Result(
                chronoDays = chrono, correctedDays = corrected, preterm = preterm,
                weightG = latestWeightG ?: profile.birthWeightG.takeIf { it > 0 },
                dailyMinMl = null, dailyMaxMl = null,
                perFeedMinMl = min, perFeedMaxMl = max,
                feedsPerDay = feeds, feedsFromLog = feedsFromLog,
                basis = "שבוע ראשון: לפי נפח הקיבה ביום ${chrono + 1} לחיים. ה-AAP: לא יותר מ-30–60 מ״ל לארוחה בשבוע הראשון.",
                warning = pretermWarning,
            )
        }

        val weight = latestWeightG ?: profile.birthWeightG.takeIf { it > 0 }
        if (weight == null) {
            return Result(
                chronoDays = chrono, correctedDays = corrected, preterm = preterm, weightG = null,
                dailyMinMl = null, dailyMaxMl = null, perFeedMinMl = null, perFeedMaxMl = null,
                feedsPerDay = feeds, feedsFromLog = feedsFromLog,
                basis = "הזינו משקל בהגדרות כדי לחשב כמות.",
                warning = pretermWarning,
            )
        }

        val kg = weight / 1000.0
        val usePretermRange = preterm && (corrected ?: 0) < 90
        val highPerKg = if (usePretermRange) ML_PER_KG_PRETERM_HIGH else ML_PER_KG_HIGH
        val dailyMin = (kg * ML_PER_KG_LOW).roundToInt().coerceAtMost(DAILY_CAP_ML)
        val dailyMax = (kg * highPerKg).roundToInt().coerceAtMost(DAILY_CAP_ML)

        return Result(
            chronoDays = chrono, correctedDays = corrected, preterm = preterm, weightG = weight,
            dailyMinMl = dailyMin, dailyMaxMl = dailyMax,
            perFeedMinMl = roundTo5(dailyMin.toDouble() / feeds),
            perFeedMaxMl = roundTo5(dailyMax.toDouble() / feeds),
            feedsPerDay = feeds, feedsFromLog = feedsFromLog,
            basis = if (usePretermRange) {
                "משקל ${"%.2f".format(kg)} ק״ג × $ML_PER_KG_LOW–$ML_PER_KG_PRETERM_HIGH מ״ל לק״ג ליום (טווח מקובל לפגים)."
            } else {
                "משקל ${"%.2f".format(kg)} ק״ג × $ML_PER_KG_LOW–$ML_PER_KG_HIGH מ״ל לק״ג ליום (AAP: כ-75 מ״ל לכל 453 גרם). עד $DAILY_CAP_ML מ״ל ביממה."
            },
            warning = pretermWarning,
        )
    }

    /** Linear interpolation between the stomach-capacity anchors, capped at the AAP 60 ml. */
    private fun firstWeek(day: Int): Pair<Int, Int> {
        val d = day.coerceIn(0, 6)
        val upper = firstWeekAnchors.first { it.first >= d }
        val lower = firstWeekAnchors.last { it.first <= d }
        if (upper.first == lower.first) return upper.second to upper.third
        val t = (d - lower.first).toDouble() / (upper.first - lower.first)
        val min = lower.second + t * (upper.second - lower.second)
        val max = lower.third + t * (upper.third - lower.third)
        return min.roundToInt() to max.roundToInt().coerceAtMost(60)
    }

    private fun roundTo5(v: Double): Int = ((v / 5.0).roundToInt() * 5).coerceAtLeast(5)

    const val DISCLAIMER =
        "הערכה כללית בלבד. ה-AAP מדגישה שאין ספר או אתר שיכולים לקבוע בדיוק כמה וכמה פעמים להאכיל – סימני רעב ושובע של התינוק קובעים. בכל ספק – רופא/ת ילדים או טיפת חלב."

    const val SOURCES =
        "מקורות: AAP / HealthyChildren.org (כמות ותדירות האכלות בתמ״ל); Blueberry Pediatrics (נפח קיבה לפי יום); " +
            "Mother&Baby UK (150 מ״ל לק״ג); GOSH NHS ו-CHOP (פגים וגיל מתוקן)."
}
