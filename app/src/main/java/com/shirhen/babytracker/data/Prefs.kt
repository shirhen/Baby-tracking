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

    var lastPullAt: Long
        get() = sp.getLong(KEY_LAST_PULL, 0L)
        set(value) = sp.edit().putLong(KEY_LAST_PULL, value).apply()

    private companion object {
        const val KEY_NAME = "user_name"
        const val KEY_FAMILY = "family_id"
        const val KEY_LAST_PULL = "last_pull"
        const val KEY_NIGHT_AUTO = "night_auto"
    }
}
