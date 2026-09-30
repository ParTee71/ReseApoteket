package se.partee71.reseapoteket.data.household

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import se.partee71.reseapoteket.core.schema.Schema
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.common.HouseholdVersionSource
import se.partee71.reseapoteket.data.datastore.SettingsStore

class HouseholdSessionTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val versions = mutableMapOf<String, MutableStateFlow<Int?>>()
    private val versionSource = HouseholdVersionSource { id -> versions.getOrPut(id) { MutableStateFlow(Schema.CURRENT_VERSION) } }

    private fun TestScope.session(): HouseholdSession {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("settings.preferences_pb") }
        return HouseholdSession(SettingsStore(store), versionSource, backgroundScope)
    }

    @Test
    fun `valt hushåll sparas och följs av versionen`() = runTest(UnconfinedTestDispatcher()) {
        val session = session()
        session.householdId.test {
            assertNull(awaitItem())
            session.select("h1")
            assertEquals("h1", awaitItem())
        }
        session.schemaVersion.test {
            assertEquals(Schema.CURRENT_VERSION, awaitItem())
            versions.getValue("h1").value = Schema.CURRENT_VERSION + 1
            assertEquals(Schema.CURRENT_VERSION + 1, awaitItem())
        }
        assertEquals(DataError.UpdateRequired, session.writeBlocker())
    }

    @Test
    fun `utan valt hushåll är versionen okänd och skrivning inte spärrad av version`() = runTest(UnconfinedTestDispatcher()) {
        val session = session()
        assertNull(session.schemaVersion.value)
        assertNull(session.writeBlocker())
    }
}
