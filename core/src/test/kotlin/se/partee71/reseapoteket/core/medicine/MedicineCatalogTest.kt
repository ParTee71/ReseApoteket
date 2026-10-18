package se.partee71.reseapoteket.core.medicine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate
import se.partee71.reseapoteket.core.model.DoseSchedule
import se.partee71.reseapoteket.core.model.ItemUnit
import se.partee71.reseapoteket.core.model.MedicineForm
import se.partee71.reseapoteket.core.model.Regimen

/** Uppslag i Läkemedelsverkets lista över läkemedel (REC-6, REC-7). */
class MedicineCatalogTest {

    private val file = listOf(
        "# Läkemedel som säljs i Sverige – Läkemedelsverket, CC BY 4.0",
        "# updated 2026-10-04",
        "Alvedon\t500 mg\tFilmdragerad tablett",
        "Alvedon\t665 mg\tTablett med modifierad frisättning",
        "Alvedon forte\t1 g\tFilmdragerad tablett",
        "Alvedon\t60 mg\tSuppositorium",
        "Alvedon\t24 mg/ml\tOral suspension",
        "Alvedon Novum\t500 mg\tFilmdragerad tablett",
        "Bricanyl Turbuhaler\t0,5 mg/dos\tInhalationspulver\tframtida kolumn",
        "Ipren\t\tTablett",
        "Lopéramid\t2 mg\tKapsel, hård",
        "Paracetamol Ägg\t500 mg\tTablett",
        "trasig rad",
        "Utan form\t5 mg\t"
    ).joinToString("\n")

    private val catalog = MedicineCatalog.parse(file)

    private fun names(query: String) = catalog.search(query).map { it.entry.title }

    @Test
    fun `filen läses med datum – trasiga rader hoppas över och okända kolumner ignoreras`() {
        assertEquals(LocalDate(2026, 10, 4), catalog.updated)
        assertEquals(10, catalog.entries.size)
        assertEquals(MedicineEntry("Alvedon", "500 mg", "Filmdragerad tablett"), catalog.entries.first())
        assertEquals(MedicineEntry("Bricanyl Turbuhaler", "0,5 mg/dos", "Inhalationspulver"), catalog.entries.single { it.name.startsWith("Bricanyl") })
        assertEquals("Ipren", catalog.entries.single { it.name == "Ipren" }.title)
    }

    @Test
    fun `under tre tecken ger inget, sedan högst fem träffar i filens ordning`() {
        assertEquals(emptyList(), catalog.search("Al"))
        assertEquals(emptyList(), catalog.search("  al  "))
        assertEquals(
            listOf("Alvedon 500 mg", "Alvedon 665 mg", "Alvedon forte 1 g", "Alvedon 60 mg", "Alvedon 24 mg/ml"),
            names("alved")
        )
    }

    @Test
    fun `början av namnet går före ett senare ord, och markeringen pekar på träffen`() {
        val forte = catalog.search("forte").single()
        assertEquals("Alvedon forte", forte.entry.name)
        assertEquals(8 to 5, forte.start to forte.length)
        assertEquals(listOf("Bricanyl Turbuhaler 0,5 mg/dos"), names("turbu"))
        // Styrkan hör till det man söker på.
        assertEquals(listOf("Alvedon 665 mg", "Alvedon 60 mg"), names("alvedon 6"))
        assertEquals(listOf("Alvedon 60 mg"), names("alvedon 60 "))
        assertEquals(listOf("Alvedon 500 mg", "Alvedon Novum 500 mg", "Paracetamol Ägg 500 mg"), names("500 mg"))
        // Mitt i ett ord är ingen träff.
        assertEquals(emptyList(), catalog.search("vedon"))
    }

    @Test
    fun `skiftläge och diakritiska tecken spelar ingen roll`() {
        assertEquals(listOf("Lopéramid 2 mg"), names("LOPERA"))
        assertEquals(listOf("Lopéramid 2 mg"), names("lopéra"))
        assertEquals(listOf("Paracetamol Ägg 500 mg"), names("agg"))
        assertEquals(listOf("Paracetamol Ägg 500 mg"), names("ägg"))
    }

    @Test
    fun `en tom fil eller ett okänt datum är tomt, inte ett fel`() {
        val empty = MedicineCatalog.parse("")
        assertNull(empty.updated)
        assertEquals(emptyList(), empty.search("alvedon"))
        assertNull(MedicineCatalog.parse("# updated i går").updated)
    }

    @Test
    fun `ett val fyller namn, styrka, form och enhet men inte doseringen eller förpackningen`() {
        val before = Regimen("r1", form = MedicineForm.TABLET, packageSize = 30, schedule = DoseSchedule.Daily(dosesPerDay = 2), notes = "Mot feber")
        val inhaler = catalog.entries.single { it.form == MedicineForm.INHALER }
        assertEquals(
            before.copy(name = "Bricanyl Turbuhaler", strength = "0,5 mg/dos", form = MedicineForm.INHALER, unit = ItemUnit.PUFF),
            before.fillFrom(inhaler)
        )
    }

