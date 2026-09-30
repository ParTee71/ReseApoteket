package se.partee71.reseapoteket.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavEntry
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
        rule.setContent { ReseApoteketTheme { AppNavHost(backStack, appEntries(backStack)) } }
        rule.onNodeWithText("Den här delen byggs i en senare version av appen.").assertIsDisplayed()
        rule.onNodeWithContentDescription("Tillbaka").assertIsDisplayed()
    }
}
