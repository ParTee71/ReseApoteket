package se.partee71.reseapoteket.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import se.partee71.reseapoteket.R
import se.partee71.ui.theme.AppTheme

private val Nunito = FontFamily(Font(R.font.nunito_extrabold, FontWeight.ExtraBold))

private val NunitoSans = FontFamily(
    Font(R.font.nunito_sans_regular, FontWeight.Normal),
    Font(R.font.nunito_sans_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_sans_bold, FontWeight.Bold)
)

private fun nunito(size: Int, line: Int, spacing: Double = 0.0) = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.em)

private fun sans(size: Int, line: Int, weight: FontWeight) = TextStyle(fontFamily = NunitoSans, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

/**
 * Material 3-typskalan med Nunito (rubriker) och Nunito Sans (brödtext) – används av [ReseApoteketTheme].
 * De namngivna stilarna (`AppTheme.typography`) härleds ur den i android-ui (`AppTheme.typography.from`).
 */
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
