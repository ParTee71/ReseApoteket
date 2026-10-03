package se.partee71.reseapoteket.core.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import se.partee71.reseapoteket.core.model.DoseSchedule
import se.partee71.reseapoteket.core.model.FamilyItem
import se.partee71.reseapoteket.core.model.HouseholdSettings
import se.partee71.reseapoteket.core.model.ItemUnit
import se.partee71.reseapoteket.core.model.PackingItem
import se.partee71.reseapoteket.core.model.PackingSection
import se.partee71.reseapoteket.core.model.PackingSource
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.core.model.QuantityRule
import se.partee71.reseapoteket.core.model.Regimen
import se.partee71.reseapoteket.core.model.RegimenKind
import se.partee71.reseapoteket.core.model.Trip

/** BER-1–10 och kantfallen i skill packing-engine. */
class PackingEngineTest {

    private val margin2 = HouseholdSettings(marginDays = 2)
    private val noMargin = HouseholdSettings(marginDays = 0)
    private fun trip(days: Int, start: LocalDate? = LocalDate(2026, 12, 20), travellers: List<String> = listOf("anna", "erik")) =
        Trip("t1", "Gran Canaria", start, days, travellers)

    // ---- Formler ----

    @Test
    fun `dagligen - doser gånger enheter gånger dagar med marginal`() {
        assertEquals(18, PackingEngine.regimenQuantity(DoseSchedule.Daily(2, 1), trip(7), margin2))
        assertEquals(0, PackingEngine.regimenQuantity(DoseSchedule.Daily(2, 1), trip(0), noMargin))
        assertEquals(2, PackingEngine.regimenQuantity(DoseSchedule.Daily(2, 1), trip(1), noMargin))
    }

