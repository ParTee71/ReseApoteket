package se.partee71.reseapoteket.core.model

import kotlinx.datetime.DayOfWeek

/** Hur ofta och hur mycket – underlag för `PackingEngine.regimenQuantity`. */
sealed interface DoseSchedule {
    data class Daily(val dosesPerDay: Int = 1, val unitsPerDose: Int = 1) : DoseSchedule

    data class Weekdays(val days: Set<DayOfWeek> = emptySet(), val unitsPerDose: Int = 1) : DoseSchedule

    data class IntervalDays(val intervalDays: Int = 1, val unitsPerDose: Int = 1) : DoseSchedule

    data class AsNeeded(val quantity: Int = 0) : DoseSchedule

    /**
     * Okänd eller saknad typ, t.ex. från en nyare appversion. Visas som "kan inte visas –
     * uppdatera appen"; [raw] (fältets råa värde, `null` om det saknades) skrivs tillbaka
     * oförändrat så att ingenting går förlorat.
     */
    data class Unknown(val raw: Any? = null) : DoseSchedule
}
