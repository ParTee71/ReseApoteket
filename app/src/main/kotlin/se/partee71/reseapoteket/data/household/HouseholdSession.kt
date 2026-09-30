package se.partee71.reseapoteket.data.household

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import se.partee71.reseapoteket.data.common.HouseholdScope
import se.partee71.reseapoteket.data.common.HouseholdVersionSource
import se.partee71.reseapoteket.data.datastore.SettingsStore
import se.partee71.reseapoteket.di.ApplicationScope

/** Det aktiva hushållet: ID:t sparas på enheten, versionen läses från hushållsdokumentet. */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class HouseholdSession @Inject constructor(
    private val settings: SettingsStore,
    versions: HouseholdVersionSource,
    @ApplicationScope scope: CoroutineScope,
) : HouseholdScope {

    override val householdId: StateFlow<String?> =
        settings.activeHouseholdId.stateIn(scope, SharingStarted.Eagerly, null)

    override val schemaVersion: StateFlow<Int?> =
        householdId.flatMapLatest { id -> if (id == null) flowOf(null) else versions.schemaVersion(id) }
            .stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun select(householdId: String?) = settings.setActiveHouseholdId(householdId)
}
