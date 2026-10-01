package se.partee71.reseapoteket.core.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import se.partee71.reseapoteket.core.model.ItemUnit
import se.partee71.reseapoteket.core.model.PackingItem
import se.partee71.reseapoteket.core.model.PackingSection
import se.partee71.reseapoteket.core.model.PackingSource
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.core.model.TripStatus

/** Packlistans ordning och status (PAK-1, PAK-2, PAK-4, PAK-5). */
class PackingListTest {

    private val anna = Person("anna", "Anna", sortOrder = 0)
    private val erik = Person("erik", "Erik", sortOrder = 1)

    private fun item(id: String, section: PackingSection, person: String?, order: Int, checked: Boolean = false, hidden: Boolean = false) =
        PackingItem(id, section = section, personId = person, sortOrder = order, checked = checked, hidden = hidden)

    private val items = listOf(
        item("solkram", PackingSection.FAMILY, null, 0),
        item("erik-enalapril", PackingSection.MEDICINE, "erik", 1),
        item("anna-levaxin", PackingSection.MEDICINE, "anna", 2, checked = true),
        item("anna-zyrtec", PackingSection.MEDICINE, "anna", 3),
        item("anna-d", PackingSection.SUPPLEMENT, "anna", 4),
        // En egen rad till Erik läggs sist men visas hos Erik.
        PackingItem("egen", section = PackingSection.MEDICINE, personId = "erik", sourceType = PackingSource.MANUAL, sortOrder = 9),
        item("plaster", PackingSection.FAMILY, null, 5, hidden = true),
    )

    @Test
    fun `sektion, sedan person i hushållets ordning, sedan radens ordning`() {
        val arranged = PackingList.arrange(items, listOf(erik, anna), checkedLast = false)
        assertEquals(listOf("anna-levaxin", "anna-zyrtec", "erik-enalapril", "egen", "anna-d", "solkram"), arranged.visible.map { it.id })
        assertEquals(listOf("plaster"), arranged.hidden.map { it.id })
    }

    @Test
    fun `avprickade sist inom personen när inställningen är på (SET-3)`() {
        val arranged = PackingList.arrange(items, listOf(anna, erik), checkedLast = true)
        assertEquals(listOf("anna-zyrtec", "anna-levaxin"), arranged.visible.filter { it.personId == "anna" && it.section == PackingSection.MEDICINE }.map { it.id })
    }

    @Test
    fun `rader för en okänd person kommer efter de kända`() {
        val arranged = PackingList.arrange(listOf(item("x", PackingSection.MEDICINE, "borta", 0), item("a", PackingSection.MEDICINE, "anna", 1)), listOf(anna), checkedLast = false)
        assertEquals(listOf("a", "x"), arranged.visible.map { it.id })
    }

    @Test
    fun `allt packat ger Packad, en borttagen avprickning Planeras – Klar och tom lista ändras inte (PAK-5)`() {
        assertEquals(TripStatus.PACKED, PackingList.statusAfter(TripStatus.PLANNING, PackingProgress(3, 3)))
        assertEquals(TripStatus.PLANNING, PackingList.statusAfter(TripStatus.PACKED, PackingProgress(2, 3)))
        assertEquals(TripStatus.PLANNING, PackingList.statusAfter(TripStatus.PLANNING, PackingProgress(2, 3)))
        assertEquals(TripStatus.DONE, PackingList.statusAfter(TripStatus.DONE, PackingProgress(2, 3)))
        assertEquals(TripStatus.PLANNING, PackingList.statusAfter(TripStatus.PLANNING, PackingProgress.NONE))
        assertEquals(TripStatus.PACKED, PackingList.statusAfter(TripStatus.PACKED, PackingProgress.NONE), "lista utan synliga rader, t.ex. allt packat dolt")
    }

    @Test
    fun `mängden med förpackningar, utan dem när den ändrats (PAK-2, PAK-3)`() {
        val levaxin = PackingItem("l", sourceType = PackingSource.REGIMEN, unit = ItemUnit.TABLETT, suggestedQuantity = 18, quantity = 18, packages = 2)
        assertEquals("18 tabletter · 2 kartor", PackingList.amount(levaxin))
        assertEquals("20 tabletter", PackingList.amount(levaxin.copy(quantity = 20)))
        assertEquals(true, PackingList.isChanged(levaxin.copy(quantity = 20)))
        assertEquals(false, PackingList.isChanged(PackingItem("e", sourceType = PackingSource.MANUAL, quantity = 3)))
        assertEquals("1 tub", PackingList.amount(PackingItem("s", sourceType = PackingSource.FAMILY_ITEM, unit = ItemUnit.TUB, suggestedQuantity = 1, quantity = 1)))
    }
}
