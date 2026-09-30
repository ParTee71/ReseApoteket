package se.partee71.reseapoteket.core.engine

import se.partee71.reseapoteket.core.model.DoseSchedule
import se.partee71.reseapoteket.core.model.FamilyItem
import se.partee71.reseapoteket.core.model.HouseholdSettings
import se.partee71.reseapoteket.core.model.ItemUnit
import se.partee71.reseapoteket.core.model.QuantityRule
import se.partee71.reseapoteket.core.model.Regimen
import se.partee71.reseapoteket.core.model.Trip

/**
 * Förklaringen till en mängd (BER-10), t.ex.
 * "2 × 1 tablett/dag × (7 + 2) dagar = 18 tabletter → 2 kartor à 10".
 * Samma text i packlistan och i förhandsvisningarna; räknar aldrig själv – mängden skickas in.
 */
object ReasonFormatter {
    const val CANNOT_CALCULATE = "kan inte beräknas – uppdatera appen"
    const val NO_START_DATE = "startdatum saknas – kan inte beräknas"
    const val AS_NEEDED_MISSING = "vid behov – ange hur många som ska med"

    /** "(7 + 2) dagar", eller "7 dagar" utan marginal. */
    fun period(trip: Trip, settings: HouseholdSettings): String =
        if (settings.marginDays > 0) "(${trip.days} + ${settings.marginDays}) dagar" else UnitNames.days(trip.days)

    fun regimen(regimen: Regimen, trip: Trip, settings: HouseholdSettings, quantity: Int?, packages: Int?): String {
        val unit = regimen.unit
        val schedule = regimen.schedule
        val total = quantity?.let { " = ${UnitNames.quantity(it, unit)}" }.orEmpty()
        val base = when (schedule) {
            is DoseSchedule.Daily ->
                "${schedule.dosesPerDay} × ${UnitNames.quantity(schedule.unitsPerDose, unit)}/dag × ${period(trip, settings)}$total"
            is DoseSchedule.Weekdays -> {
                val matching = PackingEngine.weekdayCount(schedule, trip, PackingEngine.effectiveDays(trip, settings))
                    ?: return NO_START_DATE
                "${UnitNames.quantity(schedule.unitsPerDose, unit)} × ${UnitNames.days(matching)} " +
                    "(${DoseScheduleFormatter.days(schedule.days)}) under ${period(trip, settings)}$total"
            }
            is DoseSchedule.IntervalDays ->
                "${UnitNames.quantity(schedule.unitsPerDose, unit)} ${DoseScheduleFormatter.every(schedule.intervalDays)} " +
                    "under ${period(trip, settings)}$total"
            is DoseSchedule.AsNeeded ->
                if (schedule.quantity > 0) "vid behov: ${UnitNames.quantity(schedule.quantity, unit)}" else AS_NEEDED_MISSING
            is DoseSchedule.Unknown -> return CANNOT_CALCULATE
        }
        val size = regimen.packageSize
        return if (packages != null && size != null) "$base → ${UnitNames.packages(packages, unit)} à $size" else base
    }

    fun family(item: FamilyItem, travellers: Int, trip: Trip, settings: HouseholdSettings, quantity: Int?): String {
        val unit = item.unit
        val total = quantity?.let { UnitNames.quantity(it, unit) }.orEmpty()
        return when (val rule = item.rule) {
            is QuantityRule.Fixed -> "fast antal: $total"
            is QuantityRule.PerTraveller -> "${amount(rule.quantity, unit)} × ${UnitNames.travellers(travellers)} = $total"
            is QuantityRule.PerDays ->
                "${amount(rule.quantity, unit)} per ${UnitNames.days(rule.perDays)} under ${period(trip, settings)} = $total"
            is QuantityRule.Unknown -> CANNOT_CALCULATE
        }
    }

    private fun amount(quantity: Int, unit: ItemUnit) = UnitNames.quantity(quantity, unit)
}
