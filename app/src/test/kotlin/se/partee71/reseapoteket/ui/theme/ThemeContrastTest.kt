package se.partee71.reseapoteket.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.assertTrue
import org.junit.Test

/** Varje färg som bär text klarar 4,5:1 mot sin bakgrund, i båda temana (skill `ui-style`, NFR-11). */
class ThemeContrastTest {

    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    private fun pairs(scheme: ColorScheme, extended: ExtendedColors) = mapOf(
        "text på bakgrund" to (scheme.onBackground to scheme.background),
        "dämpad text på bakgrund" to (scheme.onSurfaceVariant to scheme.background),
        "text på kort" to (scheme.onSurface to extended.card),
        "dämpad text på kort" to (scheme.onSurfaceVariant to extended.card),
        "dämpad text på tonad rad" to (scheme.onSurfaceVariant to extended.rowTint),
        "primär text på kort" to (scheme.primary to extended.card),
        "primär text på bakgrund" to (scheme.primary to scheme.background),
        "text på primärknapp" to (scheme.onPrimary to scheme.primary),
        "text på primärbehållare" to (scheme.onPrimaryContainer to scheme.primaryContainer),
        "text på sekundärknapp" to (scheme.onSecondaryContainer to scheme.secondaryContainer),
        "felknapp" to (scheme.onError to scheme.error),
        "fel på kort" to (scheme.error to extended.card),
        "text på felbehållare" to (scheme.onErrorContainer to scheme.errorContainer),
        "snackbar" to (scheme.inverseOnSurface to scheme.inverseSurface),
        "verktygsrad" to (extended.onToolbar to extended.toolbar),
        "korallton" to (extended.primaryTone.content to extended.primaryTone.container),
        "grön ton" to (extended.greenTone.content to extended.greenTone.container),
        "blå ton" to (extended.blueTone.content to extended.blueTone.container)
    )

    @Test
    fun `ljust tema`() = check("ljust", pairs(AppColors.light, AppColors.lightExtended))

    @Test
    fun `mörkt tema`() = check("mörkt", pairs(AppColors.dark, AppColors.darkExtended))

    @Test
    fun `personfärger varvar och ett oläsbart värde ger första förvalet`() {
        assertTrue(AppColors.person(8) == AppColors.person(0))
        assertTrue(AppColors.person("inte en färg") == AppColors.person(0))
        assertTrue(AppColors.person("#3B7EA1") == AppColors.person(1))
    }

    private fun check(theme: String, pairs: Map<String, Pair<Color, Color>>) {
        val failures = pairs.mapNotNull { (name, colors) ->
            val ratio = contrast(colors.first, colors.second)
            "$theme: $name har ${"%.2f".format(ratio)}:1".takeIf { ratio < MIN }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    private companion object {
        const val MIN = 4.5
    }
}
