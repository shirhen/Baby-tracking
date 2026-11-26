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

    private companion object {
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
