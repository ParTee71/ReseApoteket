package se.partee71.reseapoteket.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import se.partee71.reseapoteket.ui.theme.AppShapes
import se.partee71.reseapoteket.ui.theme.AppTypography

/** Personchip ("Vem åker med?"): avatar och namn, samma färger som [AppFilterChip]. */
@Composable
fun PersonChip(
    name: String,
    emoji: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(name, style = AppTypography.pill) },
        modifier = modifier.heightIn(min = PERSON_CHIP_HEIGHT),
        leadingIcon = { PersonAvatar(emoji, color, size = AvatarSize.Small) },
        shape = AppShapes.pill,
        colors = chipColors()
    )
}

private val PERSON_CHIP_HEIGHT = 40.dp
