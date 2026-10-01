package se.partee71.reseapoteket.data.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Temavalet (SET-1) lagras per enhet i DataStore. */
class SettingsStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.dataStore() = PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("s.preferences_pb") }

    @Test
    fun `temat är System tills användaren väljer`() = runTest {
        val settings = SettingsStore(dataStore())
        assertEquals(ThemeMode.System, settings.themeMode.first())
        settings.setThemeMode(ThemeMode.Dark)
        assertEquals(ThemeMode.Dark, settings.themeMode.first())
    }

    @Test
    fun `ett okänt lagrat tema, t_ex från en nyare app, ger System`() = runTest {
        val store = dataStore()
        store.edit { it[stringPreferencesKey("themeMode")] = "Sepia" }
        assertEquals(ThemeMode.System, SettingsStore(store).themeMode.first())
    }
}
