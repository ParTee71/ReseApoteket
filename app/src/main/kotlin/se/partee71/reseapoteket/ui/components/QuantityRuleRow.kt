package se.partee71.reseapoteket.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.model.QuantityRule
import se.partee71.ui.components.AppFilterChip
import se.partee71.ui.components.ChipRow
import se.partee71.ui.components.FieldError
import se.partee71.ui.theme.Spacing

/**
 * Mängdregel för en familjeartikel: Fast antal · Per resenär · Per N dagar och dess antal.
 * [unitName] som i [DoseScheduleRow]; en okänd regel visas som meddelande (BCK-5).
 * [error] (från formulärets validering) visas under fälten.
 */
@Composable
fun QuantityRuleRow(
    rule: QuantityRule,
    onChange: (QuantityRule) -> Unit,
    unitName: (Int) -> String,
    modifier: Modifier = Modifier,
    error: String? = null
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        if (rule is QuantityRule.Unknown) {
            UnknownVariant()
            return@Column
        }
        ChipRow {
            RuleKind.entries.forEach { kind ->
                AppFilterChip(stringResource(kind.label), selected = kind.matches(rule), onClick = { onChange(kind.switch(rule)) })
            }
        }
        when (rule) {
            is QuantityRule.Fixed ->
                LabeledStepper(rule.quantity, { onChange(rule.copy(quantity = it)) }, unitName(rule.quantity), 1..999)

            is QuantityRule.PerTraveller ->
                LabeledStepper(
                    rule.quantity,
                    { onChange(rule.copy(quantity = it)) },
                    stringResource(R.string.rule_per_traveller_label, unitName(rule.quantity)),
                    1..999
                )

            is QuantityRule.PerDays -> {
                LabeledStepper(rule.quantity, { onChange(rule.copy(quantity = it)) }, unitName(rule.quantity), 1..999)
                LabeledStepper(
                    rule.perDays,
                    { onChange(rule.copy(perDays = it)) },
                    stringResource(R.string.rule_per_days_label, unitName(1)),
                    1..365
                )
            }

            is QuantityRule.Unknown -> Unit
        }
        error?.let { FieldError(it) }
    }
}

/** Regeltypens namn ("Fast antal", "Per resenär") – samma etikett som i väljaren; `null` för en okänd typ. */
@StringRes
fun QuantityRule.kindLabel(): Int? = RuleKind.entries.firstOrNull { it.matches(this) }?.label

private enum class RuleKind(@param:StringRes val label: Int) {
    FIXED(R.string.rule_fixed),
    PER_TRAVELLER(R.string.rule_per_traveller),
    PER_DAYS(R.string.rule_per_days);

    fun matches(rule: QuantityRule) = when (this) {
        FIXED -> rule is QuantityRule.Fixed
        PER_TRAVELLER -> rule is QuantityRule.PerTraveller
        PER_DAYS -> rule is QuantityRule.PerDays
    }

    /** Byter typ och behåller antalet. */
    fun switch(from: QuantityRule): QuantityRule {
        if (matches(from)) return from
        val quantity = when (from) {
            is QuantityRule.Fixed -> from.quantity
            is QuantityRule.PerTraveller -> from.quantity
            is QuantityRule.PerDays -> from.quantity
            is QuantityRule.Unknown -> 1
        }
        return when (this) {
            FIXED -> QuantityRule.Fixed(quantity)
            PER_TRAVELLER -> QuantityRule.PerTraveller(quantity)
            PER_DAYS -> QuantityRule.PerDays(quantity)
        }
    }
}
