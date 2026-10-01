package se.partee71.reseapoteket.ui.theme

import androidx.navigation3.runtime.NavEntry
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.reseapoteket.navigation.AppBackStack
import se.partee71.reseapoteket.navigation.AppNavHost
import se.partee71.reseapoteket.testing.captureLightAndDark
import se.partee71.reseapoteket.ui.AppRootContent
import se.partee71.reseapoteket.ui.auth.AuthGate
import se.partee71.reseapoteket.ui.auth.AuthUiState
import se.partee71.reseapoteket.ui.common.ListUiState
import se.partee71.reseapoteket.ui.trips.TripsContent

@RunWith(RobolectricTestRunner::class)
class ThemeScreenshotTest {

    /** Hela startskärmen efter inloggning: Resor utan resor, med verktygsraden (NAV-2). */
    @Test
    fun `startskärmen i ljust och mörkt tema`() {
        captureLightAndDark("AppRoot_start") {
            AppRootContent(AuthUiState(AuthGate.Ready), {}) {
                AppNavHost(AppBackStack(), { key -> NavEntry(key) { TripsContent(ListUiState.Empty, {}, {}, {}) } })
            }
        }
    }
}
