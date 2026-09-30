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
    }

    @Test
    fun `mängdregeltexter`() {
        assertEquals("1 per resenär", QuantityRuleFormatter.format(QuantityRule.PerTraveller(1), ItemUnit.ST))
        assertEquals("1 tub per 7 dagar", QuantityRuleFormatter.format(QuantityRule.PerDays(1, 7), ItemUnit.TUB))
        assertEquals("2 tuber", QuantityRuleFormatter.format(QuantityRule.Fixed(2), ItemUnit.TUB))
        assertEquals("kan inte visas – uppdatera appen", QuantityRuleFormatter.format(QuantityRule.Unknown(), ItemUnit.ST))
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
        assertEquals(ReasonFormatter.AS_NEEDED_MISSING, reason(DoseSchedule.AsNeeded(0)))
        assertEquals(ReasonFormatter.CANNOT_CALCULATE, reason(DoseSchedule.Unknown()))
        assertEquals(ReasonFormatter.NO_START_DATE, reason(DoseSchedule.Weekdays(setOf(DayOfWeek.MONDAY), 1), t = trip.copy(startDate = null)))
    }

    private fun familyReason(rule: QuantityRule, unit: ItemUnit = ItemUnit.TUB, travellers: Int = 4, t: Trip = trip.copy(days = 10)) =
        PackingEngine.familyLine(FamilyItem("f", "Solkräm", unit = unit, rule = rule), travellers, t, margin2).reason

    @Test
    fun `förklaring för varje mängdregel`() {
        assertEquals("fast antal: 1 st", familyReason(QuantityRule.Fixed(1), ItemUnit.ST))
        assertEquals("1 tub × 4 resenärer = 4 tuber", familyReason(QuantityRule.PerTraveller(1)))
        assertEquals("1 tub × 1 resenär = 1 tub", familyReason(QuantityRule.PerTraveller(1), travellers = 1))
        assertEquals("1 tub per 7 dagar under (10 + 2) dagar = 2 tuber", familyReason(QuantityRule.PerDays(1, 7)))
        assertEquals(ReasonFormatter.CANNOT_CALCULATE, familyReason(QuantityRule.Unknown()))
    }
}
