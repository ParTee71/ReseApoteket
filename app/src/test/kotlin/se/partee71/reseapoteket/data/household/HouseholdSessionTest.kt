package se.partee71.reseapoteket.data.household

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
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
import se.partee71.reseapoteket.data.datastore.settingsDataStore
import se.partee71.reseapoteket.testing.FakeDataStore

/** Med vanlig (köad) testdispatcher, så att ordningsproblem inte döljs. */
class HouseholdSessionTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val versions = mutableMapOf<String, MutableStateFlow<Int>>()

    /** Hushåll vars dokument servern nekar. */
    private val denied = mutableSetOf<String>()
    /** Stämplingar som sessionen gjort: (hushåll, version). */
    private val stamps = mutableListOf<Pair<String, Int>>()
    private val versionSource = object : HouseholdVersionSource {
        override fun schemaVersion(householdId: String) =
            if (householdId in denied) flow { throw DataError.PermissionDenied } else versions.getOrPut(householdId) { MutableStateFlow(Schema.CURRENT_VERSION) }

        override fun stamp(householdId: String, version: Int) {
            stamps += householdId to version
        }
    }

    private fun TestScope.session(): HouseholdSession {
        val store = settingsDataStore(backgroundScope) { folder.newFile("settings.preferences_pb") }
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
    fun `ett äldre hushåll vars väg hit inte ändrar data stämplas en gång (BCK-5)`() = runTest {
        versions["gammalt"] = MutableStateFlow(1)
        val session = session()
        session.select("gammalt")
        session.schemaVersion.first { it?.version == 1 }
        assertEquals(listOf("gammalt" to Schema.CURRENT_VERSION), stamps)
        // Nekas stämplingen och versionen står kvar stämplas det inte om i en slinga.
        versions.getValue("gammalt").value = Schema.CURRENT_VERSION
        session.schemaVersion.first { it?.version == Schema.CURRENT_VERSION }
        versions.getValue("gammalt").value = 1
        session.schemaVersion.first { it?.version == 1 }
        assertEquals(1, stamps.size)
    }

    @Test
    fun `ett hushåll i appens version stämplas inte`() = runTest {
        val session = session()
        session.select("h1")
        session.schemaVersion.filterNotNull().first()
        assertEquals(emptyList(), stamps)
    }

    @Test
    fun `utan valt hushåll finns ingen version`() = runTest {
        val session = session()
        assertNull(session.householdId.value)
        assertNull(session.schemaVersion.value)
    }

    @Test
    fun `ett hushåll som inte går att läsa väljs bort (HUS-5)`() = runTest {
        val session = session()
        denied += "borta"
        session.select("borta")

        session.householdId.first { it == null }
        assertEquals("borta", session.unreadable.value)
    }

    @Test
    fun `ett hushåll som går att läsa igen räknas inte längre som oläsbart`() = runTest {
        val session = session()
        denied += "h1"
        session.select("h1")
        session.householdId.first { it == null }

        denied -= "h1"
        session.select("h1")
        session.schemaVersion.first { it?.householdId == "h1" }
        assertNull(session.unreadable.value)
        assertEquals("h1", session.householdId.value)
    }

    @Test
    fun `select hänger inte när inställningarna inte kan läsas tillbaka`() = runTest {
        val session = HouseholdSession(SettingsStore(FakeDataStore(frozen = true)), versionSource, backgroundScope)
        session.select("h1")
        assertEquals(5_000L, testScheduler.currentTime, "ger upp efter tidsgränsen")
    }

    @Test
    fun `ett oläsbart hushåll vars val inte går att rensa kraschar inte appen`() = runTest {
        val store = FakeDataStore(
            initial = mutablePreferencesOf(stringPreferencesKey("activeHouseholdId") to "borta"),
            writeFailure = IOException("disken är full"),
        )
        denied += "borta"
        val session = HouseholdSession(SettingsStore(store), versionSource, backgroundScope)

        session.unreadable.first { it == "borta" }
        assertEquals(DataError.PermissionDenied, session.writeBlocker())
    }
}
