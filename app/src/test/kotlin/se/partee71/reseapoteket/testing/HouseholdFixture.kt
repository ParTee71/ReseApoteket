package se.partee71.reseapoteket.testing

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import se.partee71.reseapoteket.core.model.Household
import se.partee71.reseapoteket.core.schema.HouseholdCodec
import se.partee71.reseapoteket.core.schema.Schema
import se.partee71.reseapoteket.data.FakeCollectionFactory
import se.partee71.reseapoteket.data.FakeStore
import se.partee71.reseapoteket.data.FixedClock
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.common.HouseholdVersionSource
import se.partee71.reseapoteket.data.datastore.SettingsStore
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
    private val versions = HouseholdVersionSource { id ->
        store.documents.map { all -> (all[Paths.HOUSEHOLDS]?.get(id)?.get("schemaVersion") as? Number)?.toInt() ?: Schema.FIRST_VERSION }
    }
    private val dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(folder, "settings.preferences_pb") }
    val session = HouseholdSession(SettingsStore(dataStore), versions, scope)
    val collections = FakeCollectionFactory(store, session)
    val directory = FakeHouseholdDirectory(collections)
    val repository = DefaultHouseholdRepository(collections, session, FixedClock())
    val ensureHousehold = EnsureHouseholdUseCase(directory, repository, session)

    /** Lägger ett hushåll direkt i den fejkade databasen, som om en annan enhet skapat det. */
    fun storeHousehold(household: Household) = store.set(Paths.HOUSEHOLDS, household.id, HouseholdCodec.encode(household), merge = false)

    fun households(): List<Household> = store.documents.value[Paths.HOUSEHOLDS].orEmpty().map { (id, doc) -> HouseholdCodec.decode(id, doc) }
}

/** Som servern: hushåll där uid är medlem. [failure] simulerar t.ex. att nätet saknas. */
class FakeHouseholdDirectory(private val collections: FakeCollectionFactory) : HouseholdDirectory {
    var failure: DataError? = null
    var calls = 0
        private set

    override suspend fun householdsOf(uid: String): Result<List<Household>> {
        calls++
        failure?.let { return Result.failure(it) }
        return collections.households().getAll().map { all -> all.filter { uid in it.memberUids } }
    }
}
