package se.partee71.reseapoteket.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import se.partee71.ui.theme.AppShapes
import se.partee71.ui.theme.AppTheme

/** Appens former: kort 28 dp; övriga (rader 18 dp, pills helt rundade …) är android-ui:s standard. */
private val ReseApoteketShapes = AppShapes(cardRadius = 28.dp)

/**
 * Appens tema: android-ui:s `AppTheme` (Material 3 Expressive med fjädrande rörelse) med
 * [AppColors], [AppTypeScale] och appens former (skill `ui-style`). Ingen dynamic color.
 * Ljust, mörkt eller system väljs i Inställningar; `MainActivity` räknar ut [darkTheme].
 */
@Composable
fun ReseApoteketTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    AppTheme(
        colorScheme = if (darkTheme) AppColors.dark else AppColors.light,
        extendedColors = if (darkTheme) AppColors.darkExtended else AppColors.lightExtended,
        shapes = ReseApoteketShapes,
        typeScale = AppTypeScale,
        content = content
    )
}
