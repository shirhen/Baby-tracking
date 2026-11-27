package com.shirhen.babytracker.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Small per-device settings: who is using this phone and which family it syncs with. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    private val _userName = MutableStateFlow(sp.getString(KEY_NAME, "") ?: "")
    val userName: StateFlow<String> = _userName

    private val _familyId = MutableStateFlow(sp.getString(KEY_FAMILY, null))
    val familyId: StateFlow<String?> = _familyId

    fun setUserName(value: String) {
        sp.edit().putString(KEY_NAME, value).apply()
        _userName.value = value
    }

    fun setFamilyId(value: String?) {
        sp.edit().putString(KEY_FAMILY, value).apply()
        _familyId.value = value
    }

    private val _nightAuto = MutableStateFlow(sp.getBoolean(KEY_NIGHT_AUTO, true))
    /** Switch to night mode automatically between 22:00 and 06:00. */
    val nightAuto: StateFlow<Boolean> = _nightAuto

    fun setNightAuto(value: Boolean) {
        sp.edit().putBoolean(KEY_NIGHT_AUTO, value).apply()
        _nightAuto.value = value
    }

    private fun flag(key: String, default: Boolean) = MutableStateFlow(sp.getBoolean(key, default))

    private val _diaperWarning = flag(KEY_DIAPER_WARN, true)
    /** Show the "fewer than 6 wet diapers" warning. */
    val diaperWarning: StateFlow<Boolean> = _diaperWarning
    fun setDiaperWarning(v: Boolean) { sp.edit().putBoolean(KEY_DIAPER_WARN, v).apply(); _diaperWarning.value = v }

    private val _patternAlerts = flag(KEY_PATTERN, true)
    /** Notify when the last 24 hours differ from the baby's usual pattern. */
    val patternAlerts: StateFlow<Boolean> = _patternAlerts
    fun setPatternAlerts(v: Boolean) { sp.edit().putBoolean(KEY_PATTERN, v).apply(); _patternAlerts.value = v }

    private val _medReminders = flag(KEY_MED_REMIND, true)
    /** Notify when a scheduled medication time passes without a logged dose. */
    val medReminders: StateFlow<Boolean> = _medReminders
    fun setMedReminders(v: Boolean) { sp.edit().putBoolean(KEY_MED_REMIND, v).apply(); _medReminders.value = v }

    var askedNotificationPermission: Boolean
        get() = sp.getBoolean(KEY_ASKED_NOTIF, false)
        set(value) = sp.edit().putBoolean(KEY_ASKED_NOTIF, value).apply()

    /** Keys of alerts already notified, so each one is sent once. */
    fun wasNotified(key: String) = sp.getStringSet(KEY_NOTIFIED, emptySet())!!.contains(key)
    fun markNotified(key: String) {
        val set = sp.getStringSet(KEY_NOTIFIED, emptySet())!!.toMutableList()
        set += key
        // Keep the list short; old keys are never needed again.
        sp.edit().putStringSet(KEY_NOTIFIED, set.takeLast(200).toSet()).apply()
    }

    var lastPullAt: Long
        get() = sp.getLong(KEY_LAST_PULL, 0L)
        set(value) = sp.edit().putLong(KEY_LAST_PULL, value).apply()

    // ---- Trackers shown on this phone ----

    private val _hidden = MutableStateFlow(
        sp.getStringSet(KEY_HIDDEN, emptySet())!!
            .mapNotNull { runCatching { Tracker.valueOf(it) }.getOrNull() }
            .filter { it.canHide }
            .toSet(),
    )
    /** Trackers hidden on this phone. Each phone chooses its own (grandma may only need feeds and diapers). */
    val hiddenTrackers: StateFlow<Set<Tracker>> = _hidden

    fun setTrackerShown(t: Tracker, shown: Boolean) {
        if (!t.canHide) return
        val next = if (shown) _hidden.value - t else _hidden.value + t
        sp.edit().putStringSet(KEY_HIDDEN, next.map { it.name }.toSet()).apply()
        _hidden.value = next
    }

    fun isShown(type: EventType) = Tracker.of(type) !in _hidden.value

    private val _night = MutableStateFlow(
        sp.getString(KEY_NIGHT_BUTTONS, null)
            ?.split(',')
            ?.mapNotNull { runCatching { EventType.valueOf(it) }.getOrNull() }
            ?.filter { it in NIGHT_CHOICES }
            ?: NIGHT_DEFAULT,
    )
    /** Buttons on the night screen, in the order they were chosen. */
    val nightButtons: StateFlow<List<EventType>> = _night

    fun setNightButtons(list: List<EventType>) {
        val clean = list.distinct().filter { it in NIGHT_CHOICES }.take(NIGHT_MAX)
        sp.edit().putString(KEY_NIGHT_BUTTONS, clean.joinToString(",") { it.name }).apply()
        _night.value = clean
    }

    /** Last role this phone had in the family, so the app does not flash extra options while loading. */
    var cachedRole: Role
        get() = runCatching { Role.valueOf(sp.getString(KEY_ROLE, null)!!) }.getOrDefault(Role.PARENT)
        set(value) = sp.edit().putString(KEY_ROLE, value.name).apply()

    private val _lastBackupAt = MutableStateFlow(sp.getLong(KEY_LAST_BACKUP, 0L))
    val lastBackupAt: StateFlow<Long> = _lastBackupAt
    fun markBackup() {
        val now = System.currentTimeMillis()
        sp.edit().putLong(KEY_LAST_BACKUP, now).apply()
        _lastBackupAt.value = now
    }

    private companion object {
        const val KEY_HIDDEN = "hidden_trackers"
        const val KEY_NIGHT_BUTTONS = "night_buttons"
        const val KEY_ROLE = "cached_role"
        const val KEY_LAST_BACKUP = "last_backup"
        const val KEY_NAME = "user_name"
        const val KEY_FAMILY = "family_id"
        const val KEY_LAST_PULL = "last_pull"
        const val KEY_NIGHT_AUTO = "night_auto"
        const val KEY_DIAPER_WARN = "diaper_warning"
        const val KEY_PATTERN = "pattern_alerts"
        const val KEY_MED_REMIND = "med_reminders"
        const val KEY_ASKED_NOTIF = "asked_notif"
        const val KEY_NOTIFIED = "notified_keys"
    }
}
