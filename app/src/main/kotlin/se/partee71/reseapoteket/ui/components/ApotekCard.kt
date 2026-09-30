package se.partee71.reseapoteket.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import se.partee71.reseapoteket.ui.theme.AppColors
import se.partee71.reseapoteket.ui.theme.AppShapes
import se.partee71.reseapoteket.ui.theme.Spacing

/** Det enda kortet: en grupperad yta med rader, rubrik eller innehåll (skill `ui-style`). */
@Composable
fun ApotekCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    CardSegment(top = true, bottom = true, modifier, content)
}

/**
 * En bit av ett [ApotekCard] – samma yta, hörn och luft. Lata listor (`EntityListScreen`)
 * bygger ett kort av flera bitar, en per rad, så att varje rad är ett eget listobjekt.
 */
@Composable
internal fun CardSegment(top: Boolean, bottom: Boolean, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(AppColors.extended.card, AppShapes.cardSegment(top, bottom))
            .padding(start = Spacing.m, end = Spacing.m, top = if (top) Spacing.m else HALF_GAP, bottom = if (bottom) Spacing.m else HALF_GAP),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        content = content,
    )
}

/** Halva radavståndet i ett kort ovanför och under en bit, så att bitarna ser ut som ett kort. */
private val HALF_GAP = 2.dp
