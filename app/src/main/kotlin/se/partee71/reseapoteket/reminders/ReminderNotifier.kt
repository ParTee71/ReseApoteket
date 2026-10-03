package se.partee71.reseapoteket.reminders

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.datetime.daysUntil
import se.partee71.reseapoteket.MainActivity
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.engine.Reminder
import se.partee71.reseapoteket.core.engine.ReminderKind

/** Om appen får visa notiser – annars visas rutan "Notiser är avstängda" i Inställningar (PÅM-7). */
fun Context.canPostNotifications(): Boolean =
    NotificationManagerCompat.from(this).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

/** Rubrik och text för en påminnelse (PÅM-2–5) – bara resans namn och antal, aldrig vad som ska packas. */
data class ReminderText(val title: String, val text: String)

fun Resources.reminderText(reminder: Reminder): ReminderText {
    val left = reminder.progress.total - reminder.progress.packed
    return when (reminder.kind) {
        ReminderKind.NO_LIST -> {
            val days = reminder.at.date.daysUntil(reminder.startDate)
            ReminderText(getQuantityString(R.plurals.reminder_no_list_title, days, reminder.tripName, days), getString(R.string.reminder_no_list_text))
        }
        ReminderKind.TIME_TO_PACK -> ReminderText(
            getString(R.string.reminder_time_to_pack_title, reminder.tripName),
            getString(R.string.reminder_time_to_pack_text, left, reminder.progress.total),
        )
        ReminderKind.DAY_BEFORE -> ReminderText(getString(R.string.reminder_day_before_title, reminder.tripName), getQuantityString(R.plurals.reminder_left, left, left))
        ReminderKind.TRAVEL_DAY -> ReminderText(getString(R.string.reminder_travel_day_title), getQuantityString(R.plurals.reminder_left, left, left))
    }
}

/**
 * Visar påminnelserna som notiser i en egen kanal (PÅM-7). Innehållet är privat: låsskärmen
 * visar bara "Påminnelse inför resan". Tryck öppnar resans packlista; "Senare" visar den igen om en timme.
 */
@Singleton
class ReminderNotifier @Inject constructor(@ApplicationContext private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun show(reminder: Reminder) {
        if (!context.canPostNotifications()) return
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.reminder_channel))
                .setDescription(context.getString(R.string.reminder_channel_note))
                .build(),
        )
        val id = notificationId(reminder.tripId, reminder.kind)
        try {
            manager.notify(id, build(reminder, id))
        } catch (_: SecurityException) {
            // Behörigheten drogs in mellan kontrollen och visningen – inget att visa.
        }
    }

    internal fun build(reminder: Reminder, id: Int): Notification {
        val text = context.resources.reminderText(reminder)
        val public = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_luggage)
            .setContentTitle(context.getString(R.string.reminder_public_title))
            .setContentText(context.getString(R.string.reminder_public_text))
            .build()
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_luggage)
            .setContentTitle(text.title)
            .setContentText(text.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.text))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openTrip(reminder.tripId, id))
            .addAction(0, context.getString(R.string.reminder_snooze), snooze(reminder, id))
            .build()
    }

    private fun openTrip(tripId: String, id: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_TRIP_ID, tripId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun snooze(reminder: Reminder, id: Int): PendingIntent {
        val intent = Intent(context, ReminderSnoozeReceiver::class.java)
            .putExtra(ReminderSnoozeReceiver.EXTRA_TRIP_ID, reminder.tripId)
            .putExtra(ReminderSnoozeReceiver.EXTRA_KIND, reminder.kind.name)
        return PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val CHANNEL = "trip_reminders"

        /** Samma resa och påminnelse ersätter sin förra notis i stället för att lägga till en ny. */
        fun notificationId(tripId: String, kind: ReminderKind): Int = "$tripId/${kind.name}".hashCode()
    }
}
