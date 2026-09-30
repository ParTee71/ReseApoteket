package se.partee71.reseapoteket.data.household

import se.partee71.reseapoteket.core.model.Household

/**
 * Hushållen på **servern** – underlaget för att välja eller skapa hushåll vid inloggning. Till
 * skillnad från samlingarna (offline först) väntar båda operationerna på servern: ett tomt svar
 * ska betyda att inget hushåll finns, och ett nytt hushåll ska finnas innan det väljs.
 * Utan nät blir det [se.partee71.reseapoteket.data.common.DataError.Offline].
 */
interface HouseholdDirectory {
    /** Hushåll där [uid] finns i `memberUids`. */
    suspend fun householdsOf(uid: String): Result<List<Household>>

    /** Skapar [household] och återvänder först när servern tagit emot det. */
    suspend fun create(household: Household): Result<Unit>
}
