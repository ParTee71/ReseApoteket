package se.partee71.reseapoteket.core.model

import kotlin.test.assertEquals
import org.junit.Test

class TraitsTest {

    @Test
    fun `Sortable-ordningen sorterar på sortOrder`() {
        val people = listOf(Person("c", sortOrder = 3), Person("a", sortOrder = 1), Person("b", sortOrder = 2))
        assertEquals(listOf("a", "b", "c"), people.sortedWith(Sortable.ORDER).map { it.id })
    }

    @Test
    fun `hushållets inställningar följer marginDays`() {
        assertEquals(HouseholdSettings(5), Household("h", marginDays = 5).settings)
        assertEquals(HouseholdSettings.DEFAULT_MARGIN_DAYS, Household("h").settings.marginDays)
    }

    @Test
    fun `receptets namn visas med styrka när den finns`() {
        assertEquals("Levaxin 50 µg", Regimen("r", name = "Levaxin", strength = "50 µg").displayName)
        assertEquals("D-vitamin", Regimen("r", name = "D-vitamin").displayName)
    }

    @Test
    fun `listade recept är de av sorten som inte är arkiverade`() {
        val all = listOf(
            Regimen("r1", RegimenKind.MEDICINE),
            Regimen("r2", RegimenKind.MEDICINE, archived = true),
            Regimen("s1", RegimenKind.SUPPLEMENT, active = false)
        )
        assertEquals(listOf("r1"), all.listed(RegimenKind.MEDICINE).map { it.id })
        assertEquals(listOf("s1"), all.listed(RegimenKind.SUPPLEMENT).map { it.id }, "pausade räknas, arkiverade inte")
    }
}
