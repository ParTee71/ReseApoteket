package se.partee71.reseapoteket.data.household

import se.partee71.reseapoteket.core.model.Household

/** Hushållen en användare är medlem i – underlaget för att välja eller skapa hushåll vid inloggning. */
fun interface HouseholdDirectory {
    /**
     * Hushåll där [uid] finns i `memberUids`, frågat mot **servern**: ett tomt svar ska betyda
     * att det inte finns något, inte att cachen är tom – annars skapas ett hushåll till.
     * Utan nät blir det [se.partee71.reseapoteket.data.common.DataError.Offline].
     */
    suspend fun householdsOf(uid: String): Result<List<Household>>
}
