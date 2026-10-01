package se.partee71.reseapoteket.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import se.partee71.reseapoteket.core.model.FamilyItem
import se.partee71.reseapoteket.core.model.Household
import se.partee71.reseapoteket.core.model.PackingItem
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.core.model.Regimen
import se.partee71.reseapoteket.core.model.Trip
import se.partee71.reseapoteket.data.common.CollectionFactory
import se.partee71.reseapoteket.data.common.EntityCollection
import se.partee71.reseapoteket.data.household.HouseholdDirectory
import se.partee71.reseapoteket.data.household.HouseholdSession

/** Singleton: det väntande hushållet ska överleva mellan försöken. */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultHouseholdRepository @Inject constructor(
    collections: CollectionFactory,
    private val directory: HouseholdDirectory,
    private val session: HouseholdSession,
    private val clock: Clock,
) : HouseholdRepository {
    private val households = collections.households()

    override val current: Flow<Household?> =
        session.householdId.flatMapLatest { id -> if (id == null) flowOf(null) else households.observe(id) }

    /** Hushållet som senast inte bekräftades av servern – återanvänds, så att ett nytt försök inte skapar ett till. */
    private var pending: Household? = null

    override suspend fun create(name: String, ownerUid: String): Result<String> {
        // Slumpat ID – rules förutsätter att ingen kan gissa ett hushålls-ID (NFR-16).
        val household = pending?.takeIf { it.memberUids == listOf(ownerUid) }
            ?: Household(households.newId(), name, listOf(ownerUid), createdAt = clock.now())
        pending = household
        // Via servern, inte offline först: hushållet ska finnas innan det väljs (HUS-2).
        return directory.create(household)
            .onSuccess {
                pending = null
                session.select(household.id)
            }
            .map { household.id }
    }

    override suspend fun update(household: Household) = households.upsert(household)
}

class DefaultPersonRepository @Inject constructor(collections: CollectionFactory) :
    PersonRepository, EntityCollection<Person> by collections.persons()

class DefaultFamilyItemRepository @Inject constructor(collections: CollectionFactory) :
    FamilyItemRepository, EntityCollection<FamilyItem> by collections.familyItems()

class DefaultTripRepository @Inject constructor(collections: CollectionFactory) :
    TripRepository, EntityCollection<Trip> by collections.trips()

class DefaultRegimenRepository @Inject constructor(private val collections: CollectionFactory) : RegimenRepository {

    override fun forPerson(personId: String) = collections.regimens(personId)

    override fun regimensFor(personIds: List<String>): Flow<Map<String, List<Regimen>>> =
        if (personIds.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(personIds.distinct().map { id -> forPerson(id).observe().map { id to it } }) { it.toMap() }
        }
}

class DefaultPackingRepository @Inject constructor(private val collections: CollectionFactory) : PackingRepository {

    override fun forTrip(tripId: String) = collections.packingItems(tripId)

    override suspend fun replacePackingItems(tripId: String, items: List<PackingItem>): Result<Unit> {
        val collection = forTrip(tripId)
        return collection.getAll().mapCatching { existing ->
            val keep = items.map { it.id }.toSet()
            collection.batch(upserts = items, deletes = existing.map { it.id }.filterNot { it in keep }).getOrThrow()
        }
    }
}
