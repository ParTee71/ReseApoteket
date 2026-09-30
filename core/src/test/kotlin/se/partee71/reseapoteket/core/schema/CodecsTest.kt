package se.partee71.reseapoteket.core.schema

import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.Test
import se.partee71.reseapoteket.core.model.DoseSchedule
import se.partee71.reseapoteket.core.model.FamilyItem
import se.partee71.reseapoteket.core.model.FamilyItemCategory
import se.partee71.reseapoteket.core.model.Household
import se.partee71.reseapoteket.core.model.ItemUnit
import se.partee71.reseapoteket.core.model.MedicineForm
import se.partee71.reseapoteket.core.model.PackingItem
import se.partee71.reseapoteket.core.model.PackingSection
import se.partee71.reseapoteket.core.model.PackingSource
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.core.model.QuantityRule
import se.partee71.reseapoteket.core.model.Regimen
import se.partee71.reseapoteket.core.model.RegimenKind
import se.partee71.reseapoteket.core.model.Trip
import se.partee71.reseapoteket.core.model.TripStatus

/** Ett kontraktstest per dokument-codec; proven är syntetiska. */
class CodecsTest {

    private val created = Instant.fromEpochSeconds(1_790_000_000)
    private val updated = Instant.fromEpochSeconds(1_790_086_400)

    @Test
    fun `HouseholdCodec`() = assertCodecContract(
        HouseholdCodec,
        Household("h1", "Familjen Test", listOf("uid-a", "uid-b"), marginDays = 3, schemaVersion = 7, createdAt = created),
        // Ett hushåll utan schemaVersion har det första formatet (Schema.FIRST_VERSION).
        Household("h1", schemaVersion = Schema.FIRST_VERSION),
    )

    @Test
    fun `PersonCodec`() = assertCodecContract(
        PersonCodec,
        Person("p1", "Anna", "🦊", "#BF4A2A", sortOrder = 2, archived = true),
        Person("p1"),
    )

    @Test
    fun `RegimenCodec`() = assertCodecContract(
        RegimenCodec,
        Regimen(
            id = "r1", kind = RegimenKind.SUPPLEMENT, name = "D-vitamin", form = MedicineForm.CAPSULE,
            strength = "20 µg", unit = ItemUnit.KAPSEL, packageSize = 90,
            schedule = DoseSchedule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), unitsPerDose = 2),
            notes = "Till frukost", active = false, sortOrder = 4, archived = true,
        ),
        Regimen("r1"),
    )

    @Test
    fun `FamilyItemCodec`() = assertCodecContract(
        FamilyItemCodec,
        FamilyItem(
            id = "f1", name = "Solkräm", category = FamilyItemCategory.CREAM, unit = ItemUnit.TUB,
            rule = QuantityRule.PerDays(quantity = 1, perDays = 5), notes = "SPF 50", active = false,
            sortOrder = 1, archived = true,
        ),
        FamilyItem("f1"),
    )

    @Test
    fun `TripCodec`() = assertCodecContract(
        TripCodec,
        Trip(
            id = "t1", name = "Kreta", startDate = LocalDate(2026, 7, 4), days = 9,
            travellerIds = listOf("p2", "p1"), status = TripStatus.PACKED,
            createdAt = created, updatedAt = updated, archived = true,
        ),
        Trip("t1"),
    )

    @Test
    fun `PackingItemCodec`() = assertCodecContract(
        PackingItemCodec,
        PackingItem(
            id = "i1", section = PackingSection.FAMILY, personId = "p1", sourceType = PackingSource.REGIMEN,
            sourceId = "r1", name = "Levaxin", unit = ItemUnit.TABLETT, suggestedQuantity = 18, quantity = 20,
            packages = 2, reason = "2 × 1 tablett/dag × 9 dagar", checked = true, checkedAt = updated,
            hidden = true, sortOrder = 3,
        ),
        PackingItem("i1"),
    )
}
