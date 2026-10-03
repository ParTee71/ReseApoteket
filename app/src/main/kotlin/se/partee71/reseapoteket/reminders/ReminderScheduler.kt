package se.partee71.reseapoteket.reminders

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.time.toJavaDuration
import se.partee71.reseapoteket.core.engine.ReminderKind

/**
 * Schemalägger [ReminderWorker] med WorkManager (PÅM-6): ett jobb till nästa genomgång, och ett
 * per "Senare". WorkManager behåller jobben vid omstart och uppdatering. Tiden är ungefärlig –
 * Android kan skjuta upp den en stund för att spara batteri.
 */
class ReminderScheduler @Inject constructor(@ApplicationContext context: Context, private val clock: Clock) {

    private val work = WorkManager.getInstance(context)

    /** Nästa genomgång vid [at]; ersätter den som redan väntar. */
    fun checkAt(at: Instant) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(delayUntil(at)).build()
        work.enqueueUniqueWork(CHECK, ExistingWorkPolicy.REPLACE, request)
    }

    /** "Senare": samma påminnelse igen om [SNOOZE]. Bara resans ID och typen sparas i jobbet. */
    fun snooze(tripId: String, kind: ReminderKind) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(SNOOZE.toJavaDuration())
            .setInputData(workDataOf(ReminderWorker.TRIP_ID to tripId, ReminderWorker.KIND to kind.name))
            .build()
        work.enqueueUniqueWork("$SNOOZE_PREFIX$tripId/${kind.name}", ExistingWorkPolicy.REPLACE, request)
    }

    private fun delayUntil(at: Instant): java.time.Duration = (at - clock.now()).coerceAtLeast(Duration.ZERO).toJavaDuration()

    companion object {
        const val CHECK = "reminders"
        const val SNOOZE_PREFIX = "reminder-snooze/"
        val SNOOZE = 1.hours
    }
}
