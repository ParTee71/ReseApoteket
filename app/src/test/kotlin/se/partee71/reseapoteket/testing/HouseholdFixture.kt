package se.partee71.reseapoteket.testing

import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import se.partee71.reseapoteket.core.model.Household
import se.partee71.reseapoteket.core.schema.HouseholdCodec
import se.partee71.reseapoteket.core.schema.Schema
import se.partee71.reseapoteket.data.FakeCollectionFactory
import se.partee71.reseapoteket.data.FakeStore
import se.partee71.reseapoteket.data.FixedClock
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.common.HouseholdVersionSource
import se.partee71.reseapoteket.data.datastore.SettingsStore
import se.partee71.reseapoteket.data.datastore.settingsDataStore
import se.partee71.reseapoteket.data.firestore.Paths
import se.partee71.reseapoteket.data.household.HouseholdDirectory
import se.partee71.reseapoteket.data.household.HouseholdSession
import se.partee71.reseapoteket.data.repository.DefaultHouseholdRepository
import se.partee71.reseapoteket.domain.EnsureHouseholdUseCase

/**
 * Hushållskedjan för tester: riktig [HouseholdSession] (DataStore i [folder]) och repository,
 * fejkad Firestore ([FakeStore]). Versionen läses ur det lagrade hushållsdokumentet.
 */
class HouseholdFixture(scope: CoroutineScope, folder: File) {
    val store = FakeStore()

    /** `false` = hushållets version går inte att läsa just nu (som när servern inte svarat). */
    val versionsKnown = MutableStateFlow(true)

    /** Hushåll vars dokument servern nekar, som när användaren inte längre är medlem (HUS-5). */
    val denied = MutableStateFlow(emptySet<String>())
    private val versions = object : HouseholdVersionSource {
        override fun schemaVersion(householdId: String) = combine(store.documents, versionsKnown, denied) { all, known, denied ->
            if (householdId in denied) throw DataError.PermissionDenied
            if (known) (all[Paths.HOUSEHOLDS]?.get(householdId)?.get("schemaVersion") as? Number)?.toInt() ?: Schema.FIRST_VERSION else null
        }.filterNotNull()

        override fun stamp(householdId: String, version: Int) {
            if (store.read(Paths.HOUSEHOLDS, householdId) != null) store.set(Paths.HOUSEHOLDS, householdId, mapOf("schemaVersion" to version), merge = true)
        }
    }
    val settings = SettingsStore(settingsDataStore(scope) { File(folder, "settings.preferences_pb") })
    val session = HouseholdSession(settings, versions, scope)
    val collections = FakeCollectionFactory(store, session)
    val directory = FakeHouseholdDirectory(collections)
    val repository = DefaultHouseholdRepository(collections, directory, session, settings, FixedClock())
    val ensureHousehold = EnsureHouseholdUseCase(directory, repository)

    /** Lägger ett hushåll direkt i den fejkade databasen, som om en annan enhet skapat det. */
    fun storeHousehold(household: Household) = store.set(Paths.HOUSEHOLDS, household.id, HouseholdCodec.encode(household), merge = false)

    fun households(): List<Household> = store.documents.value[Paths.HOUSEHOLDS].orEmpty().map { (id, doc) -> HouseholdCodec.decode(id, doc) }
}

/**
 * Som servern: hushåll där uid är medlem. [failure] och [createFailure] simulerar t.ex. att nätet
 * saknas; [hold] låter en fråga vänta tills testet släpper den.
 */
class FakeHouseholdDirectory(private val collections: FakeCollectionFactory) : HouseholdDirectory {
    var failure: DataError? = null
    var createFailure: DataError? = null
    var hold: CompletableDeferred<Unit>? = null
    var calls = 0
        private set

    override suspend fun householdsOf(uid: String): Result<List<Household>> {
        calls++
        hold?.await()
        failure?.let { return Result.failure(it) }
        return collections.households().getAll().map { all -> all.filter { uid in it.memberUids } }
    }

    override suspend fun create(household: Household): Result<Unit> {
        createFailure?.let { return Result.failure(it) }
        // Som rules: ett befintligt hushåll får bara skrivas av sina medlemmar.
        val existing = collections.households().get(household.id).getOrThrow()
        if (existing != null && household.memberUids.any { it !in existing.memberUids }) {
            return Result.failure(DataError.PermissionDenied)
        }
        return collections.households().upsert(household)
    }
}
