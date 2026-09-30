package se.partee71.reseapoteket.testing

import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import se.partee71.reseapoteket.ui.theme.ReseApoteketTheme

/**
 * Tar skärmdumpar i ljust och mörkt tema:
 * `app/src/test/screenshots/<name>_light.png` och `<name>_dark.png` (skill testing-strategy).
 * [name] följer `<Komponent>_<variant>`.
 */
fun captureLightAndDark(name: String, content: @Composable () -> Unit) {
    for ((suffix, dark) in listOf("light" to false, "dark" to true)) {
        captureRoboImage("src/test/screenshots/${name}_$suffix.png") {
            ReseApoteketTheme(darkTheme = dark, content = content)
        }
    }
}
