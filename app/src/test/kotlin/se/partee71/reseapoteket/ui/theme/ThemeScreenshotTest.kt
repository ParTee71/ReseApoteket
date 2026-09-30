package se.partee71.reseapoteket.ui.theme

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.reseapoteket.testing.captureLightAndDark
import se.partee71.reseapoteket.ui.AppRootContent
import se.partee71.reseapoteket.ui.auth.AuthGate
import se.partee71.reseapoteket.ui.auth.AuthUiState

@RunWith(RobolectricTestRunner::class)
class ThemeScreenshotTest {

    @Test
    fun `startskärmen i ljust och mörkt tema`() {
        captureLightAndDark("AppRoot_start") { AppRootContent(AuthUiState(AuthGate.Ready), {}) }
    }
}
