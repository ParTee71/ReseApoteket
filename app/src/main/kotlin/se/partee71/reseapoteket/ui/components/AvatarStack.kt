package se.partee71.reseapoteket.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import se.partee71.reseapoteket.ui.theme.AppColors
import se.partee71.reseapoteket.ui.theme.AppShapes

/** En avatar i en [AvatarStack]: personens emoji, färg och namn (för TalkBack). */
data class StackedAvatar(val emoji: String, val color: Color, val name: String)

/**
 * Små [PersonAvatar] som överlappar varandra, med en ring i kortets färg – resenärerna på en resa.
 * TalkBack läser namnen ("Anna, Erik, Lova").
 */
@Composable
fun AvatarStack(avatars: List<StackedAvatar>, modifier: Modifier = Modifier) {
    val ring = AppColors.extended.card
    Row(
        modifier.clearAndSetSemantics { contentDescription = avatars.joinToString(", ") { it.name } },
        horizontalArrangement = Arrangement.spacedBy(-OVERLAP)
    ) {
        avatars.forEach { PersonAvatar(it.emoji, it.color, Modifier.border(RING, ring, AppShapes.pill), size = AvatarSize.Small) }
    }
}

private val OVERLAP = 8.dp
private val RING = 2.dp
