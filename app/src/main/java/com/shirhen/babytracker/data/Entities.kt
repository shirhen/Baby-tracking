package com.shirhen.babytracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class EventType { FEED, DIAPER, BATH, PUMP, MED, CHECKIN }

enum class FeedKind { BREAST, FORMULA, EXPRESSED, MIXED }

/**
 * One logged event. A single table keeps sync simple: every row is one Firestore document.
 * Rows are never hard-deleted; [deleted] is synced so the other phone removes it too.
 */
@Entity(tableName = "events")
data class Event(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val type: EventType,
    val time: Long,
    // Feed
    val feedKind: FeedKind? = null,
    val leftMin: Int = 0,
    val rightMin: Int = 0,
    /** Total breastfeeding minutes when the sides were not timed separately. */
    @ColumnInfo(defaultValue = "0") val breastTotalMin: Int = 0,
    /** "L" or "R": the side the feed ended on. */
    val lastSide: String? = null,
    /** Breasts used in this feed: "L", "R" or "LR". Null for entries made before this existed. */
    val breastSides: String? = null,
    val formulaMl: Int = 0,
    val expressedMl: Int = 0,
    // Diaper
    val wet: Boolean = false,
    val dirty: Boolean = false,
    // Pump
    val pumpLeftMl: Int = 0,
    val pumpRightMl: Int = 0,
    val durationMin: Int = 0,
    // Medication
    val medId: String? = null,
    val medName: String? = null,
    // Check-in (a short summary of the last hours or the whole day). Null = not answered.
    /** "RECENT" (last few hours) or "DAY" (the whole day). */
    val checkScope: String? = null,
    /** 1 (hardly cried) … 5 (cried a lot). */
    val cryLevel: Int? = null,
    /** 1 (sleepy) … 5 (very alert). */
    val alertLevel: Int? = null,
    /** Gas / farts: 1 none, 2 a little, 3 a lot, 4 very many. */
    val gasLevel: Int? = null,
    /** Tummy time minutes; 0 = none, null = not answered. */
    val tummyMin: Int? = null,
    /** Comma-separated symptom codes, see [Symptom]. */
    val symptoms: String? = null,
    val tempC: Double? = null,
    // Common
    val note: String = "",
    val createdBy: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)

val Event.bottleMl: Int get() = formulaMl + expressedMl
val Event.breastMin: Int get() = if (leftMin + rightMin > 0) leftMin + rightMin else breastTotalMin
val Event.pumpedMl: Int get() = pumpLeftMl + pumpRightMl

/** Which breasts were used, also for older entries that only have minutes or the last side. */
val Event.usedLeft: Boolean get() = breastSides?.contains('L') ?: (leftMin > 0 || lastSide == "L")
val Event.usedRight: Boolean get() = breastSides?.contains('R') ?: (rightMin > 0 || lastSide == "R")

/**
 * A medication with its usual daily times.
 * [times] is a comma-separated list of "HH:mm"; empty means "as needed".
 */
@Entity(tableName = "meds")
data class Medication(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val dose: String = "",
    val times: String = "",
    val active: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)

val Medication.timeList: List<java.time.LocalTime>
    get() = times.split(',').mapNotNull { runCatching { java.time.LocalTime.parse(it.trim()) }.getOrNull() }.sorted()

@Entity(tableName = "weights")
data class Weight(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val time: Long,
    val grams: Int,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)

/** Single row (id = 1). [birthEpochDay] is a LocalDate epoch day. */
@Entity(tableName = "profile")
data class BabyProfile(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    val birthEpochDay: Long? = null,
    val gestWeeks: Int = 40,
    val gestDays: Int = 0,
    val birthWeightG: Int = 0,
    val updatedAt: Long = 0,
)

/** Health symptoms for the check-in. Codes are stored; labels are shown. */
enum class Symptom(val label: String) {
    COLD("מצונן / נזלת"),
    COUGH("שיעול"),
    TUMMY("כאבי בטן"),
    FEVER("חום"),
    VOMIT("הקאות / פליטות רבות"),
    DIARRHEA("שלשול"),
    CONSTIPATION("עצירות"),
    RASH("פריחה"),
    RESTLESS("אי שקט / לא רגוע"),
}

val Event.symptomList: List<Symptom>
    get() = symptoms.orEmpty().split(',').mapNotNull { c -> Symptom.entries.firstOrNull { it.name == c.trim() } }
