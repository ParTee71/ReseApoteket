package se.partee71.reseapoteket.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import se.partee71.reseapoteket.R

/**
 * Exempel att börja från – snabbval i ett formulär (KOS-3) och exempel i en tom lista (FAM-4):
 * chips med plus; [chosen] markeras med bock när det redan är valt.
 */
@Composable
fun ExampleChips(options: List<String>, onPick: (String) -> Unit, modifier: Modifier = Modifier, chosen: String? = null) {
    ChipRow(modifier) {
        options.forEach { option -> AppFilterChip(option, selected = option == chosen, onClick = { onPick(option) }, icon = R.drawable.ic_add) }
    }
}
