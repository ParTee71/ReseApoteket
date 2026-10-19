package se.partee71.reseapoteket.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import se.partee71.ui.components.ChoiceGrid
import se.partee71.ui.theme.AppTheme

/** Den kurerade listan för personens avatar (PER-2): djur först, sedan saker som går att känna igen. */
val PERSON_EMOJIS: List<String> = listOf(
    "🦊", "🐻", "🐰", "🦉", "🐯", "🐢", "🦄", "🐧",
    "🐼", "🐨", "🦁", "🐸", "🐙", "🦋", "🐝", "🐞",
    "🦔", "🐳", "🐬", "🦒", "🐘", "🦩", "🐶", "🐱",
    "🐭", "🐹", "🦝", "🐴", "🌻", "🌵", "🍓", "🍉",
    "⚽", "🎸", "🚲", "⛵", "🌈", "⭐", "🌙", "☀️"
)

/** Välj en emoji ur [emojis]; den valda har korallton och kant. */
@Composable
fun EmojiPicker(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier, emojis: List<String> = PERSON_EMOJIS) {
    ChoiceGrid(emojis, selected, onSelect, label = { it }, modifier = modifier) { emoji, isSelected ->
        val cell = if (isSelected) {
            Modifier.background(MaterialTheme.colorScheme.primaryContainer, AppTheme.shapes.pill)
                .border(SELECTED_BORDER, MaterialTheme.colorScheme.primary, AppTheme.shapes.pill)
        } else {
            Modifier
        }
        Box(cell.fillMaxSize(), contentAlignment = Alignment.Center) { Text(emoji, style = AppTheme.typography.sectionTitle) }
    }
}

private val SELECTED_BORDER = 2.5.dp
