package se.partee71.reseapoteket.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import se.partee71.reseapoteket.R

/**
 * Exempel att börja från – snabbval i ett formulär (KOS-3) och exempel i en tom lista (FAM-4):
 * chips med plus; [chosen] markeras med bock när det redan är valt. Varje chip väljer sitt eget
 * [options]-objekt, även när två har samma text ([label]).
 */
@Composable
fun <T> ExampleChips(
    options: List<T>,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
    chosen: T? = null,
    label: (T) -> String = { it.toString() },
) {
    ChipRow(modifier) {
        options.forEach { option -> AppFilterChip(label(option), selected = option == chosen, onClick = { onPick(option) }, icon = R.drawable.ic_add) }
    }
}
