package se.partee71.reseapoteket.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.reseapoteket.data.datastore.SettingsStore
import se.partee71.reseapoteket.data.datastore.ThemeMode
import se.partee71.reseapoteket.testing.MainDispatcherRule
import se.partee71.reseapoteket.testing.captureLightAndDark
import se.partee71.reseapoteket.ui.theme.ReseApoteketTheme

/** Inställningar: temaval (SET-1) och komponentgalleriet i debug. */
@RunWith(RobolectricTestRunner::class)
class SettingsTest {

    @get:Rule
    val rule = createComposeRule()

    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun `temavalet sparas och följer med ViewModel`() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("s.preferences_pb") }
        val viewModel = SettingsViewModel(SettingsStore(store))
        viewModel.themeMode.test {
            assertEquals(ThemeMode.System, awaitItem())
            viewModel.onEvent(SettingsEvent.SetTheme(ThemeMode.Dark))
            assertEquals(ThemeMode.Dark, awaitItem())
        }
    }

    @Test
    fun `segmentvalet skickar valt tema`() {
        val events = mutableListOf<SettingsEvent>()
        rule.setContent { ReseApoteketTheme { SettingsContent(ThemeMode.System, { events += it }, "0.1.0", onOpenGallery = null) } }
        rule.onNodeWithText("Mörkt").performClick()
        assertEquals(listOf<SettingsEvent>(SettingsEvent.SetTheme(ThemeMode.Dark)), events)
        rule.onNodeWithText("Version 0.1.0").assertIsDisplayed()
        rule.onNodeWithText("Komponenter").assertDoesNotExist()
    }

    @Test
    fun `komponentgalleriet nås i debug`() {
        var opened = 0
        rule.setContent { ReseApoteketTheme { SettingsContent(ThemeMode.Light, {}, "0.1.0", onOpenGallery = { opened++ }) } }
        rule.onNodeWithText("Komponenter").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `Inställningar - standard`() = captureLightAndDark("SettingsScreen_standard") {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) { SettingsContent(ThemeMode.System, {}, "0.1.0", onOpenGallery = {}) }
    }
}
