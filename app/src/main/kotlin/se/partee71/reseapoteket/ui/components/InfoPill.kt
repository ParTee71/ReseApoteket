package se.partee71.reseapoteket.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import se.partee71.reseapoteket.ui.theme.AppColors
import se.partee71.reseapoteket.ui.theme.AppShapes
import se.partee71.reseapoteket.ui.theme.AppTypography
import se.partee71.reseapoteket.ui.theme.Spacing
import se.partee71.reseapoteket.ui.theme.Tone

/** Etikett eller räknare i en pill, i en av sektionstonerna ([Tone]). */
@Composable
fun InfoPill(text: String, modifier: Modifier = Modifier, tone: Tone = Tone.Primary) {
    val colors = AppColors.tone(tone)
    Text(
        text,
        modifier = modifier.background(colors.container, AppShapes.pill).padding(horizontal = Spacing.m, vertical = Spacing.xs),
        style = AppTypography.pill,
        color = colors.content,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
