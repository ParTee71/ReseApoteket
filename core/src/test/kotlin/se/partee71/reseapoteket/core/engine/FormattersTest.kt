package se.partee71.reseapoteket.core.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import se.partee71.reseapoteket.core.model.DoseSchedule
import se.partee71.reseapoteket.core.model.FamilyItem
import se.partee71.reseapoteket.core.model.HouseholdSettings
import se.partee71.reseapoteket.core.model.ItemUnit
import se.partee71.reseapoteket.core.model.QuantityRule
import se.partee71.reseapoteket.core.model.Regimen
import se.partee71.reseapoteket.core.model.Trip

/** Texterna i REC-3, FAM-3 och BER-10 – en formulering per sak (regel 4). */
class FormattersTest {

    private val trip = Trip("t", "Gran Canaria", LocalDate(2026, 12, 20), days = 7)
    private val margin2 = HouseholdSettings(2)

    @Test
    fun `enhetsnamn böjs, ordningstal på svenska`() {
        assertEquals("1 tablett", UnitNames.quantity(1, ItemUnit.TABLETT))
        assertEquals("18 tabletter", UnitNames.quantity(18, ItemUnit.TABLETT))
        assertEquals("2 kapslar", UnitNames.quantity(2, ItemUnit.KAPSEL))
        assertEquals("0 tuber", UnitNames.quantity(0, ItemUnit.TUB))
        assertEquals("1 karta", UnitNames.packages(1, ItemUnit.TABLETT))
        assertEquals("2 flaskor", UnitNames.packages(2, ItemUnit.ML))
        assertEquals("kapsel", UnitNames.name(1, ItemUnit.KAPSEL))
        assertEquals("puffar", UnitNames.name(4, ItemUnit.PUFF))
        assertEquals(listOf("1:a", "2:a", "3:e", "11:e", "12:e", "21:a", "22:a", "23:e"), listOf(1, 2, 3, 11, 12, 21, 22, 23).map(UnitNames::ordinal))
    }

    @Test
    fun `doseringstexter`() {
        assertEquals("1 tablett morgon och kväll", DoseScheduleFormatter.format(DoseSchedule.Daily(2, 1), ItemUnit.TABLETT))
        assertEquals("2 tabletter dagligen", DoseScheduleFormatter.format(DoseSchedule.Daily(1, 2), ItemUnit.TABLETT))
        assertEquals("1 puff 3 gånger om dagen", DoseScheduleFormatter.format(DoseSchedule.Daily(3, 1), ItemUnit.PUFF))
        val monWedFri = setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
        assertEquals("1 kapsel mån, ons, fre", DoseScheduleFormatter.format(DoseSchedule.Weekdays(monWedFri, 1), ItemUnit.KAPSEL))
        assertEquals("1 dos var 3:e dag", DoseScheduleFormatter.format(DoseSchedule.IntervalDays(3, 1), ItemUnit.DOS))
        assertEquals("1 dos varje dag", DoseScheduleFormatter.format(DoseSchedule.IntervalDays(1, 1), ItemUnit.DOS))
        assertEquals("vid behov: ta med 10", DoseScheduleFormatter.format(DoseSchedule.AsNeeded(10), ItemUnit.TABLETT))
        assertEquals("vid behov", DoseScheduleFormatter.format(DoseSchedule.AsNeeded(0), ItemUnit.TABLETT))
        assertEquals("kan inte visas – uppdatera appen", DoseScheduleFormatter.format(DoseSchedule.Unknown(), ItemUnit.TABLETT))
        assertEquals("1 kapsel – inga veckodagar valda", DoseScheduleFormatter.format(DoseSchedule.Weekdays(emptySet(), 1), ItemUnit.KAPSEL))
    }

    @Test
    fun `mängdregeltexter`() {
        assertEquals("1 per resenär", QuantityRuleFormatter.format(QuantityRule.PerTraveller(1), ItemUnit.ST))
        assertEquals("1 tub per 7 dagar", QuantityRuleFormatter.format(QuantityRule.PerDays(1, 7), ItemUnit.TUB))
        assertEquals("2 tuber", QuantityRuleFormatter.format(QuantityRule.Fixed(2), ItemUnit.TUB))
        assertEquals("kan inte visas – uppdatera appen", QuantityRuleFormatter.format(QuantityRule.Unknown(), ItemUnit.ST))
        assertEquals("1 tub per dag", QuantityRuleFormatter.format(QuantityRule.PerDays(1, 1), ItemUnit.TUB))
    }

