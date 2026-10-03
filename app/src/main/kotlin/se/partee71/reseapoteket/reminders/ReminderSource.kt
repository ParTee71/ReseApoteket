package se.partee71.reseapoteket.reminders

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import se.partee71.reseapoteket.core.engine.Reminder
import se.partee71.reseapoteket.core.engine.ReminderPlanner
import se.partee71.reseapoteket.data.common.combineByKey
import se.partee71.reseapoteket.data.datastore.SettingsStore
import se.partee71.reseapoteket.data.repository.PackingRepository
import se.partee71.reseapoteket.data.repository.PersonRepository
import se.partee71.reseapoteket.data.repository.TripRepository

/**
 * Påminnelseplanen ur hushållets resor och packlistor och telefonens inställningar (PÅM-1–5).
 * Bara packlistorna för resor som kan ge påminnelser följs, och bara personer som inte är
 * arkiverade räknas – en vald person som arkiveras slutar ge påminnelser. Ett läsfel avslutar flödet –
 * den som läser bestämmer om det ska försökas igen.
 */
@Singleton
class ReminderSource @Inject constructor(
    private val trips: TripRepository,
    private val packing: PackingRepository,
    private val persons: PersonRepository,
    private val settings: SettingsStore,
) {
    val plan: Flow<List<Reminder>> =
        combine(trips.observe(), settings.reminderSettings, persons.observe()) { list, chosen, people ->
            val active = people.filterNot { it.archived }.map { it.id }.toSet()
            list to chosen.copy(personIds = chosen.personIds intersect active)
        }
            .combineByKey(
                key = { (list, chosen) -> list.filter { ReminderPlanner.remindsAbout(it, chosen) }.map { it.id }.toSet() },
                other = { ids -> packing.packingFor(ids.toList()) },
            ) { (list, chosen), lists -> ReminderPlanner.plan(list, lists, chosen) }
}
