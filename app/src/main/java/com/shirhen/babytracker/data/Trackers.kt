package com.shirhen.babytracker.data

/** Things the family can choose to track. A hidden tracker disappears from the screens; its data stays. */
enum class Tracker(val title: String, val type: EventType?) {
    FEED("האכלות", EventType.FEED),
    DIAPER("חיתולים", EventType.DIAPER),
    PUMP("שאיבה", EventType.PUMP),
    MED("תרופות", EventType.MED),
    BATH("מקלחות", EventType.BATH),
    WEIGHT("משקל", null),
    CHECKIN("איך היה היום", EventType.CHECKIN),
    ;

    /** Feeding drives the clock, the guide and the widget header, so it cannot be hidden. */
    val canHide: Boolean get() = this != FEED

    companion object {
        fun of(type: EventType): Tracker = entries.first { it.type == type }
    }
}

/** Event types that can sit on the night screen, in display order. */
val NIGHT_CHOICES = listOf(EventType.FEED, EventType.DIAPER, EventType.PUMP, EventType.MED, EventType.BATH, EventType.CHECKIN)
val NIGHT_DEFAULT = listOf(EventType.FEED, EventType.DIAPER, EventType.PUMP, EventType.MED)
const val NIGHT_MAX = 6

/** What a family member may do. Checked in the app (it is a convenience, not a security boundary). */
enum class Role(val title: String, val description: String) {
    PARENT("הורה", "הכל: תיעוד, עריכה של כל רשומה, תרופות, פרטי התינוק וניהול המשפחה"),
    RECORDER("מתעד/ת", "מתעד/ת ועורך/ת רק את מה שהוסיף/ה. בלי שינוי הגדרות, תרופות ופרטי התינוק"),
}

data class Member(
    val uid: String,
    val name: String,
    val role: Role,
    val joinedAt: Long = 0L,
    val updatedAt: Long = 0L,
)
