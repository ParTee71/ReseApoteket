package se.partee71.reseapoteket.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import se.partee71.reseapoteket.core.model.FamilyItem
import se.partee71.reseapoteket.core.model.Household
import se.partee71.reseapoteket.core.model.Identified
import se.partee71.reseapoteket.core.model.PackingItem
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.core.model.Regimen
import se.partee71.reseapoteket.core.model.Trip
import se.partee71.reseapoteket.data.common.CollectionFactory
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.common.EntityCollection
import se.partee71.reseapoteket.data.common.dataError
import se.partee71.reseapoteket.data.datastore.PendingHousehold
import se.partee71.reseapoteket.data.datastore.SettingsStore
import se.partee71.reseapoteket.data.household.HouseholdDirectory
import se.partee71.reseapoteket.data.household.HouseholdSession

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultHouseholdRepository @Inject constructor(
    collections: CollectionFactory,
    private val directory: HouseholdDirectory,
    private val session: HouseholdSession,
    private val settings: SettingsStore,
    private val clock: Clock,
) : HouseholdRepository {
    private val households = collections.households()

    override val current: Flow<Household?> =
        session.householdId.flatMapLatest { id -> if (id == null) flowOf(null) else households.observe(id) }

    override suspend fun create(name: String, ownerUid: String): Result<String> {
        // Ett hushåll som inte bekräftades av servern kan ligga kvar i skrivkön och nå servern
        // senare – det återanvänds, även efter omstart, så att ett nytt försök inte skapar ett till.
        val saved = settings.pendingHousehold.first()?.takeIf { it.ownerUid == ownerUid }
        val household = if (saved != null) {
            Household(saved.id, name, listOf(ownerUid), createdAt = saved.createdAt)
        } else {
            // Slumpat ID – rules förutsätter att ingen kan gissa ett hushålls-ID (NFR-16).
            Household(households.newId(), name, listOf(ownerUid), createdAt = clock.now())
                .also { settings.setPendingHousehold(PendingHousehold(it.id, ownerUid, it.createdAt!!)) }
        }
        // Via servern, inte offline först: hushållet ska finnas innan det väljs (HUS-2).
        val result = directory.create(household)
        if (saved != null && result.dataError() == DataError.PermissionDenied) {
            // Det sparade hushållet finns redan och användaren är inte längre medlem – börja om.
            settings.setPendingHousehold(null)
            return create(name, ownerUid)
        }
        return result
            .onSuccess {
                settings.setPendingHousehold(null)
                session.select(household.id)
            }
            .map { household.id }
    }

    override suspend fun select(householdId: String) {
        // Ett kvarglömt försök får inte återuppliva ett hushåll som sedan raderats.
        settings.setPendingHousehold(null)
        session.select(householdId)
    }

    /**
     * Bara [fields] – aldrig versionen, som en äldre kopia skulle kunna sänka. Rules kräver
     * `schemaVersion` i dokumentet; ett hushåll får den när det skapas, och ett äldre stämplas när
     * det öppnas (`HouseholdSession`) eller av `tools/db/migrate.mjs`.
     */
    override suspend fun update(household: Household, fields: Set<String>) = households.update(household, fields)
}

class DefaultPersonRepository @Inject constructor(collections: CollectionFactory) :
    PersonRepository, EntityCollection<Person> by collections.persons()

class DefaultFamilyItemRepository @Inject constructor(collections: CollectionFactory) :
    FamilyItemRepository, EntityCollection<FamilyItem> by collections.familyItems()

class DefaultTripRepository @Inject constructor(collections: CollectionFactory) :
    TripRepository, EntityCollection<Trip> by collections.trips()

class DefaultRegimenRepository @Inject constructor(private val collections: CollectionFactory) : RegimenRepository {

    override fun forPerson(personId: String) = collections.regimens(personId)

    override fun regimensFor(personIds: List<String>): Flow<Map<String, List<Regimen>>> = observeEach(personIds, ::forPerson)
}

class DefaultPackingRepository @Inject constructor(private val collections: CollectionFactory) : PackingRepository {

    override fun forTrip(tripId: String) = collections.packingItems(tripId)

    override fun packingFor(tripIds: List<String>): Flow<Map<String, List<PackingItem>>> = observeEach(tripIds, ::forTrip)

    override suspend fun replacePackingItems(tripId: String, items: List<PackingItem>, existing: List<PackingItem>?): Result<Unit> {
        val collection = forTrip(tripId)
        val current = existing?.let { Result.success(it) } ?: collection.getAll()
        return current.mapCatching { old ->
            val keep = items.map { it.id }.toSet()
            collection.batch(upserts = items, deletes = old.map { it.id }.filterNot { it in keep }).getOrThrow()
        }
    }
}

/** En samling per ID, sammanslagen till en karta per ID; tom lista ger en tom karta direkt. */
private fun <T : Identified> observeEach(ids: List<String>, collection: (String) -> EntityCollection<T>): Flow<Map<String, List<T>>> =
    if (ids.isEmpty()) {
        flowOf(emptyMap())
    } else {
        combine(ids.distinct().map { id -> collection(id).observe().map { id to it } }) { it.toMap() }
    }
