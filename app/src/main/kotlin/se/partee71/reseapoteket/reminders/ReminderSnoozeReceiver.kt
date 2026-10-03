package se.partee71.reseapoteket.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import kotlin.time.Clock
import se.partee71.reseapoteket.core.engine.ReminderKind

/** "Senare" i en påminnelse: notisen försvinner och kommer igen om en timme (PÅM-7). */
class ReminderSnoozeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val tripId = intent.getStringExtra(EXTRA_TRIP_ID) ?: return
        val kind = ReminderKind.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_KIND) } ?: return
        ReminderScheduler(context.applicationContext, Clock.System).snooze(tripId, kind)
        NotificationManagerCompat.from(context).cancel(ReminderNotifier.notificationId(tripId, kind))
    }

    companion object {
        const val EXTRA_TRIP_ID = "tripId"
        const val EXTRA_KIND = "kind"
    }
}
