package se.partee71.reseapoteket.ui.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.testing.captureLightAndDark
import se.partee71.reseapoteket.ui.AppRootContent
import se.partee71.reseapoteket.ui.theme.ReseApoteketTheme

/** Skärmarna som inloggningen styr: beteende och skärmdumpar (ljust + mörkt). */
@RunWith(RobolectricTestRunner::class)
class AuthScreensTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `inloggningen visar knappen och skickar SignIn`() {
        val events = mutableListOf<AuthEvent>()
        rule.setContent { ReseApoteketTheme { AppRootContent(AuthUiState(AuthGate.SignedOut), { events += it }) } }

        rule.onNodeWithText("Håll koll på vad ni ska packa i reseapoteket").assertIsDisplayed()
        rule.onNodeWithText("Logga in med Google").performClick()

        assertIs<AuthEvent.SignIn>(events.single())
    }

    @Test
    fun `ett fel visas som snackbar och markeras som visat`() {
        val events = mutableListOf<AuthEvent>()
        rule.setContent {
            ReseApoteketTheme { AppRootContent(AuthUiState(AuthGate.SignedOut, error = DataError.Offline), { events += it }) }
        }

        rule.onNodeWithText("Ingen anslutning just nu", substring = true).assertIsDisplayed()
        rule.mainClock.advanceTimeBy(SNACKBAR_SHORT_MS)
        rule.waitForIdle()
        assertEquals(listOf<AuthEvent>(AuthEvent.ErrorShown), events)
    }

    @Test
    fun `nyare hushållsversion visar Uppdatera appen`() {
        rule.setContent { ReseApoteketTheme { AppRootContent(AuthUiState(AuthGate.UpdateRequired), {}) } }
        rule.onNodeWithText("Uppdatera appen").assertIsDisplayed()
        rule.onNodeWithText("Hämta senaste versionen").assertIsDisplayed()
    }

    @Test
    fun `SignInScreen - start`() = captureLightAndDark("SignInScreen_start") {
        AppRootContent(AuthUiState(AuthGate.SignedOut), {})
    }

    @Test
    fun `SignInScreen - loggar in`() = captureLightAndDark("SignInScreen_loggarIn") {
        AppRootContent(AuthUiState(AuthGate.SignedOut, busy = true), {})
    }

    @Test
    fun `SignInScreen - fel`() {
        rule.mainClock.autoAdvance = false
        rule.captureLightAndDark("SignInScreen_fel", settle = {
            waitForIdle()
            mainClock.advanceTimeBy(SNACKBAR_VISIBLE_MS)
        }) {
            AppRootContent(AuthUiState(AuthGate.SignedOut, error = DataError.Unknown), {})
        }
    }

    @Test
    fun `UpdateRequiredScreen - start`() = captureLightAndDark("UpdateRequiredScreen_start") {
        AppRootContent(AuthUiState(AuthGate.UpdateRequired), {})
    }

    private companion object {
        /** `SnackbarDuration.Short` plus marginal. */
        const val SNACKBAR_SHORT_MS = 5_000L

        /** Snackbaren har glidit in; två bilder hinns med innan `Short` (4 s) går ut. */
        const val SNACKBAR_VISIBLE_MS = 1_000L
    }
}
