package se.partee71.reseapoteket.data.household

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import se.partee71.reseapoteket.core.schema.Schema
import se.partee71.reseapoteket.core.schema.SchemaMigrator
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.common.HouseholdScope
import se.partee71.reseapoteket.data.common.HouseholdVersion
import se.partee71.reseapoteket.data.common.HouseholdVersionSource
import se.partee71.reseapoteket.data.datastore.SettingsStore
import se.partee71.reseapoteket.di.ApplicationScope

/**
 * Det aktiva hushållet: ID:t sparas på enheten, versionen läses från hushållsdokumentet. Går
 * dokumentet inte att läsa väljs hushållet bort, så att inloggningen letar upp ett nytt (HUS-5).
 */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class HouseholdSession @Inject constructor(
    private val settings: SettingsStore,
    private val versions: HouseholdVersionSource,
    @ApplicationScope scope: CoroutineScope
) : HouseholdScope {

    override val householdId: StateFlow<String?> =
        settings.activeHouseholdId.stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * Det sparade valet som flöde: första värdet kommer först när inställningarna lästs, så
     * "inget valt" skiljs från "inte inläst än" (inloggningen ska inte skapa ett hushåll i onödan).
     */
    val selection: Flow<String?> = settings.activeHouseholdId.distinctUntilChanged()

    private val _unreadable = MutableStateFlow<String?>(null)
    override val unreadable: StateFlow<String?> = _unreadable.asStateFlow()

    override val schemaVersion: StateFlow<HouseholdVersion?> =
        householdId.flatMapLatest { id ->
            if (id == null) {
                emptyFlow()
            } else {
                versions.schemaVersion(id)
                    .onEach { version -> stampIfDataFree(id, version) }
                    .map { HouseholdVersion(id, it) }
                    .onEach { _unreadable.compareAndSet(id, null) }
                    .catch { error ->
                        // Källan försöker själv igen vid andra fel; bara en nekad läsning når hit.
                        if (error == DataError.PermissionDenied) {
                            _unreadable.value = id
                            // Går valet inte att rensa står hushållet kvar som oläsbart: skrivningar
                            // nekas, och nästa start försöker igen. Versionen får aldrig krascha appen.
                            try {
                                settings.clearActiveHouseholdId(ifEquals = id)
                            } catch (_: IOException) {
                            }
                        }
                    }
            }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    /** Hushåll som stämplats den här körningen – en nekad stämpling upprepas inte i en slinga. */
    private val stamped = mutableSetOf<String>()

    /**
     * Ett äldre hushåll vars väg till appens version inte ändrar några dokument stämplas direkt,
     * så att en äldre app på en annan enhet ber om uppdatering i stället för att läsa ett nytt
     * värde fel och skriva tillbaka det (BCK-5). Kräver dokumentändringar körs `migrate.mjs`.
     */
    private fun stampIfDataFree(householdId: String, version: Int) {
        if (SchemaMigrator.canStamp(version) && synchronized(stamped) { stamped.add(householdId) }) {
            versions.stamp(householdId, Schema.CURRENT_VERSION)
        }
    }

    /**
     * Väljer hushåll och återvänder när sessionen visar det – nästa skrivning hamnar rätt. Kan
     * inställningarna inte läsas tillbaka ges det upp efter en stund i stället för att hänga.
     */
    suspend fun select(householdId: String?) {
        settings.setActiveHouseholdId(householdId)
        withTimeoutOrNull(SELECT_WAIT) { this@HouseholdSession.householdId.first { it == householdId } }
    }

    private companion object {
        val SELECT_WAIT = 5.seconds
    }
}
