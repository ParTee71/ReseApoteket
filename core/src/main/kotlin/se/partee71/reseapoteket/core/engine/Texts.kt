package se.partee71.reseapoteket.core.engine

/** Motorns återkommande texter – en formulering per sak (regel 4). */
object Texts {
    /** Dosering eller regel av okänd typ, i listor och redigering (DAT-6). */
    const val UNKNOWN_TYPE = "kan inte visas – uppdatera appen"

    /** Förklaringen när en mängd inte kan räknas ut (DAT-6). */
    const val CANNOT_CALCULATE = "kan inte beräknas – uppdatera appen"

    const val NO_START_DATE = "startdatum saknas – kan inte beräknas"
    const val AS_NEEDED_MISSING = "vid behov – ange hur många som ska med"
    const val NO_WEEKDAYS = "inga veckodagar valda"

    /** Förklaringen på en avprickad rad som inte längre finns i förslaget (RES-4). */
    const val NO_LONGER_SUGGESTED = "Inte längre i förslaget"
}
