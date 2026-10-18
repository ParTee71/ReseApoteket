package se.partee71.reseapoteket.domain

import javax.inject.Inject
import kotlin.time.Instant
import se.partee71.reseapoteket.core.model.Household
import se.partee71.reseapoteket.core.schema.Schema
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.common.suspendRunCatching
import se.partee71.reseapoteket.data.household.HouseholdDirectory
import se.partee71.reseapoteket.data.repository.HouseholdRepository

/** Hur det gick att välja hushåll efter inloggning. */
sealed interface HouseholdStatus {
    data class Ready(val householdId: String) : HouseholdStatus

    /** Hushållet är sparat med en nyare version av appen (BCK-5). */
    data class RequiresUpdate(val householdId: String) : HouseholdStatus
}

/**
 * Efter inloggning (HUS-1, HUS-2): väljer användarens hushåll – det äldsta om det finns flera –
 * eller skapar "Mitt hushåll" om det inte finns något. Frågar servern, så att ett tomt svar
 * betyder att det verkligen saknas.
 */
class EnsureHouseholdUseCase @Inject constructor(
    private val directory: HouseholdDirectory,
    private val households: HouseholdRepository
) {
    suspend operator fun invoke(uid: String): Result<HouseholdStatus> = suspendRunCatching({ DataError.Unknown }) {
        val found = directory.householdsOf(uid).getOrThrow()
        // Äldst först; ett hushåll utan createdAt vet vi inget om och kommer sist.
        val existing = found.minWithOrNull(compareBy<Household, Instant?>(nullsLast()) { it.createdAt }.thenBy { it.id })
        if (existing == null) {
            HouseholdStatus.Ready(households.create(DEFAULT_NAME, uid).getOrThrow())
        } else {
            households.select(existing.id)
            if (Schema.isNewerThanApp(existing.schemaVersion)) {
                HouseholdStatus.RequiresUpdate(existing.id)
            } else {
                HouseholdStatus.Ready(existing.id)
            }
        }
    }

    companion object {
        /** Namnet på ett nytt hushåll – data, inte en UI-text; kan byta namn i Inställningar (Epic 3). */
        const val DEFAULT_NAME = "Mitt hushåll"
    }
}
