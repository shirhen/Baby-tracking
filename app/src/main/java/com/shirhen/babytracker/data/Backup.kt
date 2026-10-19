package com.shirhen.babytracker.data

import android.content.Context
import android.net.Uri
import com.shirhen.babytracker.sync.FamilySync
import com.shirhen.babytracker.sync.eventFromMap
import com.shirhen.babytracker.sync.profileFromMap
import com.shirhen.babytracker.sync.toMap
import com.shirhen.babytracker.sync.weightFromMap
import com.shirhen.babytracker.ui.Fmt
import com.shirhen.babytracker.widget.WidgetRefresher
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Backup and restore.
 *  - JSON: everything (including deletions), restorable into the app.
 *  - CSV: a readable table for Excel / Google Sheets.
 * Restore only adds or updates entries (newer edit wins); it never deletes anything.
 */
object Backup {

    data class RestoreResult(val events: Int, val weights: Int, val profile: Boolean)

    private const val FORMAT = "baby-tracker-backup"

    // ---------- JSON ----------

    suspend fun writeJson(context: Context, uri: Uri) {
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
        Repo.db.profile().get()?.let { root.put("profile", JSONObject(it.toMap().filterValues { v -> v != null })) }
        root.put("events", JSONArray().apply {
            Repo.db.events().allIncludingDeleted().sortedBy { it.time }.forEach { e ->
                put(JSONObject(e.toMap().filterValues { it != null }).put("id", e.id))
            }
        })
        root.put("weights", JSONArray().apply {
            Repo.db.weights().allIncludingDeleted().sortedBy { it.time }.forEach { w ->
                put(JSONObject(w.toMap()).put("id", w.id))
            }
        })
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(root.toString(2).toByteArray()) }
    }

    suspend fun restoreJson(context: Context, uri: Uri): RestoreResult {
        val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        val root = JSONObject(text)
        require(root.optString("format") == FORMAT) { "זה לא קובץ גיבוי של האפליקציה" }

        var events = 0
        root.optJSONArray("events")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val e = eventFromMap(o.getString("id"), o.toMap()) ?: continue
                if (Repo.mergeRemote(e)) {
                    FamilySync.pushEvent(e)
                    events++
                }
            }
        }
        var weights = 0
        root.optJSONArray("weights")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val w = weightFromMap(o.getString("id"), o.toMap())
                if (Repo.mergeRemote(w)) {
                    FamilySync.pushWeight(w)
                    weights++
                }
            }
        }
        var profile = false
        root.optJSONObject("profile")?.let { o ->
            val p = profileFromMap(o.toMap())
            if (Repo.mergeRemote(p)) {
                FamilySync.pushProfile(p)
                profile = true
            }
        }
        WidgetRefresher.refresh(context)
        return RestoreResult(events, weights, profile)
    }

    private fun JSONObject.toMap(): Map<String, Any?> =
        keys().asSequence().associateWith { k -> if (isNull(k)) null else get(k) }

    // ---------- CSV ----------

    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    suspend fun writeCsv(context: Context, uri: Uri) {
        val zone = ZoneId.systemDefault()
        val header = listOf(
            "תאריך", "שעה", "סוג", "תיאור", "סוג האכלה", "הנקה שמאל (ד׳)", "הנקה ימין (ד׳)", "הנקה סה״כ (ד׳)",
            "צד אחרון", "תמ״ל (מ״ל)", "שאוב (מ״ל)", "רטוב", "מלוכלך", "שאיבה שמאל (מ״ל)", "שאיבה ימין (מ״ל)",
            "משך שאיבה (ד׳)", "הערה", "תועד ע״י",
        )
        val sb = StringBuilder()
        sb.append('﻿') // BOM so Excel reads Hebrew correctly
        sb.appendLine(header.joinToString(",") { it.csv() })
        Repo.db.events().allIncludingDeleted().filter { !it.deleted }.sortedBy { it.time }.forEach { e ->
            val dt = Instant.ofEpochMilli(e.time).atZone(zone)
            val row = listOf(
                dt.format(dateFmt), dt.format(timeFmt), Fmt.title(e.type), Fmt.describe(e),
                if (e.type == EventType.FEED) Fmt.feedKind(e.feedKind) else "",
                e.leftMin.blankIfZero(), e.rightMin.blankIfZero(), e.breastMin.takeIf { e.type == EventType.FEED }.blankIfZero(),
                Fmt.side(e.lastSide), e.formulaMl.blankIfZero(), e.expressedMl.blankIfZero(),
                if (e.type == EventType.DIAPER && e.wet) "כן" else "",
                if (e.type == EventType.DIAPER && e.dirty) "כן" else "",
                e.pumpLeftMl.blankIfZero(), e.pumpRightMl.blankIfZero(), e.durationMin.blankIfZero(),
                e.note, e.createdBy,
            )
            sb.appendLine(row.joinToString(",") { it.csv() })
        }
        val weights = Repo.db.weights().allIncludingDeleted().filter { !it.deleted }.sortedBy { it.time }
        if (weights.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine(listOf("תאריך", "משקל (גרם)").joinToString(",") { it.csv() })
            weights.forEach { w ->
                sb.appendLine(listOf(Instant.ofEpochMilli(w.time).atZone(zone).format(dateFmt), w.grams.toString()).joinToString(","))
            }
        }
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(sb.toString().toByteArray()) }
    }

    private fun Int?.blankIfZero() = if (this == null || this == 0) "" else toString()

    private fun String.csv(): String =
        if (any { it == ',' || it == '"' || it == '\n' }) "\"" + replace("\"", "\"\"") + "\"" else this

    fun fileStamp(): String = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm").format(Instant.now().atZone(ZoneId.systemDefault()))
}