    @Test
    fun `period i singular och med marginal`() {
        assertEquals("1 dag", ReasonFormatter.period(trip.copy(days = 1), HouseholdSettings(0)))
        assertEquals("(1 + 2) dagar", ReasonFormatter.period(trip.copy(days = 1), margin2))
    }

    @Test
    fun `förklaringen visar dagarna som motorn räknar dem (BER-1, BER-10)`() {
        assertEquals("(366 + 2) dagar", ReasonFormatter.period(trip.copy(days = 400), margin2))
        assertEquals("(0 + 2) dagar", ReasonFormatter.period(trip.copy(days = -3), margin2))
        assertEquals("7 dagar", ReasonFormatter.period(trip, HouseholdSettings(-1)))
        assertEquals("1 × 1 tablett/dag × (0 + 2) dagar = 2 tabletter", reason(DoseSchedule.Daily(1, 1), t = trip.copy(days = -3)))
    }

    private fun reason(schedule: DoseSchedule, unit: ItemUnit = ItemUnit.TABLETT, packageSize: Int? = null, t: Trip = trip, s: HouseholdSettings = margin2) =
        PackingEngine.regimenLine(Regimen("r", unit = unit, packageSize = packageSize, schedule = schedule), t, s).reason

    @Test
    fun `förklaring för varje doseringstyp`() {
        assertEquals("2 × 1 tablett/dag × (7 + 2) dagar = 18 tabletter → 2 kartor à 10", reason(DoseSchedule.Daily(2, 1), packageSize = 10))
        assertEquals("1 × 1 tablett/dag × 7 dagar = 7 tabletter", reason(DoseSchedule.Daily(1, 1), s = HouseholdSettings(0)))
        assertEquals(
            "1 kapsel × 4 dagar (mån, ons, fre) under (7 + 2) dagar = 4 kapslar",
            reason(DoseSchedule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), 1), ItemUnit.KAPSEL),
        )
        assertEquals("1 dos var 3:e dag under (7 + 2) dagar = 3 doser", reason(DoseSchedule.IntervalDays(3, 1), ItemUnit.DOS))
        assertEquals("vid behov: 10 tabletter", reason(DoseSchedule.AsNeeded(10)))
        assertEquals(Texts.AS_NEEDED_MISSING, reason(DoseSchedule.AsNeeded(0)))
        assertEquals(Texts.CANNOT_CALCULATE, reason(DoseSchedule.Unknown()))
        assertEquals(Texts.NO_START_DATE, reason(DoseSchedule.Weekdays(setOf(DayOfWeek.MONDAY), 1), t = trip.copy(startDate = null)))
    }

    @Test
    fun `veckodagsförklaringen räknar dagar, inte mängd genom antal per dos`() {
        assertEquals(
            "2 kapslar × 4 dagar (mån, ons, fre) under (7 + 2) dagar = 8 kapslar",
            reason(DoseSchedule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), 2), ItemUnit.KAPSEL),
        )
        assertEquals(
            "1 kapsel × 0 dagar (inga veckodagar valda) under (7 + 2) dagar = 0 kapslar",
            reason(DoseSchedule.Weekdays(emptySet(), 1), ItemUnit.KAPSEL),
        )
    }

    @Test
    fun `förhandsvisningens texter`() {
        val line = PackingEngine.regimenLine(Regimen("r", unit = ItemUnit.TABLETT, packageSize = 10, schedule = DoseSchedule.Daily(2, 1)), trip, margin2)
        assertEquals("18 tabletter · 2 kartor", ReasonFormatter.amount(line, ItemUnit.TABLETT))
        assertEquals("1 tub", ReasonFormatter.amount(PackingLine(1, null, ""), ItemUnit.TUB))
        assertEquals("För en 7-dagarsresa", ReasonFormatter.tripContext(7))
        assertEquals("För 4 resenärer i 10 dagar", ReasonFormatter.familyContext(4, 10))
        assertEquals("För 1 resenär i 1 dag", ReasonFormatter.familyContext(1, 1))
    }

    @Test
    fun `förhandsvisningen i receptformuläret räknar en 7-dagarsresa som börjar i dag (REC-4)`() {
        val levaxin = Regimen("r", unit = ItemUnit.TABLETT, packageSize = 10, schedule = DoseSchedule.Daily(2, 1))
        val thursday = LocalDate(2026, 10, 1)
        assertEquals(
            PackingPreviewText("För en 7-dagarsresa", "18 tabletter · 2 kartor", "2 × 1 tablett/dag × (7 + 2) dagar = 18 tabletter → 2 kartor à 10"),
            PackingEngine.regimenPreview(levaxin, margin2, thursday),
        )
        // Tor 1/10 + 9 dagar: fre 2, mån 5, ons 7, fre 9.
        val weekdays = levaxin.copy(packageSize = null, schedule = DoseSchedule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), 1))
        assertEquals("4 tabletter", PackingEngine.regimenPreview(weekdays, margin2, thursday).quantity)
        // Kan mängden inte räknas ut står förklaringen i stället för "0 tabletter".
        assertEquals(
            PackingPreviewText("För en 7-dagarsresa", Texts.AS_NEEDED_MISSING, null),
            PackingEngine.regimenPreview(levaxin.copy(schedule = DoseSchedule.AsNeeded(0)), margin2, thursday),
        )
        assertEquals(Texts.CANNOT_CALCULATE, PackingEngine.regimenPreview(levaxin.copy(schedule = DoseSchedule.Unknown()), margin2, thursday).quantity)
    }

    @Test
    fun `förhandsvisningen för en familjeartikel räknar hushållets personer i 7 dagar (FAM-3)`() {
        val sunscreen = FamilyItem("f", "Solkräm", unit = ItemUnit.TUB, rule = QuantityRule.PerDays(1, 7))
        assertEquals(
            PackingPreviewText("För 4 resenärer i 7 dagar", "2 tuber", "1 tub per 7 dagar under (7 + 2) dagar = 2 tuber"),
            PackingEngine.familyPreview(sunscreen, 4, margin2),
        )
        val plaster = FamilyItem("p", "Plåster", unit = ItemUnit.ST, rule = QuantityRule.PerTraveller(10))
        assertEquals("40 st", PackingEngine.familyPreview(plaster, 4, margin2).quantity)
        assertEquals("0 st", PackingEngine.familyPreview(plaster, 0, margin2).quantity, "utan personer packas inget per resenär")
        assertEquals("För 0 resenärer i 7 dagar", PackingEngine.familyPreview(plaster, 0, margin2).context)
        val thermometer = FamilyItem("t", "Febertermometer", rule = QuantityRule.Fixed(1))
        assertEquals("1 st", PackingEngine.familyPreview(thermometer, 0, margin2).quantity)
        val unknown = PackingEngine.familyPreview(thermometer.copy(rule = QuantityRule.Unknown("framtida")), 4, margin2)
        assertEquals(PackingPreviewText("För 4 resenärer i 7 dagar", Texts.CANNOT_CALCULATE, null), unknown)
    }

    @Test
    fun `varje enhet har namn och förpackning`() {
        for (unit in ItemUnit.entries) {
            assertEquals(true, UnitNames.quantity(2, unit).startsWith("2 "), unit.name)
            assertEquals(true, UnitNames.packages(1, unit).startsWith("1 "), unit.name)
        }
    }

    private fun familyReason(rule: QuantityRule, unit: ItemUnit = ItemUnit.TUB, travellers: Int = 4, t: Trip = trip.copy(days = 10)) =
        PackingEngine.familyLine(FamilyItem("f", "Solkräm", unit = unit, rule = rule), travellers, t, margin2).reason

    @Test
    fun `förklaring för varje mängdregel`() {
        assertEquals("fast antal: 1 st", familyReason(QuantityRule.Fixed(1), ItemUnit.ST))
        assertEquals("1 tub × 4 resenärer = 4 tuber", familyReason(QuantityRule.PerTraveller(1)))
        assertEquals("1 tub × 1 resenär = 1 tub", familyReason(QuantityRule.PerTraveller(1), travellers = 1))
        assertEquals("1 × 4 resenärer = 4 st", familyReason(QuantityRule.PerTraveller(1), ItemUnit.ST))
        assertEquals("1 tub per 7 dagar under (10 + 2) dagar = 2 tuber", familyReason(QuantityRule.PerDays(1, 7)))
        assertEquals(Texts.CANNOT_CALCULATE, familyReason(QuantityRule.Unknown()))
    }

    @Test
    fun `kort dosering för packlistans pill`() {
        assertEquals("1/dag", DoseScheduleFormatter.short(DoseSchedule.Daily(1, 2)))
        assertEquals("mån, fre", DoseScheduleFormatter.short(DoseSchedule.Weekdays(setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY))))
        assertEquals("var 7:e dag", DoseScheduleFormatter.short(DoseSchedule.IntervalDays(7)))
        assertEquals("vid behov", DoseScheduleFormatter.short(DoseSchedule.AsNeeded(10)))
        assertEquals(null, DoseScheduleFormatter.short(DoseSchedule.Unknown("x")))
    }
}
