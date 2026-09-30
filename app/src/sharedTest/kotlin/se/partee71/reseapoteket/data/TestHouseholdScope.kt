package se.partee71.reseapoteket.data

import kotlinx.coroutines.flow.MutableStateFlow
import se.partee71.reseapoteket.core.schema.Schema
import se.partee71.reseapoteket.data.common.HouseholdScope

/** Ett aktivt hushåll för tester, med styrbar version. */
class TestHouseholdScope(
    householdId: String? = "hushall-1",
    schemaVersion: Int? = Schema.CURRENT_VERSION,
) : HouseholdScope {
    override val householdId = MutableStateFlow(householdId)
    override val schemaVersion = MutableStateFlow(schemaVersion)
}
