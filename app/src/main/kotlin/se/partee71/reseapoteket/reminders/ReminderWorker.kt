package se.partee71.reseapoteket.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import se.partee71.reseapoteket.core.engine.ReminderKind

/**
 * Bakgrundsjobbet bakom påminnelserna (PÅM-6): går igenom dem och schemalägger nästa
 * genomgång, eller visar en uppskjuten påminnelse igen ("Senare").
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun runner(): ReminderRunner

        fun scheduler(): ReminderScheduler
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        val tripId = inputData.getString(TRIP_ID)
        val kind = inputData.getString(KIND)?.let { name -> ReminderKind.entries.firstOrNull { it.name == name } }
        if (tripId != null && kind != null) {
            deps.runner().showAgain(tripId, kind)
        } else {
            // Sist: att ersätta jobbet avbryter det här, och då är allt redan gjort.
            deps.scheduler().checkAt(deps.runner().run())
        }
        return Result.success()
    }

    companion object {
        const val TRIP_ID = "tripId"
        const val KIND = "kind"
    }
}
