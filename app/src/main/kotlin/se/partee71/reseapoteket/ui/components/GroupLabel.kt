package se.partee71.reseapoteket.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import se.partee71.reseapoteket.ui.theme.AppTypography
import se.partee71.reseapoteket.ui.theme.Spacing

/** Liten etikett över en grupp kort, t.ex. "UTSEENDE" i Inställningar. Läses som rubrik. */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = AppTypography.caption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = Spacing.s).semantics { heading() },
    )
}
