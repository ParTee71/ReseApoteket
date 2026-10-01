package se.partee71.reseapoteket.data

import kotlinx.coroutines.flow.MutableStateFlow
import se.partee71.reseapoteket.core.schema.Schema
import se.partee71.reseapoteket.data.common.HouseholdScope
import se.partee71.reseapoteket.data.common.HouseholdVersion

/** Ett aktivt hushåll för tester, med styrbar version. */
class TestHouseholdScope(
    householdId: String? = "hushall-1",
    version: Int? = Schema.CURRENT_VERSION,
) : HouseholdScope {
    override val householdId = MutableStateFlow(householdId)
    override val schemaVersion = MutableStateFlow(versionOf(householdId, version))
    override val unreadable = MutableStateFlow<String?>(null)

    /** Sätter det aktiva hushållets version; `null` = okänd. */
    fun setVersion(version: Int?) {
        schemaVersion.value = versionOf(householdId.value, version)
    }

    private fun versionOf(id: String?, version: Int?) =
        if (id != null && version != null) HouseholdVersion(id, version) else null
}
