@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package se.partee71.reseapoteket.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import se.partee71.reseapoteket.R

/**
 * Kryssrad: en [ItemRow] med ett formmorfande kryss – en ring som fjädrar till en fylld
 * "kaka" med bock – och dämpad "packad"-stil när den är ikryssad (skill `ui-style`).
 * Hela raden är tryckytan; TalkBack läser den som en kryssruta.
 */
@Composable
fun CheckRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    ItemRowLayout(
        title = title,
        modifier = modifier,
        interaction = Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        subtitle = subtitle,
        leading = { MorphingCheck(checked) },
        trailing = trailing,
        tinted = checked,
        done = checked,
    )
}

@Composable
private fun MorphingCheck(checked: Boolean) {
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "kryss",
    )
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val color = MaterialTheme.colorScheme.primary
    Box(Modifier.size(SIZE), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(SIZE)) {
            val path = morph.toPath(progress)
            val stroke = STROKE.toPx()
            val inner = size.minDimension - stroke
            val offset = Offset(stroke / 2, stroke / 2)
            translate(offset.x, offset.y) {
                scale(inner, pivot = Offset.Zero) {
                    if (progress < 1f) drawPath(path, color, alpha = 1f - progress, style = Stroke(width = stroke / inner))
                    if (progress > 0f) drawPath(path, color, alpha = progress)
                }
            }
        }
        Icon(
            painterResource(R.drawable.ic_check),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(ICON).alpha(progress.coerceIn(0f, 1f)),
        )
    }
}

private val SIZE = 40.dp
private val ICON = 22.dp
private val STROKE = 2.5.dp
