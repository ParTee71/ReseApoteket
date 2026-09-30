package se.partee71.reseapoteket.data.household

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import se.partee71.reseapoteket.core.schema.Schema
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.common.HouseholdVersion
import se.partee71.reseapoteket.data.common.HouseholdVersionSource
import se.partee71.reseapoteket.data.common.writeBlocker
import se.partee71.reseapoteket.data.datastore.SettingsStore

/** Med vanlig (köad) testdispatcher, så att ordningsproblem inte döljs. */
class HouseholdSessionTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val versions = mutableMapOf<String, MutableStateFlow<Int>>()
    private val versionSource = HouseholdVersionSource { id -> versions.getOrPut(id) { MutableStateFlow(Schema.CURRENT_VERSION) } }

    private fun TestScope.session(): HouseholdSession {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("settings.preferences_pb") }
        return HouseholdSession(SettingsStore(store), versionSource, backgroundScope)
    }

    @Test
    fun `select återvänder först när sessionen visar det valda hushållet`() = runTest {
        val session = session()
        session.select("h1")
        assertEquals("h1", session.householdId.value)
        session.select("h2")
        assertEquals("h2", session.householdId.value)
    }

    @Test
    fun `versionen följer det valda hushållet och spärrar när den är nyare`() = runTest {
        val session = session()
        session.select("h1")
        assertEquals(HouseholdVersion("h1", Schema.CURRENT_VERSION), session.schemaVersion.filterNotNull().first())
        assertNull(session.writeBlocker())
        versions.getValue("h1").value = Schema.CURRENT_VERSION + 1
        session.schemaVersion.first { it?.version == Schema.CURRENT_VERSION + 1 }
        assertEquals(DataError.UpdateRequired, session.writeBlocker())
    }

    @Test
    fun `utan valt hushåll finns ingen version`() = runTest {
        val session = session()
        assertNull(session.householdId.value)
        assertNull(session.schemaVersion.value)
    }
}
