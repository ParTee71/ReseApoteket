package se.partee71.reseapoteket.data.common

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import se.partee71.reseapoteket.core.schema.Schema

/** Ett hushålls `schemaVersion` – versionen gäller just [householdId]. */
data class HouseholdVersion(val householdId: String, val version: Int)

/** Det aktiva hushållet som samlingarna läser och skriver i (`HouseholdSession`). */
interface HouseholdScope {
    val householdId: StateFlow<String?>

    /** Aktivt hushålls version; `null` eller ett annat hushålls version så länge den inte är känd. */
    val schemaVersion: StateFlow<HouseholdVersion?>
}

/** Den kända versionen för det aktiva hushållet, annars `null`. */
fun HouseholdScope.currentVersion(): Int? =
    schemaVersion.value?.takeIf { it.householdId == householdId.value }?.version

/**
 * Varför en skrivning inte får göras, eller `null` om den får (BCK-5). **Stängd som
 * standard:** så länge det aktiva hushållets version är okänd väntar skrivningen en stund på
 * den, och vägras sedan hellre än att en äldre app skriver över nyare data.
 */
suspend fun HouseholdScope.writeBlocker(): DataError? {
    val id = householdId.value ?: return null // samlingar som kräver hushåll kastar NoHousehold själva
    val version = withTimeoutOrNull(VERSION_WAIT) {
        schemaVersion.first { it?.householdId == id }
    } ?: return DataError.Offline
    return if (Schema.isNewerThanApp(version.version)) DataError.UpdateRequired else null
}

/** Hur länge en skrivning väntar på hushållets version (från cachen tar det millisekunder). */
private val VERSION_WAIT = 5.seconds

/**
 * Hushållets `schemaVersion` som flöde, läst direkt ur hushållsdokumentet. Ett dokument utan
 * fältet har [Schema.FIRST_VERSION]; vid fel emitteras ingenting (skrivningar förblir spärrade).
 */
fun interface HouseholdVersionSource {
    fun schemaVersion(householdId: String): Flow<Int>
}
