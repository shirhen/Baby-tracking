package com.shirhen.babytracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class EventType { FEED, DIAPER, BATH, PUMP }

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
    /** "L" or "R": the side the feed ended on. */
    val lastSide: String? = null,
    val formulaMl: Int = 0,
    val expressedMl: Int = 0,
    // Diaper
    val wet: Boolean = false,
    val dirty: Boolean = false,
    // Pump
    val pumpLeftMl: Int = 0,
    val pumpRightMl: Int = 0,
    val durationMin: Int = 0,
    // Common
    val note: String = "",
    val createdBy: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)

val Event.bottleMl: Int get() = formulaMl + expressedMl
val Event.breastMin: Int get() = leftMin + rightMin
val Event.pumpedMl: Int get() = pumpLeftMl + pumpRightMl

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
