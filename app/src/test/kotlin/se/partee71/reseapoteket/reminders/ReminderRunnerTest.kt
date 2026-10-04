package se.partee71.reseapoteket.reminders

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.PendingIntent
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import se.partee71.reseapoteket.core.engine.PackingProgress
import se.partee71.reseapoteket.core.engine.Reminder
import se.partee71.reseapoteket.core.engine.ReminderKind
import se.partee71.reseapoteket.core.model.PackingItem
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.core.model.Trip
import se.partee71.reseapoteket.data.FakeCollectionFactory
import se.partee71.reseapoteket.data.datastore.SettingsStore
import se.partee71.reseapoteket.data.repository.DefaultPackingRepository
import se.partee71.reseapoteket.data.repository.DefaultPersonRepository
import se.partee71.reseapoteket.data.repository.DefaultTripRepository
import se.partee71.reseapoteket.testing.FakeDataStore

/** Genomgången av påminnelserna och notiserna (PÅM-2–7) mot fejkad databas och Robolectrics notiser. */
@RunWith(RobolectricTestRunner::class)
class ReminderRunnerTest {

    private class MovableClock(var now: Instant) : Clock {
        override fun now(): Instant = now
    }

    private val zone = TimeZone.of("Europe/Stockholm")
    private val application: Application = RuntimeEnvironment.getApplication()
    private val collections = FakeCollectionFactory()
    private val trips = DefaultTripRepository(collections)
    private val packing = DefaultPackingRepository(collections)
    private val persons = DefaultPersonRepository(collections)
    private val settings = SettingsStore(FakeDataStore())
    private val clock = MovableClock(at(2026, 9, 30, 17, 0))
    private val runner = ReminderRunner(ReminderSource(trips, packing, persons, settings), settings, ReminderNotifier(application), clock) { zone }
    private val notifications get() = shadowOf(application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).allNotifications

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int) = LocalDateTime(year, month, day, hour, minute).toInstant(zone)

    private suspend fun seed(packed: Int, total: Int, travellers: List<String> = listOf("anna", "erik")) {
        persons.upsert(Person("anna", "Anna"))
        persons.upsert(Person("erik", "Erik"))
        trips.upsert(Trip("gc", "Gran Canaria", LocalDate(2026, 10, 3), 7, travellers))
        (0 until total).forEach { packing.forTrip("gc").upsert(PackingItem("r$it", checked = it < packed)) }
        settings.setReminderPersons(setOf("anna"))
    }

    @Before
    fun allowNotifications() {
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun `första genomgången skickar inget som redan passerat och siktar på nästa påminnelse`() = runTest {
        seed(packed = 9, total = 16)
        clock.now = at(2026, 9, 30, 19, 0)
        assertEquals(at(2026, 10, 1, 1, 0), runner.run()) // ingen påminnelse före om 6 timmar
        assertEquals(emptyList(), notifications)
        assertEquals(clock.now, settings.remindersCheckedUntil.first())
    }

    @Test
    fun `en förfallen påminnelse visas privat med resans namn och antal kvar (PÅM-3, PÅM-7)`() = runTest {
        seed(packed = 9, total = 16)
        assertEquals(at(2026, 9, 30, 18, 0), runner.run())
        clock.now = at(2026, 9, 30, 18, 5)
        runner.run()
        val shown = notifications.single()
        assertEquals("Dags att packa för Gran Canaria", shown.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("7 av 16 kvar att packa.", shown.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(NotificationCompat.VISIBILITY_PRIVATE, shown.visibility)
        assertEquals("Påminnelse inför resan", shown.publicVersion.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Senare", shown.actions.single().title)
        // Notisens länkar är explicita och oföränderliga; resan öppnas via det icke-exporterade aliaset (#88).
        val open = shadowOf(shown.contentIntent).savedIntent
        assertEquals(TripLink.ALIAS, open.component?.className)
        assertEquals("gc", TripLink.tripId(open))
        val snooze = shadowOf(shown.actions.single().actionIntent)
        assertEquals(ReminderSnoozeReceiver::class.java.name, snooze.savedIntent.component?.className)
        for (flags in listOf(shadowOf(shown.contentIntent).flags, snooze.flags)) assertTrue(flags and PendingIntent.FLAG_IMMUTABLE != 0)
    }

    @Test
    fun `samma påminnelse skickas inte två gånger, och inte alls när den är för gammal (PÅM-6)`() = runTest {
        seed(packed = 0, total = 3)
        runner.run()
        clock.now = at(2026, 9, 30, 18, 1)
        runner.run()
        clock.now = at(2026, 9, 30, 18, 2)
        runner.run()
        assertEquals(1, notifications.size)
        // Telefonen avstängd dagen före: kvällens påminnelse är för gammal när den startar igen.
        clock.now = at(2026, 10, 3, 1, 0)
        runner.run()
        assertEquals(1, notifications.size)
    }

    @Test
    fun `inga påminnelser för resor där ingen av telefonens personer reser (PÅM-1)`() = runTest {
        seed(packed = 0, total = 3, travellers = listOf("erik"))
        runner.run()
        clock.now = at(2026, 9, 30, 18, 1)
        runner.run()
        assertEquals(emptyList(), notifications)
    }

    @Test
    fun `en vald person som arkiverats ger inte längre påminnelser`() = runTest {
        seed(packed = 0, total = 3)
        persons.upsert(Person("anna", "Anna", archived = true))
        runner.run()
        clock.now = at(2026, 9, 30, 18, 1)
        runner.run()
        assertEquals(emptyList(), notifications)
    }

    @Test
    fun `en påminnelse som flyttats bakåt till en passerad tid visas inte (PÅM-6)`() = runTest {
        seed(packed = 0, total = 3)
        clock.now = at(2026, 9, 30, 12, 0)
        runner.run()
        clock.now = at(2026, 9, 30, 19, 0)
        // Användaren ändrar inställningarna kl. 19, efter att jobbet kl. 18 inte hunnit köra.
        runner.settingsChanged()
        runner.run()
        assertEquals(emptyList(), notifications)
    }

    @Test
    fun `en sen genomgång inom sex timmar visar ändå påminnelsen`() = runTest {
        seed(packed = 0, total = 3)
        runner.run()
        clock.now = at(2026, 9, 30, 23, 30)
        runner.run()
        assertEquals("Dags att packa för Gran Canaria", notifications.single().extras.getString(Notification.EXTRA_TITLE))
    }

    @Test
    fun `utan tillåtelse att visa notiser visas inget`() = runTest {
        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        seed(packed = 0, total = 3)
        runner.run()
        clock.now = at(2026, 9, 30, 18, 1)
        runner.run()
        assertEquals(emptyList(), notifications)
    }

    @Test
    fun `Senare visar samma påminnelse igen om den fortfarande gäller`() = runTest {
        seed(packed = 1, total = 3)
        runner.showAgain("gc/DAY_BEFORE/18:00")
        assertEquals("Gran Canaria i morgon", notifications.single().extras.getString(Notification.EXTRA_TITLE))
        assertEquals("2 saker kvar att packa.", notifications.single().extras.getString(Notification.EXTRA_TEXT))
        runner.showAgain("gc/NO_LIST/18:00")
        assertEquals(1, notifications.size)
    }

    @Test
    fun `dagen före nämner det som packas på morgonen, resedagen räknar det utan namn (PAK-6, PÅM-5)`() {
        val resources = application.resources
        val dayBefore = Reminder(
            ReminderKind.DAY_BEFORE, "gc", "Gran Canaria", LocalDate(2026, 10, 3), LocalDateTime(2026, 10, 2, 18, 0),
            PackingProgress(10, 15), travelDayCount = 2,
        )
        assertEquals("3 saker kvar att packa. Och 2 packas på morgonen.", resources.reminderText(dayBefore).text)
        assertEquals("Allt annat är packat – 2 packas på morgonen.", resources.reminderText(dayBefore.copy(progress = PackingProgress(13, 15))).text)
        val morning = dayBefore.copy(kind = ReminderKind.TRAVEL_DAY, at = LocalDateTime(2026, 10, 3, 7, 0))
        assertEquals("2 saker att packa nu.", resources.reminderText(morning).text)
        assertEquals("1 sak att packa nu.", resources.reminderText(morning.copy(travelDayCount = 1)).text)
    }

    @Test
    fun `en egen tid på resedagen får en egen notis som Senare hittar igen (PAK-6)`() = runTest {
        seed(packed = 0, total = 2)
        packing.forTrip("gc").upsert(PackingItem("laddare", name = "Laddare", travelDay = true, travelDayTime = LocalTime(8, 30)))
        clock.now = at(2026, 10, 3, 6, 0)
        runner.run()
        clock.now = at(2026, 10, 3, 8, 31)
        runner.run()
        assertEquals(listOf("3 saker kvar att packa.", "1 sak att packa nu."), notifications.map { it.extras.getString(Notification.EXTRA_TEXT) })
        assertEquals(
            listOf("Påminnelse inför resan"),
            notifications.map { it.publicVersion.extras.getString(Notification.EXTRA_TITLE) }.distinct(),
            "låsskärmen visar aldrig vad som ska packas",
        )
        assertEquals(false, notifications.any { it.publicVersion.extras.getString(Notification.EXTRA_TEXT).orEmpty().contains("Laddare") })
        runner.showAgain("gc/TRAVEL_DAY/08:30")
        assertEquals(2, notifications.size, "samma notis ersätts")
    }

    @Test
    fun `texterna för ingen packlista och resedagen`() {
        val resources = application.resources
        val noList = Reminder(
            ReminderKind.NO_LIST, "f", "Fjällen", LocalDate(2026, 12, 19), LocalDateTime(2026, 12, 12, 18, 0),
            PackingProgress.NONE,
        )
        assertEquals("Fjällen om 7 dagar", resources.reminderText(noList).title)
        val travelDay = noList.copy(kind = ReminderKind.TRAVEL_DAY, at = LocalDateTime(2026, 12, 19, 7, 0), progress = PackingProgress(10, 11))
        assertEquals("Resedag – packa det sista", resources.reminderText(travelDay).title)
        assertEquals("1 sak kvar att packa.", resources.reminderText(travelDay).text)
    }

    @Test
    fun `nästa genomgång är första påminnelsen efter förra genomgången, men senast om sex timmar`() {
        val planned = listOf(
            Reminder(
                ReminderKind.TIME_TO_PACK, "gc", "Gran Canaria", LocalDate(2026, 10, 3), LocalDateTime(2026, 9, 30, 18, 0),
                PackingProgress(0, 1),
            ),
        )
        val now = at(2026, 9, 30, 18, 1)
        // Påminnelsen passerades efter förra genomgången: genomgången ska ske direkt.
        assertEquals(at(2026, 9, 30, 18, 0), runner.nextCheck(planned, checkedUntil = now - 30.minutes, now = now))
        assertEquals(now + ReminderRunner.REFRESH, runner.nextCheck(planned, checkedUntil = now, now = now))
    }
}
