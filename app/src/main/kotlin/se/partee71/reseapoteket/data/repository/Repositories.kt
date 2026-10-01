package se.partee71.reseapoteket.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import se.partee71.reseapoteket.core.model.FamilyItem
import se.partee71.reseapoteket.core.model.Household
import se.partee71.reseapoteket.core.model.HouseholdSettings
import se.partee71.reseapoteket.core.model.PackingItem
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.core.model.Regimen
import se.partee71.reseapoteket.core.model.Trip
import se.partee71.reseapoteket.data.common.EntityCollection

// Tunna fasader som ViewModels talar med (skill firestore-data-layer). Vanliga läs- och
// skrivoperationer kommer från EntityCollection; här står bara domänfrågorna.

interface HouseholdRepository {
    /** Det aktiva hushållet, eller `null` om inget är valt. */
    val current: Flow<Household?>

    /** Skapar ett hushåll med [ownerUid] som enda medlem – bekräftat av servern – och gör det aktivt; ger dess ID. */
    suspend fun create(name: String, ownerUid: String): Result<String>

    /** Gör ett befintligt hushåll aktivt (HUS-1); ett obekräftat skapande behövs då inte längre och glöms. */
    suspend fun select(householdId: String)

    suspend fun update(household: Household): Result<Unit>
}

/**
 * Det beräkningsmotorn behöver från det aktiva hushållet – förhandsvisningarna i formulären.
 * Utan hushåll, eller om det inte går att läsa, gäller standardvärdena (förhandsvisningen är bara
 * en vägledning och får aldrig stoppa formuläret).
 */
fun HouseholdRepository.householdSettings(): Flow<HouseholdSettings> =
    current.map { it?.settings ?: HouseholdSettings() }.catch { emit(HouseholdSettings()) }

interface PersonRepository : EntityCollection<Person>

/** Antal personer som inte är arkiverade – resenärerna i familjeartiklarnas förhandsvisning (FAM-3). */
fun PersonRepository.activeCount(): Flow<Int> = observe().map { all -> all.count { !it.archived } }

interface FamilyItemRepository : EntityCollection<FamilyItem>

interface TripRepository : EntityCollection<Trip>

interface RegimenRepository {
    /** Recept och kosttillskott för en person. */
    fun forPerson(personId: String): EntityCollection<Regimen>

    /** Recepten för flera personer, per person-ID – underlag för packförslaget. */
    fun regimensFor(personIds: List<String>): Flow<Map<String, List<Regimen>>>
}

interface PackingRepository {
    /** Packlistan för en resa. */
    fun forTrip(tripId: String): EntityCollection<PackingItem>

    /**
     * Ersätter resans packlista med [items]; rader som inte finns kvar tas bort. Atomärt upp
     * till 500 rader. Läser listan först, så en rad som en annan enhet lägger till i samma
     * ögonblick kan tas bort – används bara när användaren själv räknar om listan.
     */
    suspend fun replacePackingItems(tripId: String, items: List<PackingItem>): Result<Unit>
}
