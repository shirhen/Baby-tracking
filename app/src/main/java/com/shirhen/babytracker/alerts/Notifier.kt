package com.shirhen.babytracker.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.shirhen.babytracker.R
import com.shirhen.babytracker.data.Alerts
import com.shirhen.babytracker.data.Repo
import com.shirhen.babytracker.ui.MainActivity

/** Sends each pattern alert / medication reminder once, as a phone notification. */
object Notifier {
    private const val CH_PATTERN = "pattern"
    private const val CH_MEDS = "meds"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_PATTERN, "שינוי בדפוס", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_MEDS, "תזכורות לתרופות", NotificationManager.IMPORTANCE_HIGH))
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    suspend fun check(context: Context) {
        if (!canNotify(context)) return
        val prefs = Repo.prefs
        val now = System.currentTimeMillis()
        val events = Repo.db.events().since(now - 16 * 24 * 3_600_000L)
        val list = buildList {
            if (prefs.patternAlerts.value) addAll(Alerts.pattern(events, now))
            if (prefs.medReminders.value) addAll(Alerts.medReminders(Repo.db.meds().active(), events, now))
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        list.filterNot { prefs.wasNotified(it.key) }.forEach { a ->
            val n = NotificationCompat.Builder(context, if (a.isMedReminder) CH_MEDS else CH_PATTERN)
                .setSmallIcon(R.drawable.ic_stat_baby)
                .setContentTitle(a.title)
                .setContentText(a.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(if (a.isMedReminder) a.text else a.text + "\n" + Alerts.NOTE))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(a.key.hashCode(), n)
                prefs.markNotified(a.key)
            } catch (_: SecurityException) {
                // Permission was withdrawn; try again next time.
            }
        }
    }
}
