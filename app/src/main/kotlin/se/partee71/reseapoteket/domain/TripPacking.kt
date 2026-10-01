package se.partee71.reseapoteket.domain

import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import se.partee71.reseapoteket.core.engine.PackingEngine
import se.partee71.reseapoteket.core.engine.PackingPreviewText
import se.partee71.reseapoteket.core.engine.PackingSuggestion
import se.partee71.reseapoteket.core.model.Trip
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.common.suspendRunCatching
import se.partee71.reseapoteket.data.repository.FamilyItemRepository
import se.partee71.reseapoteket.data.repository.HouseholdRepository
import se.partee71.reseapoteket.data.repository.PackingRepository
import se.partee71.reseapoteket.data.repository.PersonRepository
import se.partee71.reseapoteket.data.repository.RegimenRepository
import se.partee71.reseapoteket.data.repository.householdSettings

/**
 * Packförslaget för en resa – det enda stället som hämtar underlaget till [PackingEngine]
 * (personer, deras recept och kosttillskott, familjeartiklar, hushållets marginal):
 * sammanfattningen i reseformuläret, packlistan för en ny resa (RES-3) och "Räkna om" (RES-4,
 * också från packlistan i #26).
 */
class TripPacking @Inject constructor(
    private val persons: PersonRepository,
    private val regimens: RegimenRepository,
    private val familyItems: FamilyItemRepository,
    private val households: HouseholdRepository,
    private val packing: PackingRepository,
) {
    /** Sammanfattningen för resan som den ser ut i formuläret just nu (RES-3). */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun preview(trip: Flow<Trip>): Flow<PackingPreviewText> {
        // Bara det förslaget räknas på – inte varje bokstav i namnet.
        val basis = trip.map { it.copy(name = "") }.distinctUntilChanged()
        return basis.map { it.travellerIds }.distinctUntilChanged().flatMapLatest { ids ->
            combine(persons.observe(), regimens.regimensFor(ids), familyItems.observe(), households.householdSettings()) { all, byPerson, family, settings ->
                { current: Trip -> PackingEngine.tripPreview(current, all, byPerson, family, settings) }
            }
        }.combine(basis) { preview, current -> preview(current) }
    }

    /** Skriver packlistan för en ny resa (RES-3). */
    suspend fun create(trip: Trip): Result<Unit> = suspendRunCatching({ DataError.Unknown }) {
        packing.replacePackingItems(trip.id, suggest(trip).items).getOrThrow()
    }

    /**
     * Räknar om packlistan (RES-4): föreslagna rader får nya förslag; ändrad mängd, avprickning och
     * egna rader behålls (`PackingEngine.merge`).
     */
    suspend fun recount(trip: Trip): Result<Unit> = suspendRunCatching({ DataError.Unknown }) {
        val existing = packing.forTrip(trip.id).getAll().getOrThrow()
        packing.replacePackingItems(trip.id, PackingEngine.merge(existing, suggest(trip)), existing).getOrThrow()
    }

    private suspend fun suggest(trip: Trip): PackingSuggestion = PackingEngine.suggest(
        trip,
        persons.getAll().getOrThrow(),
        regimens.regimensFor(trip.travellerIds).first(),
        familyItems.getAll().getOrThrow(),
        households.householdSettings().first(),
    )
}
