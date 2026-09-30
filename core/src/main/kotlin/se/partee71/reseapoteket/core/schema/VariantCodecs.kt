package se.partee71.reseapoteket.core.schema

import se.partee71.reseapoteket.core.model.DoseSchedule
import se.partee71.reseapoteket.core.model.QuantityRule

/**
 * `schedule` i ett recept. Varje variant skriver alla varianters fält, med `null` för det som
 * inte gäller, så att byte av typ inte lämnar gamla värden kvar vid merge. [DoseSchedule.Unknown]
 * skrivs tillbaka exakt som den lästes.
 */
object DoseScheduleCodec : ValueCodec<DoseSchedule> {
    override fun encode(value: DoseSchedule): Any? = when (value) {
        is DoseSchedule.Unknown -> value.raw
        is DoseSchedule.Daily -> fields("DAILY", dosesPerDay = value.dosesPerDay, unitsPerDose = value.unitsPerDose)
        is DoseSchedule.Weekdays -> fields("WEEKDAYS", unitsPerDose = value.unitsPerDose, weekdays = value.days.encodeWeekdays())
        is DoseSchedule.IntervalDays -> fields("INTERVAL", unitsPerDose = value.unitsPerDose, intervalDays = value.intervalDays)
        is DoseSchedule.AsNeeded -> fields("AS_NEEDED", asNeededQuantity = value.quantity)
    }

    override fun decode(raw: Any?): DoseSchedule {
        if (raw !is Map<*, *>) return DoseSchedule.Unknown(raw)
        val map = asDoc(raw)
        return when (map.string("type")) {
            "DAILY" -> DoseSchedule.Daily(map.int("dosesPerDay", 1), map.int("unitsPerDose", 1))
            "WEEKDAYS" -> DoseSchedule.Weekdays(map.weekdays("weekdays"), map.int("unitsPerDose", 1))
            "INTERVAL" -> DoseSchedule.IntervalDays(map.int("intervalDays", 1), map.int("unitsPerDose", 1))
            "AS_NEEDED" -> DoseSchedule.AsNeeded(map.int("asNeededQuantity"))
            else -> DoseSchedule.Unknown(raw)
        }
    }

    private fun fields(
        type: String,
        dosesPerDay: Int? = null,
        unitsPerDose: Int? = null,
        weekdays: List<Int>? = null,
        intervalDays: Int? = null,
        asNeededQuantity: Int? = null,
    ): Doc = mapOf(
        "type" to type,
        "dosesPerDay" to dosesPerDay,
        "unitsPerDose" to unitsPerDose,
        "weekdays" to weekdays,
        "intervalDays" to intervalDays,
        "asNeededQuantity" to asNeededQuantity,
    )
}

/** `rule` i en familjeartikel – samma principer som [DoseScheduleCodec]. */
object QuantityRuleCodec : ValueCodec<QuantityRule> {
    override fun encode(value: QuantityRule): Any? = when (value) {
        is QuantityRule.Unknown -> value.raw
        is QuantityRule.Fixed -> fields("FIXED", value.quantity)
        is QuantityRule.PerTraveller -> fields("PER_TRAVELLER", value.quantity)
        is QuantityRule.PerDays -> fields("PER_DAYS", value.quantity, perDays = value.perDays)
    }

    override fun decode(raw: Any?): QuantityRule {
        if (raw !is Map<*, *>) return QuantityRule.Unknown(raw)
        val map = asDoc(raw)
        return when (map.string("type")) {
            "FIXED" -> QuantityRule.Fixed(map.int("quantity", 1))
            "PER_TRAVELLER" -> QuantityRule.PerTraveller(map.int("quantity", 1))
            "PER_DAYS" -> QuantityRule.PerDays(map.int("quantity", 1), map.int("perDays", 7))
            else -> QuantityRule.Unknown(raw)
        }
    }

    private fun fields(type: String, quantity: Int, perDays: Int? = null): Doc =
        mapOf("type" to type, "quantity" to quantity, "perDays" to perDays)
}
