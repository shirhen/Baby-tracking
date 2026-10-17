package com.shirhen.babytracker.sync

import com.shirhen.babytracker.data.BabyProfile
import com.shirhen.babytracker.data.Event
import com.shirhen.babytracker.data.EventType
import com.shirhen.babytracker.data.FeedKind
import com.shirhen.babytracker.data.Weight

// Firestore returns whole numbers as Long and may omit fields written by older versions,
// so every read has a default.

private fun Map<String, Any?>.int(key: String) = (this[key] as? Number)?.toInt() ?: 0
private fun Map<String, Any?>.long(key: String) = (this[key] as? Number)?.toLong() ?: 0L
private fun Map<String, Any?>.bool(key: String) = this[key] as? Boolean ?: false
private fun Map<String, Any?>.str(key: String) = this[key] as? String

fun Event.toMap(): Map<String, Any?> = mapOf(
    "type" to type.name,
    "time" to time,
    "feedKind" to feedKind?.name,
    "leftMin" to leftMin,
    "rightMin" to rightMin,
    "lastSide" to lastSide,
    "formulaMl" to formulaMl,
    "expressedMl" to expressedMl,
    "wet" to wet,
    "dirty" to dirty,
    "pumpLeftMl" to pumpLeftMl,
    "pumpRightMl" to pumpRightMl,
    "durationMin" to durationMin,
    "note" to note,
    "createdBy" to createdBy,
    "updatedAt" to updatedAt,
    "deleted" to deleted,
)

fun eventFromMap(id: String, m: Map<String, Any?>): Event? {
    val type = m.str("type")?.let { runCatching { EventType.valueOf(it) }.getOrNull() } ?: return null
    return Event(
        id = id,
        type = type,
        time = m.long("time"),
        feedKind = m.str("feedKind")?.let { runCatching { FeedKind.valueOf(it) }.getOrNull() },
        leftMin = m.int("leftMin"),
        rightMin = m.int("rightMin"),
        lastSide = m.str("lastSide"),
        formulaMl = m.int("formulaMl"),
        expressedMl = m.int("expressedMl"),
        wet = m.bool("wet"),
        dirty = m.bool("dirty"),
        pumpLeftMl = m.int("pumpLeftMl"),
        pumpRightMl = m.int("pumpRightMl"),
        durationMin = m.int("durationMin"),
        note = m.str("note") ?: "",
        createdBy = m.str("createdBy") ?: "",
        updatedAt = m.long("updatedAt"),
        deleted = m.bool("deleted"),
    )
}

fun Weight.toMap(): Map<String, Any?> = mapOf(
    "time" to time,
    "grams" to grams,
    "updatedAt" to updatedAt,
    "deleted" to deleted,
)

fun weightFromMap(id: String, m: Map<String, Any?>) = Weight(
    id = id,
    time = m.long("time"),
    grams = m.int("grams"),
    updatedAt = m.long("updatedAt"),
    deleted = m.bool("deleted"),
)

fun BabyProfile.toMap(): Map<String, Any?> = mapOf(
    "name" to name,
    "birthEpochDay" to birthEpochDay,
    "gestWeeks" to gestWeeks,
    "gestDays" to gestDays,
    "birthWeightG" to birthWeightG,
    "updatedAt" to updatedAt,
)

fun profileFromMap(m: Map<String, Any?>) = BabyProfile(
    id = 1,
    name = m.str("name") ?: "",
    birthEpochDay = (m["birthEpochDay"] as? Number)?.toLong(),
    gestWeeks = (m["gestWeeks"] as? Number)?.toInt() ?: 40,
    gestDays = m.int("gestDays"),
    birthWeightG = m.int("birthWeightG"),
    updatedAt = m.long("updatedAt"),
)
