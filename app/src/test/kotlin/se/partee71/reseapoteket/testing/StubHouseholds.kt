package se.partee71.reseapoteket.testing

import kotlinx.coroutines.flow.MutableStateFlow
import se.partee71.reseapoteket.core.model.Household
import se.partee71.reseapoteket.data.repository.HouseholdRepository

/** Bara det aktiva hushållet – formulärens förhandsvisningar läser marginalen därifrån. */
class StubHouseholds(override val current: MutableStateFlow<Household?>) : HouseholdRepository {
    override suspend fun create(name: String, ownerUid: String): Result<String> = error("används inte")

    override suspend fun select(householdId: String) = error("används inte")

    override suspend fun update(household: Household): Result<Unit> = error("används inte")
}
