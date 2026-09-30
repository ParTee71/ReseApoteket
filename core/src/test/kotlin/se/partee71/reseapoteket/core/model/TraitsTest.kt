package se.partee71.reseapoteket.core.model

import org.junit.Test
import kotlin.test.assertEquals

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
}
