package se.partee71.reseapoteket.core.model

/** Mängdregel för en familjeartikel – underlag för `PackingEngine.familyQuantity`. */
sealed interface QuantityRule {
    data class Fixed(val quantity: Int = 1) : QuantityRule

    data class PerTraveller(val quantity: Int = 1) : QuantityRule

    data class PerDays(val quantity: Int = 1, val perDays: Int = 7) : QuantityRule

    /** Okänd eller saknad typ; [raw] skrivs tillbaka oförändrad (se [DoseSchedule.Unknown]). */
    data class Unknown(val raw: Any? = null) : QuantityRule
}
