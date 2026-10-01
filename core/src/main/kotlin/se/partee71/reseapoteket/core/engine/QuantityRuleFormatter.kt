package se.partee71.reseapoteket.core.engine

import se.partee71.reseapoteket.core.model.ItemUnit
import se.partee71.reseapoteket.core.model.QuantityRule

/** Mängdregeln i klartext – samma text i lista, redigering och packlista (FAM-3). */
object QuantityRuleFormatter {
    fun format(rule: QuantityRule, unit: ItemUnit): String = when (rule) {
        is QuantityRule.Fixed -> UnitNames.quantity(rule.quantity, unit)
        is QuantityRule.PerTraveller -> "${UnitNames.amount(rule.quantity, unit)} per resenär"
        is QuantityRule.PerDays -> "${UnitNames.amount(rule.quantity, unit)} ${UnitNames.perDays(rule.perDays)}"
        is QuantityRule.Unknown -> Texts.UNKNOWN_TYPE
    }
}
