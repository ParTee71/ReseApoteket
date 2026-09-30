package se.partee71.reseapoteket.data.common

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import se.partee71.reseapoteket.core.schema.Schema

/** Det aktiva hushållet som samlingarna läser och skriver i (`HouseholdSession`). */
interface HouseholdScope {
    val householdId: StateFlow<String?>

    /** Hushållets `schemaVersion`; `null` så länge den inte är känd. */
    val schemaVersion: StateFlow<Int?>

    /** Varför en skrivning inte får göras just nu, eller `null` om den får. */
    fun writeBlocker(): DataError? =
        if (schemaVersion.value?.let(Schema::isNewerThanApp) == true) DataError.UpdateRequired else null
}

/** Hushållets `schemaVersion` som flöde – läses direkt från hushållsdokumentet. */
fun interface HouseholdVersionSource {
    fun schemaVersion(householdId: String): Flow<Int?>
}
