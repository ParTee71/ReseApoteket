@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package se.partee71.reseapoteket.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.engine.PackingProgress
import se.partee71.reseapoteket.ui.theme.AppColors
import se.partee71.reseapoteket.ui.theme.AppShapes
import se.partee71.reseapoteket.ui.theme.AppTypography
import se.partee71.reseapoteket.ui.theme.Spacing

/**
 * Packprogress: "9 av 14 packat" med vågig progress och resväska. [compact] ger bara vågen och
 * "4 / 10" – för en rad i en lista. Progressen fjädrar när den ändras. När allt är packat visar
 * den stora varianten "Allt packat! 🧳" i grönt (PAK-5); lägg den då i ett grönt `ApotekCard`.
 */
@Composable
fun SuitcaseProgress(packed: Int, total: Int, modifier: Modifier = Modifier, compact: Boolean = false) {
    val target = if (total > 0) packed.toFloat() / total else 0f
    val progress by animateFloatAsState(target, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "packat")
    val text = stringResource(R.string.packed_of_format, total)
    val description = "$packed $text"
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.primaryContainer
    if (compact) {
        Row(
            modifier.clearAndSetSemantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.width(COMPACT_WIDTH), color = primary, trackColor = track)
            Text("$packed / $total", style = AppTypography.pill, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    if (PackingProgress(packed, total).isComplete) {
        AllPacked(total, modifier)
        return
    }
    Row(
        modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(packed.toString(), style = AppTypography.bigNumber, color = primary)
                Text(text, style = AppTypography.itemTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = primary, trackColor = track)
        }
        val tone = AppColors.extended.primaryTone
        Box(Modifier.size(TILE).background(tone.container, AppShapes.row), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_luggage), contentDescription = null, tint = tone.content, modifier = Modifier.size(ICON))
        }
    }
}

/** "Allt packat! 🧳 Trevlig resa" – samma plats och resväska, i grönt. */
@Composable
private fun AllPacked(total: Int, modifier: Modifier) {
    val tone = AppColors.extended.greenTone
    val title = stringResource(R.string.packed_all_title)
    val message = stringResource(R.string.packed_all_message, total)
    Row(
        modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "$title $message" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(title, style = AppTypography.sectionTitle, color = tone.content)
            Text(message, style = AppTypography.itemTitle, color = tone.content)
        }
        Box(Modifier.size(TILE).background(AppColors.extended.card, AppShapes.row), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_luggage), contentDescription = null, tint = tone.content, modifier = Modifier.size(ICON))
        }
    }
}

private val COMPACT_WIDTH = 120.dp
private val TILE = 52.dp
private val ICON = 28.dp
