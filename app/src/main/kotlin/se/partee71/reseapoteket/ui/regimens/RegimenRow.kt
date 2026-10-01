package se.partee71.reseapoteket.ui.regimens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.engine.DoseScheduleFormatter
import se.partee71.reseapoteket.core.model.Regimen
import se.partee71.reseapoteket.core.model.displayName
import se.partee71.reseapoteket.ui.components.InfoPill
import se.partee71.reseapoteket.ui.components.ItemRow
import se.partee71.reseapoteket.ui.components.kindLabel
import se.partee71.reseapoteket.ui.theme.Tone

/**
 * Ett recept eller kosttillskott i en lista: namn och styrka, doseringen i klartext (REC-3) och
 * doseringstypen som pill. Ett pausat är nedtonat och säger att det inte räknas med (REC-5).
 */
@Composable
fun RegimenRow(regimen: Regimen, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val pill = if (regimen.active) regimen.schedule.kindLabel()?.let { stringResource(it) } else stringResource(R.string.regimen_paused)
    ItemRow(
        title = regimen.displayName,
        modifier = modifier,
        subtitle = if (regimen.active) {
            DoseScheduleFormatter.format(regimen.schedule, regimen.unit)
        } else {
            stringResource(R.string.regimen_paused_note)
        },
        trailing = pill?.let { { InfoPill(it, tone = Tone.Blue) } },
        onClick = onClick,
        navigates = true,
        inactive = !regimen.active,
    )
}
