package com.shirhen.babytracker.data

import android.content.Context
import com.shirhen.babytracker.sync.FamilySync
import com.shirhen.babytracker.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Single entry point for reads and writes. Every local write is mirrored to the family in Firestore. */
object Repo {
    lateinit var db: AppDatabase
        private set
    lateinit var prefs: Prefs
        private set
    lateinit var appContext: Context
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        appContext = context.applicationContext
        db = AppDatabase.get(appContext)
        prefs = Prefs(appContext)
    }

    // ---- Events ----

    suspend fun save(event: Event) {
        val stamped = event.copy(
            updatedAt = System.currentTimeMillis(),
            createdBy = event.createdBy.ifBlank { prefs.userName.value },
        )
        db.events().upsert(stamped)
        FamilySync.pushEvent(stamped)
        WidgetRefresher.refresh(appContext)
    }

    suspend fun delete(event: Event) = save(event.copy(deleted = true))

    /** Keeps whichever copy was edited last. Returns true if the local copy changed. */
    suspend fun mergeRemote(remote: Event): Boolean {
        val local = db.events().byId(remote.id)
        if (local == null || remote.updatedAt > local.updatedAt) {
            db.events().upsert(remote)
            return true
        }
        return false
    }

    // ---- Weights ----

    suspend fun saveWeight(weight: Weight) {
        val stamped = weight.copy(updatedAt = System.currentTimeMillis())
        db.weights().upsert(stamped)
        FamilySync.pushWeight(stamped)
    }

    suspend fun deleteWeight(weight: Weight) = saveWeight(weight.copy(deleted = true))

    suspend fun mergeRemote(remote: Weight): Boolean {
        val local = db.weights().byId(remote.id)
        if (local == null || remote.updatedAt > local.updatedAt) {
            db.weights().upsert(remote)
            return true
        }
        return false
    }

    // ---- Profile ----

    suspend fun saveProfile(profile: BabyProfile) {
        val stamped = profile.copy(id = 1, updatedAt = System.currentTimeMillis())
        db.profile().upsert(stamped)
        FamilySync.pushProfile(stamped)
        WidgetRefresher.refresh(appContext)
    }

    suspend fun mergeRemote(remote: BabyProfile): Boolean {
        val local = db.profile().get()
        if (local == null || remote.updatedAt > local.updatedAt) {
            db.profile().upsert(remote.copy(id = 1))
            return true
        }
        return false
    }
}
