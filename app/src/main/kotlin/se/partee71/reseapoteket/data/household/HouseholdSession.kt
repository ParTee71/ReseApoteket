package se.partee71.reseapoteket.data.household

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import se.partee71.reseapoteket.data.common.HouseholdScope
import se.partee71.reseapoteket.data.common.HouseholdVersion
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

    /**
     * Det sparade valet som flöde: första värdet kommer först när inställningarna lästs, så
     * "inget valt" skiljs från "inte inläst än" (inloggningen ska inte skapa ett hushåll i onödan).
     */
    val selection: Flow<String?> = settings.activeHouseholdId.distinctUntilChanged()

    override val schemaVersion: StateFlow<HouseholdVersion?> =
        householdId.flatMapLatest { id ->
            if (id == null) emptyFlow() else versions.schemaVersion(id).map { HouseholdVersion(id, it) }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    /** Väljer hushåll och återvänder först när sessionen visar det – nästa skrivning hamnar rätt. */
    suspend fun select(householdId: String?) {
        settings.setActiveHouseholdId(householdId)
        this.householdId.first { it == householdId }
    }
}
