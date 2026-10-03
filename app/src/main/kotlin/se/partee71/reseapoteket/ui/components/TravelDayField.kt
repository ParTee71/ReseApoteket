package se.partee71.reseapoteket.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.datetime.LocalTime
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.engine.ReminderKind
import se.partee71.reseapoteket.core.engine.ReminderSettings
import se.partee71.reseapoteket.ui.theme.Spacing

/** Telefonens tid för "Resedagens morgon" (PÅM-5) – sätts av appen; förvalet utan den. */
val LocalTravelDayMorning = compositionLocalOf { ReminderSettings.DEFAULTS.getValue(ReminderKind.TRAVEL_DAY).time }

/**
 * "Packas på resedagen" (PAK-6) i recept, kosttillskott, familjeartiklar och egna rader: växel
 * och, när den är på, tiden påminnelsen kommer – resedagens morgon tills en egen tid väljs.
 */
@Composable
fun TravelDayField(travelDay: Boolean, time: LocalTime?, onChange: (Boolean, LocalTime?) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SwitchRow(
            stringResource(R.string.travel_day),
            travelDay,
            { onChange(it, time) },
            subtitle = stringResource(R.string.travel_day_note),
        )
        if (travelDay) {
            TimeField(
                stringResource(R.string.travel_day_time),
                time ?: LocalTravelDayMorning.current,
                { onChange(true, it) },
                helper = stringResource(if (time == null) R.string.travel_day_time_morning else R.string.travel_day_time_own),
            )
            if (time != null) AppButton(stringResource(R.string.travel_day_use_morning), { onChange(true, null) }, variant = ButtonVariant.Text, compact = true)
        }
    }
}
