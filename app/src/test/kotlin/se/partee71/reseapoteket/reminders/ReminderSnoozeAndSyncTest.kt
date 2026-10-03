package se.partee71.reseapoteket.reminders

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.model.PackingItem
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.core.model.Trip
import se.partee71.reseapoteket.data.FakeCollectionFactory
import se.partee71.reseapoteket.data.FixedClock
import se.partee71.reseapoteket.data.datastore.SettingsStore
import se.partee71.reseapoteket.data.repository.DefaultPackingRepository
import se.partee71.reseapoteket.data.repository.DefaultPersonRepository
import se.partee71.reseapoteket.data.repository.DefaultTripRepository
import se.partee71.reseapoteket.testing.FakeDataStore

/** "Senare" (PÅM-7) och att nästa genomgång följer ändringar medan appen syns (PÅM-6). */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ReminderSnoozeAndSyncTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val work get() = WorkManager.getInstance(context)

    @Before
    fun workManager() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
    }

    private fun waiting(name: String) = work.getWorkInfosForUniqueWork(name).get().filter { it.state == WorkInfo.State.ENQUEUED }

    @Test
    fun `Senare tar bort notisen och schemalägger samma påminnelse om en timme`() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val id = ReminderNotifier.notificationId("gc/DAY_BEFORE/18:00")
        manager.notify(id, NotificationCompat.Builder(context, ReminderNotifier.CHANNEL).setSmallIcon(R.drawable.ic_luggage).build())
        val intent = Intent(context, ReminderSnoozeReceiver::class.java)
            .putExtra(ReminderSnoozeReceiver.EXTRA_KEY, "gc/DAY_BEFORE/18:00")
        ReminderSnoozeReceiver().onReceive(context, intent)
        assertEquals(emptyList(), shadowOf(manager).allNotifications)
        assertEquals(1, waiting("${ReminderScheduler.SNOOZE_PREFIX}gc/DAY_BEFORE/18:00").size)
    }

    @Test
    fun `en ändring medan appen syns flyttar nästa genomgång`() = runTest {
        val collections = FakeCollectionFactory()
        val trips = DefaultTripRepository(collections)
        val packing = DefaultPackingRepository(collections)
        val persons = DefaultPersonRepository(collections)
        val settings = SettingsStore(FakeDataStore())
        val clock = FixedClock() // mån 21 sep 2026 eftermiddag (UTC)
        val source = ReminderSource(trips, packing, persons, settings)
        val runner = ReminderRunner(source, settings, ReminderNotifier(context), clock) { TimeZone.UTC }
        val sync = ReminderSync(source, runner, ReminderScheduler(context, clock), clock)
        persons.upsert(Person("anna", "Anna"))
        settings.setReminderPersons(setOf("anna"))
        // Resan i morgon: dagen före-påminnelsen är i kväll kl. 18.
        trips.upsert(Trip("gc", "Gran Canaria", LocalDate(2026, 9, 22), 7, listOf("anna")))
        packing.forTrip("gc").upsert(PackingItem("r1"))

        val following = backgroundScope.launch { sync.follow() }
        testScheduler.advanceTimeBy(3.seconds)
        testScheduler.runCurrent()
        val delay = waiting(ReminderScheduler.CHECK).single().initialDelayMillis
        val evening = LocalDateTime(2026, 9, 21, 18, 0).toInstant(TimeZone.UTC)
        assertEquals((evening - clock.instant).inWholeMilliseconds, delay)
        following.cancel()
    }
}
