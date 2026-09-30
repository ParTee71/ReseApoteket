package se.partee71.reseapoteket.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import se.partee71.reseapoteket.ui.theme.AppShapes
import se.partee71.reseapoteket.ui.theme.AppTypography

enum class AvatarSize(internal val size: Dp) { Regular(40.dp), Small(28.dp) }

/**
 * Personens emoji i en cirkel i personens färg (`AppColors.person`). Dekorativ – namnet står
 * alltid bredvid, så TalkBack hoppar över den.
 */
@Composable
fun PersonAvatar(emoji: String, color: Color, modifier: Modifier = Modifier, size: AvatarSize = AvatarSize.Regular) {
    Box(
        modifier.size(size.size).background(color, AppShapes.pill).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, style = if (size == AvatarSize.Regular) AppTypography.sectionTitle else AppTypography.pill)
    }
}
