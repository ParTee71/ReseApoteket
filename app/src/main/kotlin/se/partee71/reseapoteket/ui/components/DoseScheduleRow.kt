package se.partee71.reseapoteket.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import kotlinx.datetime.DayOfWeek
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.model.DoseSchedule
import se.partee71.ui.components.AppFilterChip
import se.partee71.ui.components.AppTextField
import se.partee71.ui.components.ChipRow
import se.partee71.ui.components.FieldError
import se.partee71.ui.components.QuantityStepper
import se.partee71.ui.theme.AppTheme
import se.partee71.ui.theme.Spacing

/**
 * Dosering: typ (Dagligen · Veckodagar · Intervall · Vid behov) och dess antal. [unitName]
 * ger enhetens namn för ett antal ("tablett"/"tabletter") från `UnitNames` i `:core`.
 * En okänd typ från en nyare app visas som meddelande och kan inte ändras (BCK-5).
 * [error] (från formulärets validering) visas under fälten, som i `AppTextField`.
 */
@Composable
fun DoseScheduleRow(
    schedule: DoseSchedule,
    onChange: (DoseSchedule) -> Unit,
    unitName: (Int) -> String,
    modifier: Modifier = Modifier,
    error: String? = null
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        if (schedule is DoseSchedule.Unknown) {
            UnknownVariant()
            return@Column
        }
        ChipRow {
            DOSE_KINDS.forEach { kind ->
                AppFilterChip(stringResource(kind.label), selected = kind.matches(schedule), onClick = { onChange(kind.switch(schedule)) })
            }
        }
        when (schedule) {
            is DoseSchedule.Daily -> {
                LabeledStepper(schedule.dosesPerDay, { onChange(schedule.copy(dosesPerDay = it)) }, stringResource(R.string.dose_doses_per_day), 1..24)
                UnitsPerDose(schedule.unitsPerDose, unitName) { onChange(schedule.copy(unitsPerDose = it)) }
            }

            is DoseSchedule.Weekdays -> {
                ChipRow {
                    DayOfWeek.entries.forEach { day ->
                        AppFilterChip(
                            stringResource(day.shortName()),
                            selected = day in schedule.days,
                            onClick = { onChange(schedule.copy(days = if (day in schedule.days) schedule.days - day else schedule.days + day)) }
                        )
                    }
                }
                UnitsPerDose(schedule.unitsPerDose, unitName) { onChange(schedule.copy(unitsPerDose = it)) }
            }

            is DoseSchedule.IntervalDays -> {
                LabeledStepper(schedule.intervalDays, { onChange(schedule.copy(intervalDays = it)) }, stringResource(R.string.dose_interval_days), 2..365)
                UnitsPerDose(schedule.unitsPerDose, unitName) { onChange(schedule.copy(unitsPerDose = it)) }
            }

            is DoseSchedule.AsNeeded ->
                LabeledStepper(
                    schedule.quantity,
                    { onChange(schedule.copy(quantity = it)) },
                    stringResource(R.string.dose_as_needed_quantity, unitName(schedule.quantity)),
                    1..999
                )

            is DoseSchedule.Unknown -> Unit
        }
        error?.let { FieldError(it) }
    }
}

@Composable
private fun UnitsPerDose(value: Int, unitName: (Int) -> String, onChange: (Int) -> Unit) = LabeledStepper(value, onChange, stringResource(R.string.dose_units_per_dose, unitName(value)), 1..99)

/** En [QuantityStepper] med sin etikett bredvid – delas av [DoseScheduleRow] och [QuantityRuleRow]. */
@Composable
internal fun LabeledStepper(value: Int, onChange: (Int) -> Unit, label: String, range: IntRange) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        QuantityStepper(value, onChange, label, range = range)
        Text(label, style = AppTheme.typography.itemTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Meddelandet för en typ som den här appversionen inte känner till. */
@Composable
internal fun UnknownVariant() {
    Text(stringResource(R.string.unknown_variant), style = AppTheme.typography.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Doseringstypens namn ("Dagligen", "Vid behov") – samma etikett som i väljaren; `null` för en okänd typ. */
@StringRes
fun DoseSchedule.kindLabel(): Int? = DOSE_KINDS.firstOrNull { it.matches(this) }?.label

/**
 * En valbar typ: hur den känns igen och hur man byter till den (antal per dos följer med).
 * Stegarna börjar där en dosering blir giltig (REC-2): intervall från 2 dagar, vid behov från 1.
 */
private enum class DoseKind(@param:StringRes val label: Int) {
    DAILY(R.string.dose_daily),
    WEEKDAYS(R.string.dose_weekdays),
    INTERVAL(R.string.dose_interval),
    AS_NEEDED(R.string.dose_as_needed);

    fun matches(schedule: DoseSchedule) = when (this) {
        DAILY -> schedule is DoseSchedule.Daily
        WEEKDAYS -> schedule is DoseSchedule.Weekdays
        INTERVAL -> schedule is DoseSchedule.IntervalDays
        AS_NEEDED -> schedule is DoseSchedule.AsNeeded
    }

    fun switch(from: DoseSchedule): DoseSchedule {
        if (matches(from)) return from
        val units = when (from) {
            is DoseSchedule.Daily -> from.unitsPerDose
            is DoseSchedule.Weekdays -> from.unitsPerDose
            is DoseSchedule.IntervalDays -> from.unitsPerDose
            else -> 1
        }
        return when (this) {
            DAILY -> DoseSchedule.Daily(unitsPerDose = units)
            WEEKDAYS -> DoseSchedule.Weekdays(unitsPerDose = units)
            INTERVAL -> DoseSchedule.IntervalDays(intervalDays = 2, unitsPerDose = units)
            AS_NEEDED -> DoseSchedule.AsNeeded(quantity = 1)
        }
    }
}

private val DOSE_KINDS = DoseKind.entries

@StringRes
private fun DayOfWeek.shortName(): Int = when (this) {
    DayOfWeek.MONDAY -> R.string.day_mon
    DayOfWeek.TUESDAY -> R.string.day_tue
    DayOfWeek.WEDNESDAY -> R.string.day_wed
    DayOfWeek.THURSDAY -> R.string.day_thu
    DayOfWeek.FRIDAY -> R.string.day_fri
    DayOfWeek.SATURDAY -> R.string.day_sat
    DayOfWeek.SUNDAY -> R.string.day_sun
}
