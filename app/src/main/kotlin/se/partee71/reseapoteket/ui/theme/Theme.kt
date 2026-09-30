@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package se.partee71.reseapoteket.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable

/**
 * Appens tema: Material 3 Expressive med fjädrande rörelse. Färger, typografi, former och
 * avstånd fylls i enligt skill `ui-style` när designsystemet byggs.
 *
 * Mörkt läge använder `darkColorScheme()`: Expressive har bara ett ljust standardschema
 * (`expressiveLightColorScheme()`) i `material3:1.5.0-alpha29`. `AppColors` definierar båda
 * scheman med samma roller när designsystemet byggs (ARKITEKTUR.md → "Verifierade API:er").
 */
@Composable
fun ReseApoteketTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(
        colorScheme = if (darkTheme) darkColorScheme() else expressiveLightColorScheme(),
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
