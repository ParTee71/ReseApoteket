package se.partee71.reseapoteket.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle

/**
 * Appens namngivna textstilar – det enda sättet feature-kod väljer typografi (skill `ui-style`).
 * I dag mappade på Material 3:s typskala; Nunito och resten av stilarna kommer med designsystemet.
 */
object AppTypography {
    val screenTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.headlineMedium
}
