package se.partee71.reseapoteket.domain

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import se.partee71.reseapoteket.core.engine.PackingEngine
import se.partee71.reseapoteket.core.engine.PackingPreviewText
import se.partee71.reseapoteket.core.engine.PackingSuggestion
import se.partee71.reseapoteket.core.model.FamilyItem
import se.partee71.reseapoteket.core.model.HouseholdSettings
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.core.model.Trip
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.common.combineByKey
import se.partee71.reseapoteket.data.common.suspendRunCatching
import se.partee71.reseapoteket.data.repository.FamilyItemRepository
import se.partee71.reseapoteket.data.repository.HouseholdRepository
import se.partee71.reseapoteket.data.repository.PackingRepository
import se.partee71.reseapoteket.data.repository.PersonRepository
import se.partee71.reseapoteket.data.repository.RegimenRepository
import se.partee71.reseapoteket.data.repository.TripRepository
import se.partee71.reseapoteket.data.repository.householdSettings

/** Vad som händer med packlistan när en resa sparas. */
enum class PackingUpdate {
    /** Listan rörs inte. */
    KEEP,

    /** En ny resa: listan skrivs från förslaget (RES-3). */
    CREATE,

    /** Listan räknas om; avprickning, ändrade mängder och egna rader behålls (RES-4). */
    RECOUNT,
}

/**
 * Packförslaget för en resa – det enda stället som hämtar underlaget till [PackingEngine]
 * (personer, deras recept och kosttillskott, familjeartiklar, hushållets marginal):
 * sammanfattningen i reseformuläret, sparandet av en resa med sin packlista (RES-3, RES-4) och
 * "Räkna om" från packlistan (#26).
 */
class TripPacking @Inject constructor(
    private val trips: TripRepository,
    private val persons: PersonRepository,
    private val regimens: RegimenRepository,
    private val familyItems: FamilyItemRepository,
    private val households: HouseholdRepository,
    private val packing: PackingRepository,
) {
    /**
     * Sammanfattningen för resan som den ser ut i formuläret just nu (RES-3). Räknas om bara när
     * det förslaget bygger på ändras – inte vid varje bokstav i namnet – och recepten följs per
     * uppsättning resenärer, så att en ny resenär aldrig räknas utan sina recept. [people] är
     * hushållets personer som formuläret redan följer.
     */
    fun preview(trip: Flow<Trip>, people: Flow<List<Person>>): Flow<PackingPreviewText> {
        val basis = trip.map { it.copy(name = "") }.distinctUntilChanged()
        return combine(basis, people, familyItems.observe(), households.householdSettings(), ::Basis)
            .combineByKey({ it.trip.travellerIds.toSet() }, { ids -> regimens.regimensFor(ids.toList()) }) { b, byPerson ->
                PackingEngine.tripPreview(b.trip, b.persons, byPerson, b.family, b.settings)
            }
    }

    /**
     * Sparar resan och uppdaterar packlistan enligt [update]. Allt som förslaget bygger på läses
     * först, så att ett läsfel inte lämnar resan halvsparad. Packlistan skrivs före resan: en ny
     * resa finns aldrig utan sin lista, och en ändrad resa står kvar som osparad om listan inte
     * gick att skriva. (Skrivningar läggs i cachen direkt; fel efter läsningen är sällsynta.)
     */
    suspend fun save(trip: Trip, update: PackingUpdate): Result<Unit> = suspendRunCatching({ DataError.Unknown }) {
        when (update) {
            PackingUpdate.KEEP -> Unit
            PackingUpdate.CREATE -> {
                val items = suggest(trip).items
                // Ett tidigare försök kan ha skrivit en lista – den ersätts helt.
                packing.replacePackingItems(trip.id, items).getOrThrow()
            }
            PackingUpdate.RECOUNT -> {
                val existing = packing.forTrip(trip.id).getAll().getOrThrow()
                val items = PackingEngine.merge(existing, suggest(trip))
                packing.replacePackingItems(trip.id, items, existing).getOrThrow()
            }
        }
        trips.upsert(trip).getOrThrow()
    }

    private suspend fun suggest(trip: Trip): PackingSuggestion = PackingEngine.suggest(
        trip,
        persons.getAll().getOrThrow(),
        regimens.regimensFor(trip.travellerIds).first(),
        familyItems.getAll().getOrThrow(),
        households.householdSettings().first(),
    )

    private data class Basis(val trip: Trip, val persons: List<Person>, val family: List<FamilyItem>, val settings: HouseholdSettings)
}
