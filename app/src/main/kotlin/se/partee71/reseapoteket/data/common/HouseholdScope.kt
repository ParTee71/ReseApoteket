package se.partee71.reseapoteket.data.common

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
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

    /**
     * Hushållet vars dokument senast inte gick att läsa (användaren är inte medlem, eller det är
     * raderat), annars `null`. Ett sådant hushåll väljs bort (HUS-5).
     */
    val unreadable: StateFlow<String?>
}

/** Den kända versionen för det aktiva hushållet, annars `null`. */
fun HouseholdScope.currentVersion(): Int? = schemaVersion.value?.takeIf { it.householdId == householdId.value }?.version

/**
 * Varför en skrivning inte får göras, eller `null` om den får (BCK-5). **Stängd som
 * standard:** så länge det aktiva hushållets version är okänd väntar skrivningen en stund på
 * den, och vägras sedan hellre än att en äldre app skriver över nyare data. Ett hushåll som
 * inte går att läsa ger [DataError.PermissionDenied] (HUS-5).
 */
suspend fun HouseholdScope.writeBlocker(): DataError? {
    val id = householdId.value ?: return null // samlingar som kräver hushåll kastar NoHousehold själva
    val verdict = withTimeoutOrNull(VERSION_WAIT) {
        combine(schemaVersion, unreadable) { version, denied ->
            when {
                denied == id -> Verdict(DataError.PermissionDenied)

                version?.householdId == id ->
                    Verdict(DataError.UpdateRequired.takeIf { Schema.isNewerThanApp(version.version) })

                else -> null
            }
        }.filterNotNull().first()
    } ?: return DataError.Offline
    return verdict.blocker
}

private class Verdict(val blocker: DataError?)

/** Hur länge en skrivning väntar på hushållets version (från cachen tar det millisekunder). */
private val VERSION_WAIT = 5.seconds

/** Hushållets `schemaVersion` direkt ur hushållsdokumentet (utan codec). */
interface HouseholdVersionSource {
    /**
     * Versionen som flöde. Ett dokument utan fältet har [Schema.FIRST_VERSION]. Går dokumentet
     * inte att läsa (inte medlem, raderat) avslutas flödet med [DataError.PermissionDenied];
     * andra fel försöker källan själv igen.
     */
    fun schemaVersion(householdId: String): Flow<Int>

    /**
     * Stämplar hushållet med [version] utan att något annat fält skrivs om. Läggs i cachen och
     * synkas senare (offline först); ett fel rapporteras inte – nästa öppning försöker igen.
     */
    fun stamp(householdId: String, version: Int)
}
