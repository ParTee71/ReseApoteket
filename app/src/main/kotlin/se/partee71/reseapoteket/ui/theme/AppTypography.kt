package se.partee71.reseapoteket.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import se.partee71.reseapoteket.R

private val Nunito = FontFamily(Font(R.font.nunito_extrabold, FontWeight.ExtraBold))

private val NunitoSans = FontFamily(
    Font(R.font.nunito_sans_regular, FontWeight.Normal),
    Font(R.font.nunito_sans_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_sans_bold, FontWeight.Bold)
)

private fun nunito(size: Int, line: Int, spacing: Double = 0.0) = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.em)

private fun sans(size: Int, line: Int, weight: FontWeight) = TextStyle(fontFamily = NunitoSans, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

/** Material 3-typskalan med Nunito (rubriker) och Nunito Sans (brödtext) – används av [ReseApoteketTheme]. */
internal val AppTypeScale = Typography(
    displaySmall = nunito(34, 38, -0.015),
    headlineMedium = nunito(28, 32),
    headlineSmall = nunito(22, 28),
    titleLarge = nunito(18, 24),
    titleMedium = sans(16, 22, FontWeight.Bold),
    titleSmall = sans(14, 20, FontWeight.Bold),
    bodyLarge = sans(16, 24, FontWeight.Normal),
    bodyMedium = sans(14, 20, FontWeight.Normal),
    bodySmall = sans(13, 18, FontWeight.SemiBold),
    labelLarge = nunito(15, 20),
    labelMedium = sans(13, 16, FontWeight.Bold),
    labelSmall = sans(12, 16, FontWeight.Bold).copy(letterSpacing = 0.05.em)
)

/**
 * Appens namngivna textstilar – det enda sättet feature-kod väljer typografi (skill `ui-style`).
 */
object AppTypography {
    /** Stor rundad rubrik överst på en toppnivåskärm ("Resor"). */
    val screenTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.displaySmall

    /** Stor siffra, t.ex. antalet packade i packprogressen. */
    val bigNumber: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.headlineMedium

    /** Rubrik i dialoger och tomma tillstånd. */
    val headline: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.headlineSmall

    /** Sektionsrubrik och rubrik på en underskärm. */
    val sectionTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleLarge

    /** Mängd i en förhandsvisning ("18 tabletter · 2 kartor"). */
    val quantity: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleLarge

    /** Titeln på en listrad; avstavas i stället för att brytas mitt i ordet i smala kolumner ("Feberter-mometer"). */
    val itemTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleMedium.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph)

    /** Undertext och fotnoter. */
    val itemSubtitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodySmall

    /** Löptext, t.ex. meningen i ett tomt tillstånd. */
    val body: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyLarge

    /** Text i pills och chips. */
    val pill: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.labelMedium

    /** Liten rubrik över en grupp i inställningar ("UTSEENDE"). */
    val caption: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.labelSmall

    val button: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.labelLarge
}
