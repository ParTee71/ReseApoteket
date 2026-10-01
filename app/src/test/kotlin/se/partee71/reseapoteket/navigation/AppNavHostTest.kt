package se.partee71.reseapoteket.navigation

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import se.partee71.reseapoteket.core.model.RegimenKind
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
    fun `nycklar utan egen skärm visar Kommer snart`() {
        // Undersidan läggs på före visningen: flikarnas skärmar kräver Hilt och visas aldrig här.
        backStack.push(PackingListKey("resa-1"))
        rule.setContent {
            val owner = checkNotNull(LocalViewModelStoreOwner.current)
            ReseApoteketTheme { AppNavHost(backStack, appEntries(backStack, owner)) }
        }
        rule.onNodeWithText("Den här delen byggs i en senare version av appen.").assertIsDisplayed()
        rule.onNodeWithContentDescription("Tillbaka").assertIsDisplayed()
    }

    @Test
    fun `kosttillskottens formulär visar Kommer snart tills det finns (#23)`() {
        backStack.push(RegimenEditKey("anna", RegimenKind.SUPPLEMENT))
        rule.setContent {
            val owner = checkNotNull(LocalViewModelStoreOwner.current)
            ReseApoteketTheme { AppNavHost(backStack, appEntries(backStack, owner)) }
        }
        rule.onNodeWithText("Den här delen byggs i en senare version av appen.").assertIsDisplayed()
    }
}
