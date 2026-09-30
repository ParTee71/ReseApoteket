package se.partee71.reseapoteket.core.model

import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** `households/{hid}/trips/{tid}` – en resa. */
data class Trip(
    override val id: String,
    val name: String = "",
    val startDate: LocalDate? = null,
    val days: Int = 1,
    val travellerIds: List<String> = emptyList(),
    val status: TripStatus = TripStatus.PLANNING,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
    override val archived: Boolean = false,
) : Identified, Archivable

enum class TripStatus { PLANNING, PACKED, DONE }
