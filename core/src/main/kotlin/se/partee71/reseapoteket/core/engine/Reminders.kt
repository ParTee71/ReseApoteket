package se.partee71.reseapoteket.core.engine

import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import se.partee71.reseapoteket.core.model.PackingItem
import se.partee71.reseapoteket.core.model.Trip
import se.partee71.reseapoteket.core.model.TripStatus

/** De fyra påminnelserna inför en resa (PÅM-2–5), i den ordning de kommer. */
enum class ReminderKind { NO_LIST, TIME_TO_PACK, DAY_BEFORE, TRAVEL_DAY }

/** En påminnelses inställning på telefonen: på/av, klockslag och – för [ReminderKind.NO_LIST] och [ReminderKind.TIME_TO_PACK] – dagar före. */
data class ReminderSetting(val enabled: Boolean, val time: LocalTime, val daysBefore: Int)

/**
 * Telefonens påminnelser (PÅM-1): vilka personer den påminner för och varje påminnelses
 * inställning. Utan personer kommer inga påminnelser.
 */
data class ReminderSettings(
    val personIds: Set<String> = emptySet(),
    private val byKind: Map<ReminderKind, ReminderSetting> = DEFAULTS
) {
    operator fun get(kind: ReminderKind): ReminderSetting = byKind[kind] ?: DEFAULTS.getValue(kind)

    fun with(kind: ReminderKind, setting: ReminderSetting): ReminderSettings = copy(byKind = byKind + (kind to setting))

    companion object {
        /** Dagar före kan väljas inom detta intervall där det går ([hasDaysBefore]). */
        val DAYS_BEFORE = 1..30

        val DEFAULTS: Map<ReminderKind, ReminderSetting> = mapOf(
            ReminderKind.NO_LIST to ReminderSetting(true, LocalTime(18, 0), 7),
            ReminderKind.TIME_TO_PACK to ReminderSetting(true, LocalTime(18, 0), 3),
            ReminderKind.DAY_BEFORE to ReminderSetting(true, LocalTime(18, 0), 1),
            ReminderKind.TRAVEL_DAY to ReminderSetting(true, LocalTime(7, 0), 0)
        )

        /** Påminnelser där användaren väljer antal dagar före; de andra har fast dag. */
        fun hasDaysBefore(kind: ReminderKind): Boolean = kind == ReminderKind.NO_LIST || kind == ReminderKind.TIME_TO_PACK
    }
}

/** En planerad påminnelse för en resa, med packläget när den räknades ut. */
data class Reminder(
    val kind: ReminderKind,
    val tripId: String,
    val tripName: String,
    val startDate: LocalDate,
    val at: LocalDateTime,
    val progress: PackingProgress,
    /**
     * Antal oprickade rader som packas på resedagen (PAK-6): för dagen före alla, för resedagen de
     * som påminns om vid just den här tiden. Bara antalet – namnen hamnar aldrig i en notis (PÅM-5).
     */
    val travelDayCount: Int = 0
) {
    /** Samma påminnelse för samma resa och tid – en notis ersätter sin förra, "Senare" hittar den igen. */
    val key: String get() = "$tripId/${kind.name}/${at.time}"
}

/**
 * Vilka påminnelser som ska komma (PÅM-2–6). Rena funktioner: resor, packlistor, inställningar
 * och tidpunkter skickas in; inget läses här. Planen räknas om varje gång, så en ändrad resa
 * eller avprickad lista slår igenom direkt.
 */
object ReminderPlanner {

    /** Alla påminnelser som gäller just nu, tidigast först. */
    fun plan(trips: List<Trip>, packing: Map<String, List<PackingItem>>, settings: ReminderSettings): List<Reminder> = trips.filter { remindsAbout(it, settings) }.flatMap { trip ->
        val items = packing[trip.id].orEmpty()
        val progress = PackingEngine.progress(items)
        val travelDay = items.filter { it.travelDay && !it.hidden && !it.checked }
        ReminderKind.entries.flatMap { kind -> reminders(kind, trip, progress, travelDay, settings) }
    }.sortedBy { it.at }

    /** PÅM-1: en resa som planeras, har startdatum och där någon av telefonens personer reser. */
    fun remindsAbout(trip: Trip, settings: ReminderSettings): Boolean = !trip.archived && trip.startDate != null && trip.status == TripStatus.PLANNING &&
        trip.travellerIds.any { it in settings.personIds }

    /**
     * Påminnelser vars tid passerats efter [since] och senast [now] – men inte äldre än [grace]:
     * en telefon som varit avstängd skickar inte gamla påminnelser i efterhand (PÅM-6).
     */
    fun due(planned: List<Reminder>, since: Instant, now: Instant, zone: TimeZone, grace: Duration): List<Reminder> = planned.filter {
        val at = it.at.toInstant(zone)
        at > since && at <= now && now - at <= grace
    }

    /** När nästa påminnelse efter [now] kommer, eller `null` om ingen är planerad. */
    fun next(planned: List<Reminder>, now: Instant, zone: TimeZone): Instant? = planned.map { it.at.toInstant(zone) }.filter { it > now }.minOrNull()

    /**
     * En påminnelse av [kind] för resan – för resedagen även en per egen tid bland raderna som
     * packas då (PAK-6, PÅM-5). Rader med egen tid samma som morgonens kommer i morgonens påminnelse.
     */
    private fun reminders(
        kind: ReminderKind,
        trip: Trip,
        progress: PackingProgress,
        travelDay: List<PackingItem>,
        settings: ReminderSettings
    ): List<Reminder> {
        val setting = settings[kind]
        val start = trip.startDate ?: return emptyList()
        val applies = when (kind) {
            ReminderKind.NO_LIST -> progress.total == 0
            else -> progress.total > 0 && !progress.isComplete
        }
        if (!setting.enabled || !applies) return emptyList()
        val daysBefore = when (kind) {
            ReminderKind.DAY_BEFORE -> 1
            ReminderKind.TRAVEL_DAY -> 0
            else -> setting.daysBefore.coerceIn(ReminderSettings.DAYS_BEFORE)
        }
        val date = start.minus(DatePeriod(days = daysBefore))
        fun at(time: LocalTime, count: Int) = Reminder(kind, trip.id, trip.name, start, LocalDateTime(date, time), progress, count)
        if (kind != ReminderKind.TRAVEL_DAY) {
            return listOf(at(setting.time, if (kind == ReminderKind.DAY_BEFORE) travelDay.size else 0))
        }
        val byTime = travelDay.groupBy { it.travelDayTime ?: setting.time }
        val morning = at(setting.time, byTime[setting.time].orEmpty().size)
        return listOf(morning) + byTime.filterKeys { it != setting.time }.map { (time, rows) -> at(time, rows.size) }
    }
}
