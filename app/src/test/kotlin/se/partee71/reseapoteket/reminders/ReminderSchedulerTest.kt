package se.partee71.reseapoteket.reminders

import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import se.partee71.reseapoteket.core.engine.ReminderKind
import se.partee71.reseapoteket.data.FixedClock

/** Schemaläggningen med WorkManager (PÅM-6): ett jobb till nästa genomgång, ett per "Senare". */
@RunWith(RobolectricTestRunner::class)
class ReminderSchedulerTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val clock = FixedClock()

    @Before
    fun workManager() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
    }

    private fun all(name: String): List<WorkInfo> = WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get()

    private fun waiting(name: String): List<WorkInfo> = all(name).filter { it.state == WorkInfo.State.ENQUEUED }

    @Test
    fun `nästa genomgång ersätter den som väntar`() {
        val scheduler = ReminderScheduler(context, clock)
        scheduler.checkAt(clock.instant + 5.hours)
        scheduler.checkAt(clock.instant + 2.hours)
        val work = waiting(ReminderScheduler.CHECK).single()
        assertEquals(2.hours.inWholeMilliseconds, work.initialDelayMillis)
    }

    @Test
    fun `en passerad tid körs direkt`() {
        ReminderScheduler(context, clock).checkAt(clock.instant - 1.hours)
        // Utan väntetid startar testets WorkManager jobbet direkt – det ligger inte kvar som väntande.
        assertEquals(0, all(ReminderScheduler.CHECK).single().initialDelayMillis)
    }

    @Test
    fun `Senare väntar en timme och sparar bara resans ID och typen`() {
        ReminderScheduler(context, clock).snooze("gc", ReminderKind.DAY_BEFORE)
        val work = waiting("${ReminderScheduler.SNOOZE_PREFIX}gc/DAY_BEFORE").single()
        assertEquals(1.hours.inWholeMilliseconds, work.initialDelayMillis)
    }
}
