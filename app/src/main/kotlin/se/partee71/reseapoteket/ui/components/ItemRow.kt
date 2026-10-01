package se.partee71.reseapoteket.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.ui.theme.AppColors
import se.partee71.reseapoteket.ui.theme.AppShapes
import se.partee71.reseapoteket.ui.theme.AppTypography
import se.partee71.reseapoteket.ui.theme.Spacing

/**
 * Den enda listraden (skill shared-ui-components): valfri [leading] (avatar, kryss), titel,
 * undertext och [trailing] (pill, pil, växel). [done] ger den dämpade "packad"-stilen,
 * [tinted] den tonade bakgrunden och [accent] en färgad vänsterkant (personens färg).
 * [navigates] visar en pil till höger – raden öppnar en annan skärm.
 */
@Composable
fun ItemRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    tinted: Boolean = false,
    done: Boolean = false,
    accent: Color? = null,
    navigates: Boolean = false,
) {
    val arrow: (@Composable () -> Unit)? = if (navigates) {
        { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    } else {
        null
    }
    ItemRowLayout(
        title = title,
        modifier = modifier,
        interaction = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        subtitle = subtitle,
        leading = leading,
        trailing = trailing ?: arrow,
        tinted = tinted,
        done = done,
        accent = accent,
    )
}

/** Gemensam layout för [ItemRow], `CheckRow` och `SwitchRow` – de skiljer sig bara i [interaction]. */
@Composable
internal fun ItemRowLayout(
    title: String,
    modifier: Modifier,
    interaction: Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    tinted: Boolean = false,
    done: Boolean = false,
    accent: Color? = null,
) {
    val tint = AppColors.extended.rowTint
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = MIN_HEIGHT)
            .clip(AppShapes.row)
            .then(if (tinted) Modifier.background(tint) else Modifier)
            .then(interaction)
            .then(if (accent != null) Modifier.drawBehind { drawAccent(accent) } else Modifier)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val decoration = if (done) TextDecoration.LineThrough else null
            Text(
                title,
                style = AppTypography.itemTitle,
                color = if (done) muted else MaterialTheme.colorScheme.onSurface,
                textDecoration = decoration,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let { Text(it, style = AppTypography.itemSubtitle, color = muted, textDecoration = decoration) }
        }
        trailing?.invoke()
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAccent(color: Color) {
    val width = ACCENT_WIDTH.toPx()
    drawRoundRect(color, size = Size(width, size.height), cornerRadius = CornerRadius(width / 2))
}

private val MIN_HEIGHT = 56.dp
private val ACCENT_WIDTH = 4.dp
