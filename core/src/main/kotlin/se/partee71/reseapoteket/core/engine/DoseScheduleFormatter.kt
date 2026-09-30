package se.partee71.reseapoteket.core.engine

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber
import se.partee71.reseapoteket.core.model.DoseSchedule
import se.partee71.reseapoteket.core.model.ItemUnit

/** Doseringen i klartext – samma text i lista, redigering och packlista (REC-3). */
object DoseScheduleFormatter {
    private val dayNames = mapOf(
        DayOfWeek.MONDAY to "mån", DayOfWeek.TUESDAY to "tis", DayOfWeek.WEDNESDAY to "ons",
        DayOfWeek.THURSDAY to "tor", DayOfWeek.FRIDAY to "fre", DayOfWeek.SATURDAY to "lör", DayOfWeek.SUNDAY to "sön",
    )

    /** "mån, ons, fre" – i veckoordning. */
    fun days(days: Set<DayOfWeek>): String =
        if (days.isEmpty()) Texts.NO_WEEKDAYS else days.sortedBy { it.isoDayNumber }.joinToString(", ") { dayNames.getValue(it) }

    fun format(schedule: DoseSchedule, unit: ItemUnit): String = when (schedule) {
        is DoseSchedule.Daily -> "${UnitNames.quantity(schedule.unitsPerDose, unit)} ${timesPerDay(schedule.dosesPerDay)}"
        is DoseSchedule.Weekdays -> "${UnitNames.quantity(schedule.unitsPerDose, unit)} ${days(schedule.days)}"
        is DoseSchedule.IntervalDays -> "${UnitNames.quantity(schedule.unitsPerDose, unit)} ${every(schedule.intervalDays)}"
        is DoseSchedule.AsNeeded ->
            if (schedule.quantity > 0) "vid behov: ta med ${schedule.quantity}" else "vid behov"
        is DoseSchedule.Unknown -> Texts.UNKNOWN_TYPE
    }

    private fun timesPerDay(doses: Int) = when (doses) {
        1 -> "dagligen"
        2 -> "morgon och kväll"
        else -> "$doses gånger om dagen"
    }

    /** "varje dag", "var 2:a dag", "var 3:e dag". */
    fun every(intervalDays: Int): String = if (intervalDays <= 1) "varje dag" else "var ${UnitNames.ordinal(intervalDays)} dag"
}
