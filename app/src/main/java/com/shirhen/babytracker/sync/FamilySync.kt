package com.shirhen.babytracker.sync

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.shirhen.babytracker.BuildConfig
import com.shirhen.babytracker.data.BabyProfile
import com.shirhen.babytracker.data.Event
import com.shirhen.babytracker.data.Repo
import com.shirhen.babytracker.data.Weight
import com.shirhen.babytracker.widget.WidgetRefresher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

/**
 * Shares data between the two phones through Firestore:
 *   families/{code}              -> { members: [uid, ...] }
 *   families/{code}/events/{id}
 *   families/{code}/weights/{id}
 *   families/{code}/meta/profile
 * The family code is a long random string; only signed-in members can read the data (see firestore.rules).
 */
object FamilySync {
    private const val TAG = "FamilySync"

    /** False when the APK was built without google-services.json. */
    val available: Boolean get() = BuildConfig.HAS_FIREBASE

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    private val listeners = mutableListOf<ListenerRegistration>()

    private fun db() = FirebaseFirestore.getInstance()
    private fun family(code: String) = db().collection("families").document(code)

    private suspend fun uid(): String {
        val auth = FirebaseAuth.getInstance()
        auth.currentUser?.let { return it.uid }
        return auth.signInAnonymously().await().user?.uid ?: error("ההתחברות ל-Firebase נכשלה")
    }

    suspend fun createFamily(): String {
        val uid = uid()
        val code = randomCode()
        family(code).set(mapOf("members" to listOf(uid), "createdAt" to System.currentTimeMillis())).await()
        Repo.prefs.setFamilyId(code)
        pushAll(code)
        start()
        return code
    }

    suspend fun joinFamily(rawCode: String) {
        val code = rawCode.trim().uppercase().filter { it.isLetterOrDigit() }
        require(code.length >= 8) { "הקוד קצר מדי" }
        val uid = uid()
        val ref = family(code)
        val snap = ref.get().await()
        require(snap.exists()) { "לא נמצאה משפחה עם הקוד הזה" }
        val members = snap.get("members") as? List<*> ?: emptyList<Any>()
        if (uid !in members) ref.update("members", FieldValue.arrayUnion(uid)).await()
        Repo.prefs.setFamilyId(code)
        pushAll(code)
        start()
    }

    fun leave() {
        stop()
        Repo.prefs.setFamilyId(null)
        _status.value = ""
    }

    /** Starts live listeners. Safe to call repeatedly. */
    fun start() {
        if (!available) return
        val code = Repo.prefs.familyId.value ?: return
        stop()
        Repo.scope.launch {
            try {
                uid()
                val fam = family(code)
                listeners += fam.collection("events").addSnapshotListener { snap, err ->
                    if (err != null) { _status.value = "שגיאת סנכרון: ${err.message}"; return@addSnapshotListener }
                    val changes = snap?.documentChanges.orEmpty()
                        .filter { it.type != DocumentChange.Type.REMOVED }
                        .mapNotNull { eventFromMap(it.document.id, it.document.data) }
                    if (changes.isEmpty()) { _status.value = "מסונכרן"; return@addSnapshotListener }
                    Repo.scope.launch {
                        var changed = false
                        changes.forEach { if (Repo.mergeRemote(it)) changed = true }
                        if (changed) WidgetRefresher.refresh(Repo.appContext)
                        _status.value = "מסונכרן"
                    }
                }
                listeners += fam.collection("weights").addSnapshotListener { snap, _ ->
                    val changes = snap?.documentChanges.orEmpty()
                        .filter { it.type != DocumentChange.Type.REMOVED }
                        .map { weightFromMap(it.document.id, it.document.data) }
                    Repo.scope.launch { changes.forEach { Repo.mergeRemote(it) } }
                }
                listeners += fam.collection("meta").document("profile").addSnapshotListener { snap, _ ->
                    val data = snap?.data ?: return@addSnapshotListener
                    Repo.scope.launch {
                        if (Repo.mergeRemote(profileFromMap(data))) WidgetRefresher.refresh(Repo.appContext)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "start failed", e)
                _status.value = "שגיאת סנכרון: ${e.message}"
            }
        }
    }

    fun stop() {
        listeners.forEach { it.remove() }
        listeners.clear()
    }

    /** One-shot pull used by the background worker when the app is not open. */
    suspend fun pullOnce() {
        if (!available) return
        val code = Repo.prefs.familyId.value ?: return
        uid()
        val since = (Repo.prefs.lastPullAt - 5 * 60_000L).coerceAtLeast(0)
        val startedAt = System.currentTimeMillis()
        val docs = family(code).collection("events").whereGreaterThan("updatedAt", since).get().await()
        var changed = false
        docs.documents.forEach { d ->
            val e = eventFromMap(d.id, d.data ?: return@forEach) ?: return@forEach
            if (Repo.mergeRemote(e)) changed = true
        }
        family(code).collection("meta").document("profile").get().await().data?.let {
            if (Repo.mergeRemote(profileFromMap(it))) changed = true
        }
        Repo.prefs.lastPullAt = startedAt
        if (changed) WidgetRefresher.refresh(Repo.appContext)
    }

    // Writes are fire-and-forget: Firestore queues them offline and sends them when back online.

    fun pushEvent(e: Event) = write { family(it).collection("events").document(e.id).set(e.toMap()) }

    fun pushWeight(w: Weight) = write { family(it).collection("weights").document(w.id).set(w.toMap()) }

    fun pushProfile(p: BabyProfile) = write { family(it).collection("meta").document("profile").set(p.toMap()) }

    private inline fun write(block: (String) -> Unit) {
        if (!available) return
        val code = Repo.prefs.familyId.value ?: return
        try {
            block(code)
        } catch (e: Exception) {
            Log.w(TAG, "write failed", e)
        }
    }

    private suspend fun pushAll(code: String) {
        val fam = family(code)
        Repo.db.events().allIncludingDeleted().forEach { fam.collection("events").document(it.id).set(it.toMap()) }
        Repo.db.weights().allIncludingDeleted().forEach { fam.collection("weights").document(it.id).set(it.toMap()) }
        Repo.db.profile().get()?.takeIf { it.updatedAt > 0 }?.let {
            // Do not overwrite a newer profile the other parent already saved.
            val remote = fam.collection("meta").document("profile").get().await().data?.let(::profileFromMap)
            if (remote == null || it.updatedAt > remote.updatedAt) fam.collection("meta").document("profile").set(it.toMap())
        }
    }

    private fun randomCode(): String {
        // No 0/O/1/I to avoid typos when reading the code aloud.
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val rnd = SecureRandom()
        return (1..12).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("")
    }
}
