package se.partee71.reseapoteket.core.engine

import se.partee71.reseapoteket.core.model.ItemUnit
import se.partee71.reseapoteket.core.model.QuantityRule

/** Mängdregeln i klartext – samma text i lista, redigering och packlista (FAM-3). */
object QuantityRuleFormatter {
    const val UNKNOWN = "kan inte visas – uppdatera appen"

    fun format(rule: QuantityRule, unit: ItemUnit): String = when (rule) {
        is QuantityRule.Fixed -> UnitNames.quantity(rule.quantity, unit)
        is QuantityRule.PerTraveller -> "${amount(rule.quantity, unit)} per resenär"
        is QuantityRule.PerDays -> "${amount(rule.quantity, unit)} per ${UnitNames.days(rule.perDays)}"
        is QuantityRule.Unknown -> UNKNOWN
    }

    /** "1 tub", men "1" för styck – "1 per resenär" läses bättre än "1 st per resenär". */
    private fun amount(quantity: Int, unit: ItemUnit) =
        if (unit == ItemUnit.ST) "$quantity" else UnitNames.quantity(quantity, unit)
}
