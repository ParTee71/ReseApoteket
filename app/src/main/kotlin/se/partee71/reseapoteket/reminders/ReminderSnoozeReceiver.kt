package se.partee71.reseapoteket.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import kotlin.time.Clock

/** "Senare" i en påminnelse: notisen försvinner och kommer igen om en timme (PÅM-7). */
class ReminderSnoozeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        ReminderScheduler(context.applicationContext, Clock.System).snooze(key)
        NotificationManagerCompat.from(context).cancel(ReminderNotifier.notificationId(key))
    }

    companion object {
        const val EXTRA_KEY = "key"
    }
}
