package se.partee71.reseapoteket.core.model

import kotlin.time.Instant
import se.partee71.reseapoteket.core.schema.Schema

/** `households/{hid}` – hushållet och dess inställningar. */
data class Household(
    override val id: String,
    val name: String = "",
    val memberUids: List<String> = emptyList(),
    val marginDays: Int = HouseholdSettings.DEFAULT_MARGIN_DAYS,
    val schemaVersion: Int = Schema.CURRENT_VERSION,
    val createdAt: Instant? = null
) : Identified {
    val settings: HouseholdSettings get() = HouseholdSettings(marginDays)
}

/** Det beräkningsmotorn behöver från hushållet. */
data class HouseholdSettings(val marginDays: Int = DEFAULT_MARGIN_DAYS) {
    companion object {
        const val DEFAULT_MARGIN_DAYS = 2

        /** Det som går att välja i Inställningar (SET-2). */
        val MARGIN_DAYS_RANGE = 0..7
    }
}
