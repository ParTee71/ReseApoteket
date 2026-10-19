package se.partee71.reseapoteket.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import se.partee71.reseapoteket.ui.theme.Blue
import se.partee71.ui.theme.AppTheme
import se.partee71.ui.theme.Spacing
import se.partee71.ui.theme.Tone

/**
 * Förhandsvisning av en beräknad mängd i formulären för recept, kosttillskott och
 * familjeartiklar – samma format överallt. Texterna kommer från `ReasonFormatter` i `:core`:
 * [context] ("För en 7-dagarsresa"), [quantity] ("18 tabletter · 2 kartor") och [reason].
 */
@Composable
fun PackingPreview(context: String, quantity: String, modifier: Modifier = Modifier, reason: String? = null) {
    val colors = AppTheme.tone(Tone.Blue)
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.container, AppTheme.shapes.row)
            .padding(horizontal = Spacing.l, vertical = Spacing.m)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(context, style = AppTheme.typography.pill, color = colors.content)
        Text(quantity, style = AppTheme.typography.quantity, color = colors.content)
        reason?.let { Text(it, style = AppTheme.typography.itemSubtitle, color = colors.content) }
    }
}
