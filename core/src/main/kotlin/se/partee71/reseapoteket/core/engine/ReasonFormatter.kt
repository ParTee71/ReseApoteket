package se.partee71.reseapoteket.core.engine

import se.partee71.reseapoteket.core.model.DoseSchedule
import se.partee71.reseapoteket.core.model.FamilyItem
import se.partee71.reseapoteket.core.model.HouseholdSettings
import se.partee71.reseapoteket.core.model.QuantityRule
import se.partee71.reseapoteket.core.model.Regimen
import se.partee71.reseapoteket.core.model.Trip

/**
 * Förklaringen till en mängd (BER-10), t.ex.
 * "2 × 1 tablett/dag × (7 + 2) dagar = 18 tabletter → 2 kartor à 10".
 * Samma text i packlistan och i förhandsvisningarna. Räknar aldrig själv: mängden kommer från
 * [PackingEngine] (`null` = kan inte räknas ut), och regeltexter från [QuantityRuleFormatter].
 */
object ReasonFormatter {

    /** "(7 + 2) dagar", eller "7 dagar" utan marginal. */
    fun period(trip: Trip, settings: HouseholdSettings): String =
        if (settings.marginDays > 0) "(${trip.days} + ${settings.marginDays}) dagar" else UnitNames.days(trip.days)

    fun regimen(regimen: Regimen, trip: Trip, settings: HouseholdSettings, quantity: Int?, packages: Int?): String {
        val unit = regimen.unit
        val total = " = ${UnitNames.quantity(quantity ?: 0, unit)}"
        val base = when (val schedule = regimen.schedule) {
            is DoseSchedule.Daily ->
                "${schedule.dosesPerDay} × ${UnitNames.quantity(schedule.unitsPerDose, unit)}/dag × ${period(trip, settings)}$total"
            is DoseSchedule.Weekdays -> {
                if (quantity == null) return Texts.NO_START_DATE
                val matching = if (schedule.unitsPerDose > 0) quantity / schedule.unitsPerDose else 0
                "${UnitNames.quantity(schedule.unitsPerDose, unit)} × ${UnitNames.days(matching)} " +
                    "(${DoseScheduleFormatter.days(schedule.days)}) under ${period(trip, settings)}$total"
            }
            is DoseSchedule.IntervalDays ->
                "${UnitNames.quantity(schedule.unitsPerDose, unit)} ${DoseScheduleFormatter.every(schedule.intervalDays)} " +
                    "under ${period(trip, settings)}$total"
            is DoseSchedule.AsNeeded ->
                if (schedule.quantity > 0) "vid behov: ${UnitNames.quantity(schedule.quantity, unit)}" else Texts.AS_NEEDED_MISSING
            is DoseSchedule.Unknown -> return Texts.CANNOT_CALCULATE
        }
        val size = regimen.packageSize
        return if (packages != null && size != null) "$base → ${UnitNames.packages(packages, unit)} à $size" else base
    }

    fun family(item: FamilyItem, travellers: Int, trip: Trip, settings: HouseholdSettings, quantity: Int?): String {
        val total = UnitNames.quantity(quantity ?: 0, item.unit)
        return when (val rule = item.rule) {
            is QuantityRule.Fixed -> "fast antal: $total"
            is QuantityRule.PerTraveller -> "${UnitNames.amount(rule.quantity, item.unit)} × ${UnitNames.travellers(travellers)} = $total"
            is QuantityRule.PerDays -> "${QuantityRuleFormatter.format(rule, item.unit)} under ${period(trip, settings)} = $total"
            is QuantityRule.Unknown -> Texts.CANNOT_CALCULATE
        }
    }
}
