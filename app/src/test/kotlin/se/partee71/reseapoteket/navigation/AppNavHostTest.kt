package se.partee71.reseapoteket.navigation

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavEntry
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.ui.components.AppTopBar
import se.partee71.reseapoteket.ui.sync.SyncEvent
import se.partee71.reseapoteket.ui.sync.SyncUiState
import se.partee71.reseapoteket.ui.theme.ReseApoteketTheme

/** Navigationsramen: verktygsraden i flikarnas rot, dold på undersidor (NAV-1). */
@RunWith(RobolectricTestRunner::class)
class AppNavHostTest {

    @get:Rule
    val rule = createComposeRule()

    private val backStack = AppBackStack()

    private fun show() = rule.setContent {
        ReseApoteketTheme {
            AppNavHost(backStack, { key -> NavEntry(key) { Text("Skärm: ${key::class.simpleName}") } })
        }
    }

    @Test
    fun `verktygsraden byter flik och visar flikens skärm`() {
        show()
        rule.onNodeWithText("Skärm: TripsKey").assertIsDisplayed()
        rule.onNodeWithContentDescription("Personer").performClick()
        rule.onNodeWithText("Skärm: PersonsKey").assertIsDisplayed()
        assertEquals(PersonsKey, backStack.currentTab)
    }

    @Test
    fun `en flik behåller sitt sparade tillstånd när man byter flik och tillbaka`() {
        rule.setContent {
            ReseApoteketTheme {
                AppNavHost(backStack, { key ->
                    NavEntry(key) {
                        var taps by rememberSaveable { mutableIntStateOf(0) }
                        Text("${key::class.simpleName}: $taps", Modifier.clickable { taps++ })
                    }
                })
            }
        }
        rule.onNodeWithText("PersonsKey", substring = true).assertDoesNotExist()
        rule.onNodeWithContentDescription("Personer").performClick()
        rule.onNodeWithText("PersonsKey: 0").performClick()
        rule.onNodeWithText("PersonsKey: 1").assertIsDisplayed()
        rule.onNodeWithContentDescription("Familjeartiklar").performClick()
        rule.onNodeWithContentDescription("Personer").performClick()
        rule.onNodeWithText("PersonsKey: 1").assertIsDisplayed()
    }

    @Test
    fun `en stängd undersida börjar om när den öppnas igen`() {
        rule.setContent {
            ReseApoteketTheme {
                AppNavHost(backStack, { key ->
                    NavEntry(key) {
                        var taps by rememberSaveable { mutableIntStateOf(0) }
                        Text("${key::class.simpleName}: $taps", Modifier.clickable { taps++ })
                    }
                })
            }
        }
        rule.runOnIdle { backStack.push(TripEditKey()) }
        rule.onNodeWithText("TripEditKey: 0").performClick()
        rule.runOnIdle { backStack.pop() }
        rule.runOnIdle { backStack.push(TripEditKey()) }
        rule.onNodeWithText("TripEditKey: 0").assertIsDisplayed()
    }

    @Test
    fun `undersidor döljer verktygsraden`() {
        show()
        rule.runOnIdle { backStack.push(TripEditKey()) }
        rule.onNodeWithText("Skärm: TripEditKey").assertIsDisplayed()
        rule.onNodeWithContentDescription("Personer").assertDoesNotExist()
        rule.runOnIdle { backStack.pop() }
        rule.onNodeWithContentDescription("Personer").assertIsDisplayed()
    }

    @Test
    fun `synkindikatorn syns i skärmens toppbar och förklarar sig vid tryck (NFR-6)`() {
        var sync by mutableStateOf(SyncUiState())
        rule.setContent {
            ReseApoteketTheme {
                AppNavHost(backStack, { key -> NavEntry(key) { AppTopBar("Resor") } }, sync = sync)
            }
        }
        rule.onNodeWithContentDescription("Ändringar väntar på att synkas").assertDoesNotExist()
        sync = SyncUiState(pending = true)
        rule.onNodeWithContentDescription("Ändringar väntar på att synkas").performClick()
        rule.onNodeWithText("Ändringarna är sparade på telefonen och skickas när det finns nät.").assertIsDisplayed()
    }

    @Test
    fun `en nekad skrivning visas med orsaken och släpps sedan (NFR-6)`() {
        val events = mutableListOf<SyncEvent>()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            ReseApoteketTheme {
                AppNavHost(
                    backStack,
                    { key -> NavEntry(key) { Text("Skärm: ${key::class.simpleName}") } },
                    sync = SyncUiState(writeError = DataError.PermissionDenied),
                    onSyncEvent = { events += it },
                )
            }
        }
        rule.mainClock.advanceTimeBy(SHOWN_MILLIS)
        rule.onNodeWithText("En ändring kunde inte sparas och har ångrats. Du har inte behörighet till det här hushållet.").assertIsDisplayed()
        assertEquals(emptyList(), events)
        rule.mainClock.advanceTimeBy(LONG_SNACKBAR_MILLIS)
        rule.waitForIdle()
        assertEquals(listOf<SyncEvent>(SyncEvent.WriteErrorShown), events)
    }

    @Test
    fun `tryck på synkindikatorn stänger inte ett felmeddelande som visas`() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            ReseApoteketTheme {
                AppNavHost(
                    backStack,
                    { key -> NavEntry(key) { AppTopBar("Resor") } },
                    sync = SyncUiState(pending = true, writeError = DataError.PermissionDenied),
                )
            }
        }
        rule.mainClock.advanceTimeBy(SHOWN_MILLIS)
        rule.onNodeWithContentDescription("Ändringar väntar på att synkas").performClick()
        rule.mainClock.advanceTimeBy(SHOWN_MILLIS)
        rule.onNodeWithText("En ändring kunde inte sparas och har ångrats. Du har inte behörighet till det här hushållet.").assertIsDisplayed()
        rule.onNodeWithText("Ändringarna är sparade på telefonen och skickas när det finns nät.").assertDoesNotExist()
    }

    private companion object {
        const val SHOWN_MILLIS = 500L
        const val LONG_SNACKBAR_MILLIS = 11_000L
    }
}
