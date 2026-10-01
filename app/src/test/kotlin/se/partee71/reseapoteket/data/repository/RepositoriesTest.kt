package se.partee71.reseapoteket.data.repository

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import se.partee71.reseapoteket.core.model.Household
import se.partee71.reseapoteket.core.model.PackingItem
import se.partee71.reseapoteket.core.model.Regimen
import se.partee71.reseapoteket.data.FakeCollectionFactory
import se.partee71.reseapoteket.data.FixedClock
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.common.dataError
import se.partee71.reseapoteket.data.datastore.SettingsStore
import se.partee71.reseapoteket.data.datastore.settingsDataStore
import se.partee71.reseapoteket.data.household.HouseholdSession
import se.partee71.reseapoteket.testing.FakeHouseholdDirectory

/** Repositories byggs med `FakeCollectionFactory`; här testas bara domänfrågorna. */
class RepositoriesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val collections = FakeCollectionFactory()

    private var settings: SettingsStore? = null

    /**
     * Sessionen och repositoryt. Ett andra anrop är som en omstart: nya instanser, men samma
     * inställningar på enheten.
     */
    private fun TestScope.householdRepository(
        directory: FakeHouseholdDirectory,
        clock: FixedClock = FixedClock(),
    ): Pair<HouseholdSession, DefaultHouseholdRepository> {
        val settings = settings ?: SettingsStore(settingsDataStore(backgroundScope) { folder.newFile("s.preferences_pb") })
            .also { settings = it }
        val session = HouseholdSession(settings, { flowOf(1) }, backgroundScope)
        return session to DefaultHouseholdRepository(collections, directory, session, settings, clock)
    }

    @Test
    fun `nytt hushåll får ägaren som enda medlem, blir aktivt och kan observeras`() = runTest(UnconfinedTestDispatcher()) {
        val (session, repository) = householdRepository(FakeHouseholdDirectory(collections))

        val id = repository.create("Familjen", "uid-anna").getOrThrow()

        assertEquals(id, session.householdId.first { it != null })
        val household = repository.current.first { it != null }!!
        assertEquals(listOf("uid-anna"), household.memberUids)
        assertEquals("Familjen", household.name)
        assertEquals(FixedClock().instant, household.createdAt)
    }

    @Test
    fun `ett nytt försök efter timeout skriver samma hushåll, inte ett till`() = runTest(UnconfinedTestDispatcher()) {
        val directory = FakeHouseholdDirectory(collections)
        val (_, repository) = householdRepository(directory)
        directory.createFailure = DataError.Offline

        assertEquals(DataError.Offline, repository.create("Mitt hushåll", "uid-anna").dataError())
        directory.createFailure = null
        val id = repository.create("Mitt hushåll", "uid-anna").getOrThrow()

        assertEquals(listOf(id), collections.households().getAll().getOrThrow().map { it.id })
        val next = repository.create("Andra", "uid-anna").getOrThrow()
        assertTrue(next != id, "efter en lyckad skapelse får nästa hushåll ett nytt ID")
    }

    @Test
    fun `ett hushåll som inte bekräftades återanvänds även efter omstart`() = runTest(UnconfinedTestDispatcher()) {
        val directory = FakeHouseholdDirectory(collections)
        directory.createFailure = DataError.Offline
        val (_, beforeRestart) = householdRepository(directory)
        assertEquals(DataError.Offline, beforeRestart.create("Mitt hushåll", "uid-anna").dataError())
        val queued = settings!!.pendingHousehold.first()!!

        // Appen startas om, med en senare klocka, innan den köade skrivningen bekräftats.
        directory.createFailure = null
        val (_, afterRestart) = householdRepository(directory, FixedClock(FixedClock().instant + 1.hours))
        val id = afterRestart.create("Mitt hushåll", "uid-anna").getOrThrow()

        assertEquals(queued.id, id)
        val stored = collections.households().getAll().getOrThrow()
        assertEquals(listOf(id), stored.map { it.id }, "ett hushåll, inte två")
        assertEquals(FixedClock().instant, stored.single().createdAt, "skapandetiden från första försöket")
        assertNull(settings!!.pendingHousehold.first(), "bekräftat hushåll glöms")
    }

    @Test
    fun `ett sparat hushåll där användaren inte längre är medlem ersätts av ett nytt`() = runTest(UnconfinedTestDispatcher()) {
        val directory = FakeHouseholdDirectory(collections)
        directory.createFailure = DataError.Offline
        val (_, repository) = householdRepository(directory)
        repository.create("Mitt hushåll", "uid-anna")
        val saved = settings!!.pendingHousehold.first()!!.id
        // Den köade skrivningen nådde servern, och sedan togs Anna bort ur hushållet.
        collections.households().upsert(Household(saved, "Mitt hushåll", listOf("uid-erik"))).getOrThrow()
        directory.createFailure = null

        val id = repository.create("Mitt hushåll", "uid-anna").getOrThrow()

        assertTrue(id != saved)
        assertEquals(listOf("uid-erik"), collections.households().get(saved).getOrThrow()?.memberUids)
    }

    @Test
    fun `att välja ett befintligt hushåll glömmer ett obekräftat skapande`() = runTest(UnconfinedTestDispatcher()) {
        val directory = FakeHouseholdDirectory(collections)
        directory.createFailure = DataError.Offline
        val (session, repository) = householdRepository(directory)
        repository.create("Mitt hushåll", "uid-anna")

        repository.select("befintligt")

        assertNull(settings!!.pendingHousehold.first())
        assertEquals("befintligt", session.householdId.value)
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

    @Test
    fun `replacePackingItems med redan läst lista tar bort det som inte finns kvar i den`() = runTest {
        val repository = DefaultPackingRepository(collections)
        val existing = listOf(PackingItem("a", name = "A"), PackingItem("b", name = "B"))
        repository.replacePackingItems("t1", existing).getOrThrow()

        repository.replacePackingItems("t1", listOf(PackingItem("b", name = "B2")), existing).getOrThrow()

        assertEquals(listOf("B2"), repository.forTrip("t1").getAll().getOrThrow().map { it.name })
    }

    @Test
    fun `packingFor ger packlistorna per resa och följer ändringar`() = runTest {
        val repository = DefaultPackingRepository(collections)
        repository.forTrip("t1").upsert(PackingItem("a", name = "A")).getOrThrow()
        repository.packingFor(listOf("t1", "t2", "t1")).test {
            val first = awaitItem()
            assertEquals(setOf("t1", "t2"), first.keys)
            assertEquals(listOf("A"), first.getValue("t1").map { it.name })
            repository.forTrip("t2").upsert(PackingItem("b", name = "B")).getOrThrow()
            assertEquals(listOf("B"), awaitItem().getValue("t2").map { it.name })
        }
        assertEquals(emptyMap(), repository.packingFor(emptyList()).first())
    }
}