    @Test
    fun `Läkemedelsverkets former blir receptets former`() {
        val expected = mapOf(
            "Filmdragerad tablett" to MedicineForm.TABLET,
            "Munsönderfallande tablett" to MedicineForm.TABLET,
            "Resoriblett, sublingual" to MedicineForm.TABLET,
            "Kapsel, hård" to MedicineForm.CAPSULE,
            "Enterokapsel, hård" to MedicineForm.CAPSULE,
            "Oral lösning" to MedicineForm.LIQUID,
            "Oral suspension" to MedicineForm.LIQUID,
            "Pulver till oral suspension" to MedicineForm.POWDER,
            "Granulat i dospåse" to MedicineForm.POWDER,
            "Inhalationspulver" to MedicineForm.INHALER,
            "Inhalationspulver, hård kapsel" to MedicineForm.INHALER,
            "Inhalationsspray, suspension" to MedicineForm.INHALER,
            "Orala droppar, lösning" to MedicineForm.DROPS,
            "Ögondroppar, lösning i endosbehållare" to MedicineForm.DROPS,
            "Depotplåster" to MedicineForm.PATCH,
            "Pulver till injektionsvätska, lösning" to MedicineForm.OTHER,
            "Injektionsvätska, lösning i förfylld injektionspenna" to MedicineForm.OTHER,
            "Nässpray, lösning" to MedicineForm.OTHER,
            "Kräm" to MedicineForm.OTHER,
            "Suppositorium" to MedicineForm.OTHER,
            // Verkliga former ur Läkemedelsverkets lista 2026-10-04, där ordningen avgör.
            "Vaginaltablett" to MedicineForm.TABLET,
            "Granulat till orala droppar, suspension" to MedicineForm.DROPS,
            "Pulver till oral lösning i dospåse" to MedicineForm.POWDER,
            "Injektionsvätska, suspension, förfylld spruta" to MedicineForm.OTHER,
            "Ögondroppar, suspension" to MedicineForm.DROPS,
            "Medicinskt plåster" to MedicineForm.PATCH,
            "Medicinsk gas, komprimerad" to MedicineForm.OTHER,
            "Något helt nytt" to MedicineForm.OTHER
        )
        assertEquals(expected, expected.mapValues { (text, _) -> MedicineForms.of(text) })
    }

    @Test
    fun `varje form har en enhet`() {
        assertEquals(
            listOf(ItemUnit.TABLETT, ItemUnit.KAPSEL, ItemUnit.ML, ItemUnit.DOS, ItemUnit.PUFF, ItemUnit.ML, ItemUnit.ST, ItemUnit.ST),
            MedicineForm.entries.map { it.defaultUnit }
        )
    }
}

/** Namnfältets förslag, "ingen träff" och "ifyllt" (REC-6, REC-7). */
class MedicineLookupTest {
    private val catalog = MedicineCatalog.parse("Alvedon\t500 mg\tFilmdragerad tablett\nAlvedon\t60 mg\tSuppositorium")
    private val alvedon = catalog.entries.first()
    private val new = Regimen("r1", form = MedicineForm.TABLET, unit = ItemUnit.TABLETT)

    @Test
    fun `förslag medan man skriver, ingen träff först efter tre tecken`() {
        assertEquals(MedicineLookup(), catalog.lookup(new.copy(name = "Al"), null))
        assertEquals(2, catalog.lookup(new.copy(name = "alv"), null).suggestions.size)
        assertEquals(MedicineLookup(noMatch = true), catalog.lookup(new.copy(name = "Magnecyl"), null))
        assertEquals(MedicineLookup(), MedicineCatalog(null, emptyList()).lookup(new.copy(name = "Magnecyl"), null))
    }

    @Test
    fun `efter ett val inga förslag och ifyllt tills ett ifyllt fält ändras`() {
        val filled = new.fillFrom(alvedon)
        assertEquals(MedicineLookup(filled = true), catalog.lookup(filled, alvedon))
        assertEquals(MedicineLookup(filled = true), catalog.lookup(filled.copy(packageSize = 20, notes = "Mot feber"), alvedon))
        assertEquals(MedicineLookup(), catalog.lookup(filled.copy(strength = "1 g"), alvedon))
        assertEquals(MedicineLookup(), catalog.lookup(filled.copy(unit = ItemUnit.DOS), alvedon))
        // Ett ändrat namn söker igen.
        assertEquals(2, catalog.lookup(filled.copy(name = "Alvedo"), alvedon).suggestions.size)
    }
}
