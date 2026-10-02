package se.partee71.reseapoteket.data.export

/**
 * "Exportera som JSON" (SET-5): det aktiva hushållet som en fil i samma format som
 * `tools/db export`, så att den kan läsas in med `tools/db import` (BCK-1).
 */
interface HouseholdExport {
    /** Filens innehåll; fel som `DataError`. Innehållet är hälsodata – loggas aldrig. */
    suspend fun export(): Result<String>
}
