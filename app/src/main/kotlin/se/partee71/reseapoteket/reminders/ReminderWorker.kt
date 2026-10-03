package se.partee71.reseapoteket.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException

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
        val runner = deps.runner()
        val scheduler = deps.scheduler()
        perform(inputData, runner::run, runner::showAgain, scheduler::checkAt, runner::retryAt)
        return Result.success()
    }

    companion object {
        const val KEY = "key"

        /**
         * Det jobbet gör, utan Android: "Senare" visar sin påminnelse igen; annars gås påminnelserna
         * igenom och nästa genomgång schemaläggs – även när genomgången fallerar, så att
         * påminnelserna aldrig stannar tyst. Schemaläggningen sist: att ersätta jobbet avbryter det här.
         */
        suspend fun perform(
            input: Data,
            run: suspend () -> Instant,
            showAgain: suspend (String) -> Unit,
            checkAt: (Instant) -> Unit,
            retryAt: () -> Instant,
        ) {
            val key = input.getString(KEY)
            if (key != null) {
                showAgain(key)
                return
            }
            val next = try {
                run()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                retryAt()
            }
            checkAt(next)
        }
    }
}
