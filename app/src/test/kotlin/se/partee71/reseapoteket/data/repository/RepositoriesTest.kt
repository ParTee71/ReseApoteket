package se.partee71.reseapoteket.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import se.partee71.reseapoteket.core.model.PackingItem
import se.partee71.reseapoteket.core.model.Regimen
import se.partee71.reseapoteket.data.FakeCollectionFactory
import se.partee71.reseapoteket.data.FixedClock
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.common.dataError
import se.partee71.reseapoteket.data.datastore.SettingsStore
import se.partee71.reseapoteket.data.household.HouseholdSession
import se.partee71.reseapoteket.testing.FakeHouseholdDirectory

/** Repositories byggs med `FakeCollectionFactory`; här testas bara domänfrågorna. */
class RepositoriesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val collections = FakeCollectionFactory()

    @Test
    fun `nytt hushåll får ägaren som enda medlem, blir aktivt och kan observeras`() = runTest(UnconfinedTestDispatcher()) {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("s.preferences_pb") }
        val session = HouseholdSession(SettingsStore(store), { flowOf(1) }, backgroundScope)
        val repository = DefaultHouseholdRepository(collections, FakeHouseholdDirectory(collections), session, FixedClock())

        val id = repository.create("Familjen", "uid-anna").getOrThrow()

        assertEquals(id, session.householdId.first { it != null })
        val household = repository.current.first { it != null }!!
        assertEquals(listOf("uid-anna"), household.memberUids)
        assertEquals("Familjen", household.name)
        assertEquals(FixedClock().instant, household.createdAt)
    }

    @Test
    fun `ett nytt försök efter timeout skriver samma hushåll, inte ett till`() = runTest(UnconfinedTestDispatcher()) {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("s.preferences_pb") }
        val session = HouseholdSession(SettingsStore(store), { flowOf(1) }, backgroundScope)
        val directory = FakeHouseholdDirectory(collections)
        val repository = DefaultHouseholdRepository(collections, directory, session, FixedClock())
        directory.createFailure = DataError.Offline

        assertEquals(DataError.Offline, repository.create("Mitt hushåll", "uid-anna").dataError())
        directory.createFailure = null
        val id = repository.create("Mitt hushåll", "uid-anna").getOrThrow()

        assertEquals(listOf(id), collections.households().getAll().getOrThrow().map { it.id })
        val next = repository.create("Andra", "uid-anna").getOrThrow()
        assertTrue(next != id, "efter en lyckad skapelse får nästa hushåll ett nytt ID")
    }

    @Test
    fun `regimensFor ger recepten per person och följer ändringar`() = runTest(UnconfinedTestDispatcher()) {
        val repository = DefaultRegimenRepository(collections)
        repository.forPerson("anna").upsert(Regimen("r1", name = "Levaxin")).getOrThrow()
        repository.forPerson("bertil").upsert(Regimen("r2", name = "D-vitamin")).getOrThrow()

        repository.regimensFor(listOf("anna", "bertil", "anna")).test {
            val byPerson = awaitItem()
            assertEquals(listOf("Levaxin"), byPerson.getValue("anna").map { it.name })
            assertEquals(listOf("D-vitamin"), byPerson.getValue("bertil").map { it.name })
            repository.forPerson("anna").upsert(Regimen("r3", name = "Alvedon", sortOrder = 1)).getOrThrow()
            assertEquals(listOf("Levaxin", "Alvedon"), awaitItem().getValue("anna").map { it.name })
        }
        assertEquals(emptyMap(), repository.regimensFor(emptyList()).first())
    }

    @Test
    fun `replacePackingItems ersätter resans lista och tar bort rader som inte finns kvar`() = runTest {
        val repository = DefaultPackingRepository(collections)
        repository.replacePackingItems("t1", listOf(PackingItem("a", name = "A"), PackingItem("b", name = "B"))).getOrThrow()
        repository.forTrip("t2").upsert(PackingItem("x", name = "Annan resa")).getOrThrow()

        repository.replacePackingItems("t1", listOf(PackingItem("b", name = "B2"), PackingItem("c", name = "C"))).getOrThrow()

        assertEquals(listOf("B2", "C"), repository.forTrip("t1").getAll().getOrThrow().map { it.name }.sorted())
        assertEquals(listOf("Annan resa"), repository.forTrip("t2").getAll().getOrThrow().map { it.name })
    }
}
