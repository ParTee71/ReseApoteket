package se.partee71.reseapoteket.testing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import se.partee71.reseapoteket.ui.theme.ReseApoteketTheme

private val themes = listOf("light" to false, "dark" to true)

/**
 * Tar skärmdumpar i ljust och mörkt tema:
 * `<name>_light.png` och `<name>_dark.png` i Roborazzis outputDir (skill testing-strategy).
 * [name] följer `<Komponent>_<variant>`.
 */
fun captureLightAndDark(name: String, content: @Composable () -> Unit) {
    for ((suffix, dark) in themes) {
        captureRoboImage("${name}_$suffix.png") {
            ReseApoteketTheme(darkTheme = dark, content = content)
        }
    }
}

/**
 * Som [captureLightAndDark], men i en compose-regel: innehållet sätts en gång och byter bara
 * tema, så att effekter som redan körts (t.ex. en visad snackbar) finns med i båda bilderna.
 * [settle] väntar in varje bild; en tidsbegränsad snackbar kräver en fast tid i stället för
 * `waitForIdle`, som annars spolar fram tills den försvunnit.
 */
fun ComposeContentTestRule.captureLightAndDark(
    name: String,
    settle: ComposeContentTestRule.() -> Unit = { waitForIdle() },
    content: @Composable () -> Unit,
) {
    var dark by mutableStateOf(false)
    setContent { ReseApoteketTheme(darkTheme = dark, content = content) }
    for ((suffix, isDark) in themes) {
        dark = isDark
        settle()
        onRoot().captureRoboImage("${name}_$suffix.png")
    }
}