    @Test
    fun `veckodagar räknar datum i resan inklusive marginal`() {
        // 2026-12-20 är en söndag; 9 dagar = sön 20 … mån 28 → mån 21, ons 23, fre 25, mån 28.
        val monWedFri = DoseSchedule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), 1)
        assertEquals(4, PackingEngine.regimenQuantity(monWedFri, trip(7), margin2))
    }

    @Test
    fun `veckodagar över årsskifte och skottdag`() {
        val thursdays = DoseSchedule.Weekdays(setOf(DayOfWeek.THURSDAY), 2)
        // 2026-12-28 (mån) i 11 dagar = 28 dec … 7 jan → tor 31 dec och tor 7 jan.
        assertEquals(4, PackingEngine.regimenQuantity(thursdays, trip(11, LocalDate(2026, 12, 28)), noMargin))
        assertEquals(2, PackingEngine.regimenQuantity(thursdays, trip(10, LocalDate(2026, 12, 28)), noMargin))
        // 2028 är skottår: 2028-02-28 (mån) i 3 dagar → 28, 29 feb, 1 mar; tisdag 29 feb räknas.
        val tuesdays = DoseSchedule.Weekdays(setOf(DayOfWeek.TUESDAY), 1)
        assertEquals(1, PackingEngine.regimenQuantity(tuesdays, trip(3, LocalDate(2028, 2, 28)), noMargin))
    }

    @Test
    fun `veckodagar där inga matchar ger 0, utan startdatum kan det inte räknas ut`() {
        val sundays = DoseSchedule.Weekdays(setOf(DayOfWeek.SUNDAY), 1)
        assertEquals(0, PackingEngine.regimenQuantity(sundays, trip(3, LocalDate(2026, 12, 21)), noMargin))
        assertEquals(0, PackingEngine.regimenQuantity(DoseSchedule.Weekdays(emptySet(), 1), trip(7), margin2))
        assertNull(PackingEngine.regimenQuantity(sundays, trip(7, start = null), margin2))
    }

    @Test
    fun `intervall avrundas uppåt, intervall 1 är varje dag`() {
        assertEquals(3, PackingEngine.regimenQuantity(DoseSchedule.IntervalDays(3, 1), trip(7), margin2)) // ceil(9/3)
        assertEquals(4, PackingEngine.regimenQuantity(DoseSchedule.IntervalDays(3, 1), trip(8), margin2)) // ceil(10/3)
        assertEquals(9, PackingEngine.regimenQuantity(DoseSchedule.IntervalDays(1, 1), trip(7), margin2))
        assertEquals(0, PackingEngine.regimenQuantity(DoseSchedule.IntervalDays(3, 1), trip(0), noMargin))
    }

    @Test
    fun `vid behov är den angivna mängden, okänd dosering kan inte räknas ut`() {
        assertEquals(10, PackingEngine.regimenQuantity(DoseSchedule.AsNeeded(10), trip(7), margin2))
        assertNull(PackingEngine.regimenQuantity(DoseSchedule.Unknown("framtida"), trip(7), margin2))
    }

    @Test
    fun `familjeregler - fast, per resenär, per N dagar`() {
        assertEquals(1, PackingEngine.familyQuantity(QuantityRule.Fixed(1), 4, trip(7), margin2))
        assertEquals(4, PackingEngine.familyQuantity(QuantityRule.PerTraveller(1), 4, trip(7), margin2))
        assertEquals(0, PackingEngine.familyQuantity(QuantityRule.PerTraveller(1), 0, trip(7), margin2))
        assertEquals(2, PackingEngine.familyQuantity(QuantityRule.PerDays(1, 7), 4, trip(10), margin2)) // ceil(12/7)
        assertEquals(1, PackingEngine.familyQuantity(QuantityRule.PerDays(1, 7), 4, trip(5), margin2)) // ceil(7/7)
        assertNull(PackingEngine.familyQuantity(QuantityRule.Unknown(), 4, trip(7), margin2))
    }

    @Test
    fun `förpackningar avrundas uppåt när förpackningsstorleken inte går jämnt upp`() {
        val levaxin = Regimen("r", name = "Levaxin", unit = ItemUnit.TABLETT, packageSize = 10, schedule = DoseSchedule.Daily(2, 1))
        val line = PackingEngine.regimenLine(levaxin, trip(7), margin2)
        assertEquals(18, line.quantity)
        assertEquals(2, line.packages)
        assertEquals(null, PackingEngine.regimenLine(levaxin.copy(packageSize = null), trip(7), margin2).packages)
    }

    @Test
    fun `sammanfattningen i reseformuläret räknar resenärer, saker och sektioner (RES-3)`() {
        val anna = Person("anna", "Anna")
        val lova = Person("lova", "Lova", archived = true)
        val regimens = mapOf(
            "anna" to listOf(
                Regimen("r1", name = "Levaxin", schedule = DoseSchedule.Daily(1, 1)),
                Regimen("r2", name = "Alvedon", schedule = DoseSchedule.AsNeeded(10)),
                Regimen("s1", RegimenKind.SUPPLEMENT, "D-vitamin", schedule = DoseSchedule.Daily(1, 1)),
            ),
        )
        val family = listOf(FamilyItem("f1", "Solkräm", rule = QuantityRule.Fixed(1)))
        val preview = PackingEngine.tripPreview(trip(7, travellers = listOf("anna", "lova")), listOf(anna, lova), regimens, family, margin2)
        assertEquals(PackingPreviewText("För 1 resenär i 7 dagar", "4 saker att packa", "2 recept · 1 kosttillskott · 1 familjeartikel"), preview)
        val empty = PackingEngine.tripPreview(trip(3, travellers = emptyList()), emptyList(), emptyMap(), emptyList(), margin2)
        assertEquals(PackingPreviewText("För 0 resenärer i 3 dagar", "0 saker att packa", null), empty)
    }

    // ---- Urval och sortering ----

    private val anna = Person("anna", "Anna", sortOrder = 0)
    private val erik = Person("erik", "Erik", sortOrder = 1)
    private val lova = Person("lova", "Lova", sortOrder = 2, archived = true)
    private val alvedon = Regimen("alvedon", name = "Alvedon", strength = "500 mg", schedule = DoseSchedule.AsNeeded(10))

    @Test
    fun `urval - bara resenärer, aktiva och icke arkiverade, och samma namn hos två personer ger två rader`() {
        val suggestion = PackingEngine.suggest(
            trip(7, travellers = listOf("anna", "erik", "lova")),
            listOf(erik, anna, lova, Person("bertil", "Bertil")),
            mapOf(
                "anna" to listOf(alvedon, Regimen("inaktiv", name = "Inaktiv", active = false, schedule = DoseSchedule.Daily())),
                "erik" to listOf(alvedon, Regimen("arkiv", name = "Arkiverad", archived = true, schedule = DoseSchedule.Daily())),
                "lova" to listOf(alvedon),
                "bertil" to listOf(alvedon),
            ),
            listOf(
                FamilyItem("sol", "Solkräm", unit = ItemUnit.TUB, rule = QuantityRule.PerDays(1, 7)),
                FamilyItem("gammal", "Gammal", rule = QuantityRule.Fixed(1), archived = true),
                FamilyItem("av", "Avstängd", rule = QuantityRule.Fixed(1), active = false),
            ),
            margin2,
        )
        assertEquals(listOf("regimen-anna-alvedon", "regimen-erik-alvedon", "family-sol"), suggestion.items.map { it.id })
        assertEquals(listOf("Alvedon 500 mg", "Alvedon 500 mg", "Solkräm"), suggestion.items.map { it.name })
        assertEquals(listOf(0, 1, 2), suggestion.items.map { it.sortOrder })
    }

    @Test
    fun `person utan recept ger inga rader, och per resenär räknar bara aktiva resenärer`() {
        val suggestion = PackingEngine.suggest(
            trip(7, travellers = listOf("anna", "lova")),
            listOf(anna, lova),
            emptyMap(),
            listOf(FamilyItem("myg", "Myggmedel", rule = QuantityRule.PerTraveller(1))),
            margin2,
        )
        assertEquals(1, suggestion.items.single().quantity)
    }

    @Test
    fun `sortering - Mediciner, Kosttillskott, Övrigt, sedan person och namn`() {
        val dvitamin = Regimen("d", kind = RegimenKind.SUPPLEMENT, name = "D-vitamin", schedule = DoseSchedule.Daily())
        val zyrtec = Regimen("z", name = "Zyrtec", schedule = DoseSchedule.Daily())
        val suggestion = PackingEngine.suggest(
            trip(7),
            listOf(erik, anna),
            mapOf("anna" to listOf(zyrtec, dvitamin, alvedon), "erik" to listOf(dvitamin, zyrtec)),
            listOf(FamilyItem("b", "B-plåster", sortOrder = 1, rule = QuantityRule.Fixed(1)), FamilyItem("a", "Termometer", sortOrder = 0, rule = QuantityRule.Fixed(1))),
            margin2,
        )
        assertEquals(
            listOf(
                PackingSection.MEDICINE to "anna/alvedon", PackingSection.MEDICINE to "anna/z", PackingSection.MEDICINE to "erik/z",
                PackingSection.SUPPLEMENT to "anna/d", PackingSection.SUPPLEMENT to "erik/d",
                PackingSection.FAMILY to "null/a", PackingSection.FAMILY to "null/b",
            ),
            suggestion.items.map { it.section to "${it.personId}/${it.sourceId}" },
        )
    }

    /** Svensk ordning via java.text.Collator – kräver JDK:ns svenska lokaldata (finns i vanliga JDK). */
    @Test
    fun `namn sorteras i svensk ordning - å, ä, ö sist`() {
        val names = listOf("Ölkorv", "Äppelcider", "Åkomma", "Zink", "abc").map { Regimen(it, name = it, schedule = DoseSchedule.Daily()) }
        val suggestion = PackingEngine.suggest(trip(7), listOf(anna), mapOf("anna" to names), emptyList(), margin2)
        assertEquals(listOf("abc", "Zink", "Åkomma", "Äppelcider", "Ölkorv"), suggestion.items.map { it.name })
    }

    @Test
    fun `vid behov utan mängd och okänd dosering ger 0 och en varning`() {
        val suggestion = PackingEngine.suggest(
            trip(7),
            listOf(anna),
            mapOf("anna" to listOf(Regimen("vb", name = "Nässpray", schedule = DoseSchedule.AsNeeded(0)), Regimen("u", name = "Ny typ"))),
            listOf(FamilyItem("x", "Okänd regel")),
            margin2,
        )
        assertTrue(suggestion.items.all { it.quantity == 0 })
        assertEquals(listOf("Ny typ", "Nässpray", "Okänd regel"), suggestion.warnings) // svensk ordning: y före ä
    }

    // ---- merge ----

    private fun row(id: String, source: PackingSource, sourceId: String?, personId: String?, suggested: Int, quantity: Int = suggested) =
        PackingItem(id, sourceType = source, sourceId = sourceId, personId = personId, name = id, suggestedQuantity = suggested, quantity = quantity)

    @Test
    fun `merge - nytt förslag, manuellt ändrad mängd, avprickning och dolt behålls`() {
        val checkedAt = Instant.fromEpochSeconds(1_790_000_000)
        val existing = listOf(
            row("gammal-1", PackingSource.REGIMEN, "levaxin", "anna", suggested = 9),
            row("gammal-2", PackingSource.REGIMEN, "zyrtec", "anna", suggested = 9, quantity = 12)
                .copy(checked = true, checkedAt = checkedAt, hidden = true),
        )
        val suggestion = PackingSuggestion(
            listOf(
                row("regimen-anna-levaxin", PackingSource.REGIMEN, "levaxin", "anna", suggested = 11),
                row("regimen-anna-zyrtec", PackingSource.REGIMEN, "zyrtec", "anna", suggested = 11),
                row("regimen-erik-ny", PackingSource.REGIMEN, "ny", "erik", suggested = 5),
            ),
            emptyList(),
        )
        val merged = PackingEngine.merge(existing, suggestion).associateBy { it.sourceId }

        assertEquals(Triple("gammal-1", 11, 11), merged.getValue("levaxin").let { Triple(it.id, it.suggestedQuantity, it.quantity) })
        val zyrtec = merged.getValue("zyrtec")
        assertEquals(Triple(11, 12, true), Triple(zyrtec.suggestedQuantity, zyrtec.quantity, zyrtec.checked))
        assertEquals(checkedAt, zyrtec.checkedAt)
        assertTrue(zyrtec.hidden)
        assertEquals("regimen-erik-ny", merged.getValue("ny").id)
    }

    @Test
    fun `merge - borttagen rad försvinner, avprickad behålls märkt, egna rader rörs aldrig`() {
        val manual = row("egen", PackingSource.MANUAL, null, null, suggested = 0, quantity = 2).copy(name = "Laddare")
        val existing = listOf(
            row("borta", PackingSource.REGIMEN, "a", "lova", suggested = 3),
            row("packad", PackingSource.REGIMEN, "b", "lova", suggested = 3).copy(checked = true),
            manual,
        )
        val merged = PackingEngine.merge(existing, PackingSuggestion(emptyList(), emptyList()))
        assertEquals(listOf("packad", "egen"), merged.map { it.id })
        assertEquals(Texts.NO_LONGER_SUGGESTED, merged.first().reason)
        assertEquals(manual.copy(sortOrder = 1), merged.last())
    }

    @Test
    fun `merge - samma recept hos två personer och familjerader matchas var för sig`() {
        val existing = listOf(
            row("a", PackingSource.REGIMEN, "alvedon", "anna", suggested = 10, quantity = 5),
            row("e", PackingSource.REGIMEN, "alvedon", "erik", suggested = 10).copy(checked = true),
            row("f", PackingSource.FAMILY_ITEM, "sol", null, suggested = 1, quantity = 3),
        )
        val suggestion = PackingSuggestion(
            listOf(
                row("regimen-anna-alvedon", PackingSource.REGIMEN, "alvedon", "anna", suggested = 12),
                row("regimen-erik-alvedon", PackingSource.REGIMEN, "alvedon", "erik", suggested = 12),
                row("family-sol", PackingSource.FAMILY_ITEM, "sol", null, suggested = 2),
            ),
            emptyList(),
        )
        val merged = PackingEngine.merge(existing, suggestion)
        assertEquals(listOf("a" to 5, "e" to 12, "f" to 3), merged.map { it.id to it.quantity })
        assertEquals(listOf(false, true, false), merged.map { it.checked })
    }

    @Test
    fun `merge - entydig ordning, dolt behålls på avprickad rad och dubbletter tappas inte tyst`() {
        val existing = listOf(
            row("egen-2", PackingSource.MANUAL, null, null, suggested = 0).copy(sortOrder = 9),
            row("egen-1", PackingSource.MANUAL, null, null, suggested = 0).copy(sortOrder = 3),
            row("gammal", PackingSource.REGIMEN, "borta", "anna", suggested = 1).copy(checked = true, hidden = true, sortOrder = 0),
            row("dubblett-1", PackingSource.REGIMEN, "levaxin", "anna", suggested = 9),
            row("dubblett-2", PackingSource.REGIMEN, "levaxin", "anna", suggested = 9).copy(checked = true),
        )
        val suggestion = PackingSuggestion(listOf(row("regimen-anna-levaxin", PackingSource.REGIMEN, "levaxin", "anna", suggested = 9)), emptyList())
        val merged = PackingEngine.merge(existing, suggestion)
        assertEquals(listOf("dubblett-1", "gammal", "dubblett-2", "egen-1", "egen-2"), merged.map { it.id })
        assertEquals(listOf(0, 1, 2, 3, 4), merged.map { it.sortOrder })
        assertTrue(merged[1].hidden)
    }

    @Test
    fun `merge - manuellt ändrad mängd behåller sina förpackningar, annars följer de förslaget`() {
        val existing = listOf(
            row("m", PackingSource.REGIMEN, "a", "anna", suggested = 9, quantity = 25).copy(packages = 3),
            row("f", PackingSource.REGIMEN, "b", "anna", suggested = 9).copy(packages = 1),
        )
        val suggestion = PackingSuggestion(
            listOf(
                row("regimen-anna-a", PackingSource.REGIMEN, "a", "anna", suggested = 11).copy(packages = 2),
                row("regimen-anna-b", PackingSource.REGIMEN, "b", "anna", suggested = 11).copy(packages = 2),
            ),
            emptyList(),
        )
        assertEquals(listOf(3, 2), PackingEngine.merge(existing, suggestion).map { it.packages })
    }

    @Test
    fun `förslagets rader har enhet, förpackningar och förklaring, och veckodagar utan startdatum varnar`() {
        val levaxin = Regimen("lev", name = "Levaxin", strength = "50 µg", unit = ItemUnit.TABLETT, packageSize = 10, schedule = DoseSchedule.Daily(2, 1))
        val weekly = Regimen("w", name = "Veckotablett", schedule = DoseSchedule.Weekdays(setOf(DayOfWeek.MONDAY), 1))
        val suggestion = PackingEngine.suggest(trip(7, start = null), listOf(anna), mapOf("anna" to listOf(levaxin, weekly)), emptyList(), margin2)
        val item = suggestion.items.first()
        assertEquals(Triple(ItemUnit.TABLETT, 2, "Levaxin 50 µg"), Triple(item.unit, item.packages, item.name))
        assertEquals("2 × 1 tablett/dag × (7 + 2) dagar = 18 tabletter → 2 kartor à 10", item.reason)
        assertEquals(listOf("Veckotablett"), suggestion.warnings)
    }

    @Test
    fun `skydd mot noll och negativa värden`() {
        assertEquals(0, PackingEngine.effectiveDays(trip(-5), noMargin))
        assertEquals(9, PackingEngine.regimenQuantity(DoseSchedule.IntervalDays(0, 1), trip(7), margin2))
        assertEquals(9, PackingEngine.familyQuantity(QuantityRule.PerDays(1, 0), 1, trip(7), margin2))
        assertEquals(0, PackingEngine.familyQuantity(QuantityRule.PerDays(1, 7), 1, trip(0), noMargin))
        val monday = DoseSchedule.Weekdays(setOf(DayOfWeek.MONDAY), 1)
        assertEquals(0, PackingEngine.regimenQuantity(monday, trip(0, LocalDate(2026, 12, 21)), noMargin))
        assertEquals(1, PackingEngine.regimenQuantity(monday, trip(1, LocalDate(2026, 12, 21)), noMargin))
    }

    @Test
    fun `orimliga värden från databasen ger en begränsad mängd, aldrig överlopp eller häng`() {
        val huge = trip(Int.MAX_VALUE, LocalDate(2026, 1, 1))
        val max = HouseholdSettings(marginDays = Int.MAX_VALUE)
        assertEquals(2 * PackingEngine.MAX_DAYS, PackingEngine.effectiveDays(huge, max))
        assertEquals(PackingEngine.MAX_QUANTITY, PackingEngine.regimenQuantity(DoseSchedule.Daily(Int.MAX_VALUE, Int.MAX_VALUE), huge, max))
        // 732 dagar från torsdag 1 jan 2026: första måndagen är dag 4, sedan var 7:e dag → 104 måndagar.
        assertEquals(104, PackingEngine.regimenQuantity(DoseSchedule.Weekdays(setOf(DayOfWeek.MONDAY), 1), huge, max))
        assertEquals(1, PackingEngine.familyQuantity(QuantityRule.PerDays(1, Int.MAX_VALUE), 1, trip(7), margin2))
        assertEquals(0, PackingEngine.regimenQuantity(DoseSchedule.Daily(-2, 1), trip(7), margin2))
        assertEquals(0, PackingEngine.regimenQuantity(DoseSchedule.AsNeeded(-5), trip(7), margin2))
        assertEquals(0, PackingEngine.familyQuantity(QuantityRule.PerTraveller(3), -1, trip(7), margin2))
        val line = PackingEngine.regimenLine(Regimen("r", packageSize = 0, schedule = DoseSchedule.Daily(1, 1)), trip(7), margin2)
        assertNull(line.packages, "förpackningsstorlek 0 ger inga förpackningar")
    }

    @Test
    fun `ordningen är densamma oavsett ordningen in – även när sortOrder är lika`() {
        val bo = Person("bo", "Bo", sortOrder = 0)
        val al = Person("al", "Al", sortOrder = 0)
        val t = trip(7, travellers = listOf("bo", "al"))
        val regimens = mapOf(
            "bo" to listOf(Regimen("r2", name = "Alvedon", strength = "500 mg"), Regimen("r1", name = "Alvedon", strength = "250 mg")),
            "al" to listOf(Regimen("r3", name = "Levaxin")),
        )
        val items = listOf(FamilyItem("f2", "Plåster"), FamilyItem("f1", "Plåster"))
        val first = PackingEngine.suggest(t, listOf(bo, al), regimens, items, margin2).items
        val second = PackingEngine.suggest(t, listOf(al, bo), regimens.mapValues { it.value.reversed() }, items.reversed(), margin2).items
        assertEquals(first, second)
        assertEquals(listOf("al", "bo", "bo"), first.filter { it.personId != null }.map { it.personId })
        assertEquals(listOf("r1", "r2"), first.filter { it.personId == "bo" }.map { it.sourceId })
        assertEquals(listOf("f1", "f2"), first.filter { it.section == PackingSection.FAMILY }.map { it.sourceId })
    }

    @Test
    fun `räkna om två gånger ger samma lista`() {
        val suggestion = PackingEngine.suggest(trip(7), listOf(anna, erik), mapOf("anna" to listOf(Regimen("lev", name = "Levaxin", schedule = DoseSchedule.Daily(1, 1)))), emptyList(), margin2)
        val once = PackingEngine.merge(emptyList(), suggestion)
        assertEquals(once, PackingEngine.merge(once, suggestion))
    }

    @Test
    fun `packat räknar avprickade bland synliga rader – dolda räknas inte (RES-1, PAK-4)`() {
        val items = listOf(
            PackingItem("a", checked = true),
            PackingItem("b"),
            PackingItem("c", checked = true, hidden = true),
            PackingItem("d", hidden = true),
        )
        assertEquals(PackingProgress(1, 2), PackingEngine.progress(items))
        assertEquals(PackingProgress.NONE, PackingEngine.progress(emptyList()))
        assertEquals(true, PackingProgress(2, 2).isComplete)
        assertEquals(false, PackingProgress(1, 2).isComplete)
        assertEquals(false, PackingProgress.NONE.isComplete, "en tom lista är inte packad")
    }

    @Test
    fun `packas på resedagen följer från recept och familjeartikel, även vid räkna om (PAK-6)`() {
        val insulin = Regimen("ins", name = "Insulin", schedule = DoseSchedule.Daily(1, 1), travelDay = true, travelDayTime = LocalTime(7, 30))
        val toothbrush = FamilyItem("tb", "Tandborste", rule = QuantityRule.PerTraveller(1), travelDay = true)
        val first = PackingEngine.suggest(trip(7), listOf(anna), mapOf("anna" to listOf(insulin)), listOf(toothbrush), margin2)
        val byName = first.items.associateBy { it.name }
        assertEquals(true to LocalTime(7, 30), byName.getValue("Insulin").let { it.travelDay to it.travelDayTime })
        assertEquals(true to null, byName.getValue("Tandborste").let { it.travelDay to it.travelDayTime })
        val checked = first.items.map { it.copy(checked = true) }
        val again = PackingEngine.suggest(trip(7), listOf(anna), mapOf("anna" to listOf(insulin.copy(travelDay = false))), listOf(toothbrush), margin2)
        val merged = PackingEngine.merge(checked, again).associateBy { it.name }
        assertEquals(false, merged.getValue("Insulin").travelDay)
        assertEquals(true, merged.getValue("Insulin").checked)
    }
}
