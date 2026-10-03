package se.partee71.reseapoteket.data.datastore

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import se.partee71.reseapoteket.core.engine.ReminderKind
import se.partee71.reseapoteket.core.engine.ReminderSetting
import se.partee71.reseapoteket.core.engine.ReminderSettings
import se.partee71.reseapoteket.testing.FakeDataStore

/** Enhetens inställningar: tema (SET-1), valt hushåll (HUS-4), ett obekräftat hushåll (HUS-2) och påminnelserna (PÅM-1). */
class SettingsStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.dataStore() = settingsDataStore(backgroundScope) { folder.newFile("s.preferences_pb") }

    @Test
    fun `temat är System tills användaren väljer`() = runTest {
        val settings = SettingsStore(dataStore())
        assertEquals(ThemeMode.System, settings.themeMode.first())
        settings.setThemeMode(ThemeMode.Dark)
        assertEquals(ThemeMode.Dark, settings.themeMode.first())
    }

    @Test
    fun `avprickade flyttas sist tills användaren stänger av det (SET-3)`() = runTest {
        val settings = SettingsStore(dataStore())
        assertEquals(true, settings.moveCheckedLast.first())
        settings.setMoveCheckedLast(false)
        assertEquals(false, settings.moveCheckedLast.first())
    }

    @Test
    fun `påminnelserna har förval tills användaren ändrar dem (PÅM-1)`() = runTest {
        val settings = SettingsStore(dataStore())
        assertEquals(ReminderSettings(), settings.reminderSettings.first())
        settings.setReminderPersons(setOf("anna", "lova"))
        val changed = ReminderSetting(false, LocalTime(20, 15), 5)
        settings.setReminder(ReminderKind.TIME_TO_PACK, changed)
        val read = settings.reminderSettings.first()
        assertEquals(setOf("anna", "lova"), read.personIds)
        assertEquals(changed, read[ReminderKind.TIME_TO_PACK])
        assertEquals(ReminderSettings.DEFAULTS[ReminderKind.NO_LIST], read[ReminderKind.NO_LIST])
    }

    @Test
    fun `ogiltiga lagrade tider och dagar ger förvalet`() = runTest {
        val store = dataStore()
        store.edit {
            it[intPreferencesKey("reminder.TIME_TO_PACK.minutes")] = 5_000
            it[intPreferencesKey("reminder.TIME_TO_PACK.daysBefore")] = 0
        }
        assertEquals(ReminderSettings.DEFAULTS[ReminderKind.TIME_TO_PACK], SettingsStore(store).reminderSettings.first()[ReminderKind.TIME_TO_PACK])
    }

    @Test
    fun `hur långt påminnelserna gåtts igenom sparas`() = runTest {
        val settings = SettingsStore(dataStore())
        assertNull(settings.remindersCheckedUntil.first())
        settings.setRemindersCheckedUntil(Instant.fromEpochMilliseconds(1_790_000_000_000))
        assertEquals(Instant.fromEpochMilliseconds(1_790_000_000_000), settings.remindersCheckedUntil.first())
    }

    @Test
    fun `ett okänt lagrat tema, t_ex från en nyare app, ger System`() = runTest {
        val store = dataStore()
        store.edit { it[stringPreferencesKey("themeMode")] = "Sepia" }
        assertEquals(ThemeMode.System, SettingsStore(store).themeMode.first())
    }

    @Test
    fun `en trasig inställningsfil ersätts, så att läsning och skrivning fungerar`() = runTest {
        val file = folder.newFile("trasig.preferences_pb").apply { writeBytes(byteArrayOf(0x0a, 0x7f, 0x01)) }
        val settings = SettingsStore(settingsDataStore(backgroundScope) { file })

        assertNull(settings.activeHouseholdId.first())
        settings.setActiveHouseholdId("h1")
        assertEquals("h1", settings.activeHouseholdId.first())
    }

    @Test
    fun `ett läsfel ger standardvärden och flödet fortsätter i stället för att ta slut`() = runTest {
        val store = FakeDataStore(readFailures = 1, initial = mutablePreferencesOf(stringPreferencesKey("activeHouseholdId") to "h1"))
        val settings = SettingsStore(store)

        settings.activeHouseholdId.test {
            assertNull(awaitItem(), "standardvärde medan filen inte går att läsa")
            assertEquals("h1", awaitItem(), "läsningen försöker igen")
            settings.setActiveHouseholdId("h2")
            assertEquals("h2", awaitItem(), "flödet lever kvar efter felet")
        }
    }

    @Test
    fun `valet tas bara bort om det fortfarande gäller samma hushåll`() = runTest {
        val settings = SettingsStore(dataStore())
        settings.setActiveHouseholdId("nytt")
        settings.clearActiveHouseholdId(ifEquals = "gammalt")
        assertEquals("nytt", settings.activeHouseholdId.first())
        settings.clearActiveHouseholdId(ifEquals = "nytt")
        assertNull(settings.activeHouseholdId.first())
    }

    @Test
    fun `ett obekräftat hushåll sparas och glöms`() = runTest {
        val settings = SettingsStore(dataStore())
        val pending = PendingHousehold("h1", "uid-anna", Instant.fromEpochMilliseconds(1_790_000_000_123))
        settings.setPendingHousehold(pending)
        assertEquals(pending, settings.pendingHousehold.first())
        settings.setPendingHousehold(null)
        assertNull(settings.pendingHousehold.first())
    }
}
