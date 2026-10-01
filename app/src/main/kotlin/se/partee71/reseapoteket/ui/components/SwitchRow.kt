package se.partee71.reseapoteket.ui.components

import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/** Inställningsrad med växel: hela raden är tryckytan, TalkBack läser den som en växel. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    ItemRowLayout(
        title = title,
        modifier = modifier,
        interaction = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        subtitle = subtitle,
        trailing = { Switch(checked = checked, onCheckedChange = null) },
    )
}
