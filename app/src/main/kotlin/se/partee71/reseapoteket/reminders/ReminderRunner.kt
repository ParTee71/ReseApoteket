package se.partee71.reseapoteket.reminders

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import se.partee71.reseapoteket.core.engine.Reminder
import se.partee71.reseapoteket.core.engine.ReminderKind
import se.partee71.reseapoteket.core.engine.ReminderPlanner
import se.partee71.reseapoteket.data.datastore.SettingsStore

/**
 * Går igenom påminnelserna (PÅM-6): visar de som förfallit sedan förra genomgången och säger när
 * nästa genomgång ska ske. Används av [ReminderWorker] i bakgrunden; [nextCheck] också medan appen syns.
 */
@Singleton
class ReminderRunner @Inject constructor(
    private val source: ReminderSource,
    private val settings: SettingsStore,
    private val notifier: ReminderNotifier,
    private val clock: Clock,
    private val zone: TimeZone,
) {
    /**
     * Visar förfallna påminnelser och ger tiden för nästa genomgång. Första gången skickas inget
     * som redan passerat; går underlaget inte att läsa (t.ex. utloggad) försöker den igen senare.
     */
    suspend fun run(): Instant {
        val now = clock.now()
        val planned = read() ?: return now + RETRY
        val since = checkedUntil()
        ReminderPlanner.due(planned, since, now, zone, GRACE).forEach(notifier::show)
        settings.setRemindersCheckedUntil(now)
        return nextCheck(planned, checkedUntil = now, now = now)
    }

    /** "Senare" i en notis: visar samma påminnelse igen om den fortfarande gäller. */
    suspend fun showAgain(tripId: String, kind: ReminderKind) {
        read()?.firstOrNull { it.tripId == tripId && it.kind == kind }?.let(notifier::show)
    }

    /**
     * Första påminnelsen efter [checkedUntil] – har den redan passerat körs genomgången direkt –
     * men senast om [REFRESH], så att ändringar från andra telefoner hinner med.
     */
    fun nextCheck(planned: List<Reminder>, checkedUntil: Instant, now: Instant): Instant =
        minOf(ReminderPlanner.next(planned, checkedUntil, zone) ?: (now + REFRESH), now + REFRESH)

    /** Hur långt påminnelserna gåtts igenom; första gången från nu – inget som redan passerat skickas. */
    suspend fun checkedUntil(): Instant =
        settings.remindersCheckedUntil.first() ?: clock.now().also { settings.setRemindersCheckedUntil(it) }

    private suspend fun read(): List<Reminder>? =
        try {
            withTimeoutOrNull(READ_TIMEOUT) { source.plan.first() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    companion object {
        /** En påminnelse som är äldre än så här när telefonen hinner köra skickas inte (PÅM-6). */
        val GRACE = 2.hours

        /** Hur ofta planen räknas om även utan påminnelse – resor ändras på andra telefoner. */
        val REFRESH = 6.hours

        val RETRY = 30.minutes

        private val READ_TIMEOUT = 30.seconds
    }
}
