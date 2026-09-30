package se.partee71.reseapoteket.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Färgtoner för pills, sektionsrubriker och förhandsvisningar – en per sektion (skill `ui-style`). */
enum class Tone {
    /** Korall – Mediciner, och det som är markerat eller ändrat. */
    Primary,

    /** Grön – Kosttillskott. */
    Green,

    /** Blå – Övrigt och förhandsvisningar. */
    Blue,
}

/** Behållare och innehåll för en [Tone]; innehållet klarar 4,5:1 mot behållaren. */
@Immutable
data class ToneColors(val container: Color, val content: Color)

/** Appens färger utöver Material 3-rollerna: kort, radtoning, verktygsrad och sektionstoner. */
@Immutable
data class ExtendedColors(
    val card: Color,
    val rowTint: Color,
    val toolbar: Color,
    val onToolbar: Color,
    val primaryTone: ToneColors,
    val greenTone: ToneColors,
    val blueTone: ToneColors,
)

/**
 * Den enda paletten (skill `ui-style`): korall på aprikos, mörkt tema med samma roller.
 * Ingen dynamic color – appen har en egen identitet. Kontrasten kontrolleras av
 * `ThemeContrastTest`.
 */
object AppColors {
    val light: ColorScheme = lightColorScheme(
        primary = Color(0xFFBF4A2A),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFE6DA),
        onPrimaryContainer = Color(0xFF7A2E18),
        inversePrimary = Color(0xFFFFB59C),
        secondary = Color(0xFF6B5A50),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFE6DA),
        onSecondaryContainer = Color(0xFF7A2E18),
        tertiary = Color(0xFF2C6584),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFE2EFF6),
        onTertiaryContainer = Color(0xFF1F4C66),
        background = Color(0xFFFFF4EE),
        onBackground = Color(0xFF241B16),
        surface = Color(0xFFFFF4EE),
        onSurface = Color(0xFF241B16),
        surfaceVariant = Color(0xFFF6E6DD),
        onSurfaceVariant = Color(0xFF6B5A50),
        surfaceTint = Color(0xFFBF4A2A),
        inverseSurface = Color(0xFF3A2E28),
        inverseOnSurface = Color(0xFFFBEEE7),
        error = Color(0xFFB3261E),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF8C1D18),
        outline = Color(0xFF8C7B71),
        outlineVariant = Color(0xFFEADDD5),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFFFF4EE),
        surfaceDim = Color(0xFFEBDDD5),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFDF1EA),
        surfaceContainer = Color(0xFFFFFFFF),
        surfaceContainerHigh = Color(0xFFFFFFFF),
        surfaceContainerHighest = Color(0xFFF6E6DD),
    )

    val dark: ColorScheme = darkColorScheme(
        primary = Color(0xFFFFB59C),
        onPrimary = Color(0xFF5E1703),
        primaryContainer = Color(0xFF6E2A15),
        onPrimaryContainer = Color(0xFFFFDBCD),
        inversePrimary = Color(0xFFBF4A2A),
        secondary = Color(0xFFCDBBB1),
        onSecondary = Color(0xFF33271F),
        secondaryContainer = Color(0xFF6E2A15),
        onSecondaryContainer = Color(0xFFFFDBCD),
        tertiary = Color(0xFF9BCBE8),
        onTertiary = Color(0xFF0E3448),
        tertiaryContainer = Color(0xFF1D3A4B),
        onTertiaryContainer = Color(0xFFC6E5F7),
        background = Color(0xFF1C1512),
        onBackground = Color(0xFFF3E6DF),
        surface = Color(0xFF1C1512),
        onSurface = Color(0xFFF3E6DF),
        surfaceVariant = Color(0xFF33271F),
        onSurfaceVariant = Color(0xFFCDBBB1),
        surfaceTint = Color(0xFFFFB59C),
        inverseSurface = Color(0xFFF3E6DF),
        inverseOnSurface = Color(0xFF33271F),
        error = Color(0xFFF2B8B5),
        onError = Color(0xFF601410),
        errorContainer = Color(0xFF601410),
        onErrorContainer = Color(0xFFF9DEDC),
        outline = Color(0xFF9E8C82),
        outlineVariant = Color(0xFF4A3B33),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF3A2E28),
        surfaceDim = Color(0xFF1C1512),
        surfaceContainerLowest = Color(0xFF17110E),
        surfaceContainerLow = Color(0xFF33271F),
        surfaceContainer = Color(0xFF2A201B),
        surfaceContainerHigh = Color(0xFF2A201B),
        surfaceContainerHighest = Color(0xFF3A2E28),
    )

    val lightExtended = ExtendedColors(
        card = Color(0xFFFFFFFF),
        rowTint = Color(0xFFFDF1EA),
        toolbar = Color(0xFF241B16),
        onToolbar = Color(0xFFF3E6DF),
        primaryTone = ToneColors(Color(0xFFFFE6DA), Color(0xFF7A2E18)),
        greenTone = ToneColors(Color(0xFFE3F0D6), Color(0xFF3F5A1C)),
        blueTone = ToneColors(Color(0xFFE2EFF6), Color(0xFF1F4C66)),
    )

    val darkExtended = ExtendedColors(
        card = Color(0xFF2A201B),
        rowTint = Color(0xFF33271F),
        toolbar = Color(0xFFF3E6DF),
        onToolbar = Color(0xFF33271F),
        primaryTone = ToneColors(Color(0xFF6E2A15), Color(0xFFFFDBCD)),
        greenTone = ToneColors(Color(0xFF34451F), Color(0xFFD2EDB0)),
        blueTone = ToneColors(Color(0xFF1D3A4B), Color(0xFFC6E5F7)),
    )

    /** De åtta personfärgerna som hex – det som lagras i `Person.colorHex`. */
    val PERSON_HEX: List<String> = listOf(
        "#D97757", "#3B7EA1", "#7A9A3C", "#8E5BA8", "#C9892A", "#3E8E7E", "#B8527A", "#5B6BB5",
    )

    /** Personfärg nummer [index] (varvar efter åtta). */
    fun person(index: Int): Color = parse(PERSON_HEX[Math.floorMod(index, PERSON_HEX.size)])

    /** Personfärgen för ett lagrat `colorHex`; ett oläsbart värde ger första förvalet. */
    fun person(hex: String): Color = runCatching { parse(hex) }.getOrElse { person(0) }

    private fun parse(hex: String): Color {
        val digits = hex.removePrefix("#")
        require(digits.length == 6) { "Förväntade #RRGGBB" }
        return Color(0xFF000000 or digits.toLong(16))
    }

    val extended: ExtendedColors
        @Composable @ReadOnlyComposable
        get() = LocalExtendedColors.current

    /** Färgerna för en [Tone] i aktuellt tema. */
    @Composable
    @ReadOnlyComposable
    fun tone(tone: Tone): ToneColors = when (tone) {
        Tone.Primary -> extended.primaryTone
        Tone.Green -> extended.greenTone
        Tone.Blue -> extended.blueTone
    }
}

internal val LocalExtendedColors = staticCompositionLocalOf { AppColors.lightExtended }
