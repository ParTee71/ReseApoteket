package se.partee71.reseapoteket.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.ui.theme.Blue
import se.partee71.ui.components.InfoPill
import se.partee71.ui.theme.Tone

/**
 * En rad för något som kan pausas – recept, kosttillskott, familjeartikel: [detail] (dosering
 * eller mängdregel i klartext) och [kind] (typen) som pill. Pausad är raden nedtonad med "Pausat"
 * och att den inte räknas med (REC-5, FAM-2). Öppnar formuläret.
 */
@Composable
fun PausableRow(title: String, detail: String, kind: String?, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val pill = if (active) kind else stringResource(R.string.paused)
    ItemRow(
        title = title,
        modifier = modifier,
        subtitle = if (active) detail else stringResource(R.string.paused_note),
        trailing = pill?.let { { InfoPill(it, tone = Tone.Blue) } },
        onClick = onClick,
        navigates = true,
        inactive = !active
    )
}
