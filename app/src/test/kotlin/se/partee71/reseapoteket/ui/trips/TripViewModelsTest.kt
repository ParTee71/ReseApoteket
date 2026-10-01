package se.partee71.reseapoteket.ui.trips

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.engine.PackingProgress
import se.partee71.reseapoteket.core.model.DoseSchedule
import se.partee71.reseapoteket.core.model.FamilyItem
import se.partee71.reseapoteket.core.model.Household
import se.partee71.reseapoteket.core.model.PackingItem
import se.partee71.reseapoteket.core.model.PackingSource
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.core.model.QuantityRule
import se.partee71.reseapoteket.core.model.Regimen
import se.partee71.reseapoteket.core.model.Trip
import se.partee71.reseapoteket.core.schema.Schema
import se.partee71.reseapoteket.core.model.TripStatus
import se.partee71.reseapoteket.data.FakeCollectionFactory
import se.partee71.reseapoteket.data.FixedClock
import se.partee71.reseapoteket.data.TestHouseholdScope
import se.partee71.reseapoteket.data.repository.DefaultFamilyItemRepository
import se.partee71.reseapoteket.data.repository.DefaultPackingRepository
import se.partee71.reseapoteket.data.repository.DefaultPersonRepository
import se.partee71.reseapoteket.data.repository.DefaultRegimenRepository
import se.partee71.reseapoteket.data.repository.DefaultTripRepository
import se.partee71.reseapoteket.domain.PackingUpdate
import se.partee71.reseapoteket.domain.TripPacking
import se.partee71.reseapoteket.testing.MainDispatcherRule
import se.partee71.reseapoteket.testing.StubHouseholds
import se.partee71.reseapoteket.ui.common.ArchiveEvent
import se.partee71.reseapoteket.ui.common.EditorEffect
import se.partee71.reseapoteket.ui.common.ListUiState
import se.partee71.reseapoteket.ui.components.UndoRequest

/** Resorna (RES-1–4) mot `FakeCollection` – motorn testas i `:core`, ramarna i ramarnas tester. */
class TripViewModelsTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val collections = FakeCollectionFactory()
    private val trips = DefaultTripRepository(collections)
    private val persons = DefaultPersonRepository(collections)
    private val regimens = DefaultRegimenRepository(collections)
    private val familyItems = DefaultFamilyItemRepository(collections)
    private val packing = DefaultPackingRepository(collections)
    private val household = MutableStateFlow<Household?>(Household("h1", marginDays = 2))
    private val tripPacking = TripPacking(trips, persons, regimens, familyItems, StubHouseholds(household), packing)

    // FixedClock = mån 21 sep 2026 (UTC): en ny resa börjar i morgon.
    private fun editor(tripId: String? = null) = TripEditViewModel(tripId, trips, persons, tripPacking, FixedClock(), TimeZone.UTC)

    private suspend fun seedHousehold() {
        persons.upsert(Person("anna", "Anna", sortOrder = 0))
        persons.upsert(Person("erik", "Erik", sortOrder = 1))
        persons.upsert(Person("lova", "Lova", archived = true))
        regimens.forPerson("anna").upsert(Regimen("levaxin", name = "Levaxin", schedule = DoseSchedule.Daily(1, 1)))
        familyItems.upsert(FamilyItem("solkram", "Solkräm", rule = QuantityRule.Fixed(1)))
    }

    /** Hushållet och resan "Kreta" (t1, Anna, 7 dagar) med sin packlista. */
    private suspend fun seedTripWithList() {
        seedHousehold()
        tripPacking.save(Trip("t1", "Kreta", LocalDate(2026, 10, 1), 7, listOf("anna")), PackingUpdate.CREATE).getOrThrow()
    }

    private suspend fun items(tripId: String) = packing.forTrip(tripId).getAll().getOrThrow()

    @Test
    fun `en ny resa börjar i morgon, 7 dagar, med alla som inte är arkiverade och kan skapas direkt (RES-2)`() = runTest {
        seedHousehold()
        val vm = editor()
        val trip = vm.state.value.value
        assertEquals(LocalDate(2026, 9, 22), trip.startDate)
        assertEquals(7, trip.days)
        assertEquals(setOf("anna", "erik"), trip.travellerIds.toSet())
        assertTrue(vm.state.value.canSave)
        assertEquals(listOf("Anna", "Erik"), vm.people.value.map { it.name })
    }

    @Test
    fun `Skapa packlista sparar resan med förvalt namn och skriver packlistan (RES-2, RES-3)`() = runTest {
        seedHousehold()
        val vm = editor()
        vm.effects.test {
            vm.onEvent(TripEditEvent.Save(defaultName = "Resa 22 sep"))
            assertEquals(EditorEffect.Done, awaitItem())
        }
        val saved = trips.getAll().getOrThrow().single()
        assertEquals("Resa 22 sep", saved.name)
        assertNotNull(saved.createdAt)
        assertEquals(setOf("Levaxin", "Solkräm"), items(saved.id).map { it.name }.toSet())
    }

    @Test
    fun `sammanfattningen visar vad packlistan blir (RES-3)`() = runTest {
        seedHousehold()
        val vm = editor()
        vm.preview.test {
            val preview = checkNotNull(expectMostRecentItem())
            assertEquals("För 2 resenärer i 7 dagar", preview.context)
            assertEquals("2 saker att packa", preview.quantity)
            assertEquals("1 recept · 1 familjeartikel", preview.reason)
            vm.onEvent(TripEditEvent.Traveller("anna"))
            assertEquals("1 sak att packa", expectMostRecentItem()?.quantity)
        }
    }

    @Test
    fun `utan resenär kan resan inte skapas (RES-2)`() = runTest {
        seedHousehold()
        val vm = editor()
        vm.onEvent(TripEditEvent.Traveller("anna"))
        vm.onEvent(TripEditEvent.Traveller("erik"))
        assertFalse(vm.state.value.canSave)
        assertEquals(R.string.trip_travellers_required, vm.state.value.errorFor(TripValidator.TRAVELLERS))
    }

    @Test
    fun `ändrade dagar frågar först, och Räkna om behåller avprickning och egna rader (RES-4)`() = runTest {
        seedTripWithList()
        val levaxin = items("t1").single { it.name == "Levaxin" }
        packing.forTrip("t1").upsert(levaxin.copy(checked = true))
        packing.forTrip("t1").upsert(PackingItem("egen", name = "Laddare", sourceType = PackingSource.MANUAL))

        val vm = editor("t1")
        vm.onEvent(TripEditEvent.Days(10))
        vm.effects.test {
            vm.onEvent(TripEditEvent.Save(""))
            assertTrue(vm.askRecount.value, "Spara frågar först")
            vm.onEvent(TripEditEvent.Recount(true))
            assertEquals(EditorEffect.Done, awaitItem())
        }
        val after = items("t1")
        val recounted = after.single { it.name == "Levaxin" }
        assertTrue(recounted.checked, "avprickningen behålls")
        assertEquals(12, recounted.suggestedQuantity, "1 × (10 + 2) dagar")
        assertTrue(after.any { it.name == "Laddare" }, "egna rader rörs inte")
        assertEquals(10, trips.get("t1").getOrThrow()!!.days)
    }

    @Test
    fun `Behåll listan sparar resan men rör inte packlistan (RES-4)`() = runTest {
        seedTripWithList()
        val before = items("t1")
        val vm = editor("t1")
        vm.onEvent(TripEditEvent.Traveller("erik"))
        vm.effects.test {
            vm.onEvent(TripEditEvent.Save(""))
            vm.onEvent(TripEditEvent.Recount(false))
            assertEquals(EditorEffect.Done, awaitItem())
        }
        assertEquals(before, items("t1"))
        assertEquals(listOf("anna", "erik"), trips.get("t1").getOrThrow()!!.travellerIds)
    }

    @Test
    fun `bara ett nytt namn frågar inte om omräkning`() = runTest {
        seedHousehold()
        trips.upsert(Trip("t1", "Kreta", LocalDate(2026, 10, 1), 7, listOf("anna")))
        val vm = editor("t1")
        vm.onEvent(TripEditEvent.Name("Kreta igen"))
        vm.effects.test {
            vm.onEvent(TripEditEvent.Save(""))
            assertFalse(vm.askRecount.value)
            assertEquals(EditorEffect.Done, awaitItem())
        }
    }

    @Test
    fun `avbryt frågan sparar ingenting`() = runTest {
        seedHousehold()
        trips.upsert(Trip("t1", "Kreta", LocalDate(2026, 10, 1), 7, listOf("anna")))
        val vm = editor("t1")
        vm.onEvent(TripEditEvent.Days(3))
        vm.onEvent(TripEditEvent.Save(""))
        vm.onEvent(TripEditEvent.CancelRecount)
        assertFalse(vm.askRecount.value)
        assertEquals(7, trips.get("t1").getOrThrow()!!.days)
        assertTrue(vm.state.value.isDirty)
    }

    @Test
    fun `listan visar resenärer och packat, nyaste först, och svep arkiverar (RES-1)`() = runTest {
        seedHousehold()
        trips.upsert(Trip("old", "Göteborg", LocalDate(2026, 5, 15), 2, listOf("anna"), TripStatus.DONE))
        trips.upsert(Trip("new", "Kreta", LocalDate(2026, 10, 1), 7, listOf("anna", "erik")))
        packing.forTrip("new").upsert(PackingItem("a", name = "A", checked = true))
        packing.forTrip("new").upsert(PackingItem("b", name = "B"))
        packing.forTrip("new").upsert(PackingItem("c", name = "C", hidden = true, checked = true))
        val vm = TripsViewModel(trips, persons, packing)
        vm.state.test {
            val list = (expectMostRecentItem() as ListUiState.Content).items
            assertEquals(listOf("Kreta", "Göteborg"), list.map { it.trip.name })
            assertEquals(listOf("Anna", "Erik"), list.first().travellers.map { it.name })
            assertEquals(PackingProgress(1, 2), list.first().progress, "dolda rader räknas inte")
            vm.archive.onEvent(ArchiveEvent.Archive("old", "Göteborg"))
            assertEquals(listOf("Kreta"), (expectMostRecentItem() as ListUiState.Content).items.map { it.trip.name })
            assertEquals(UndoRequest("old", "Göteborg"), vm.archive.undo.value)
            vm.archive.onEvent(ArchiveEvent.Undo)
            assertEquals(listOf("Kreta", "Göteborg"), (expectMostRecentItem() as ListUiState.Content).items.map { it.trip.name })
        }
    }

    @Test
    fun `packat följer packlistan medan resan är densamma (RES-1)`() = runTest {
        trips.upsert(Trip("t1", "Kreta", LocalDate(2026, 10, 1), 7))
        packing.forTrip("t1").upsert(PackingItem("a", name = "A"))
        val vm = TripsViewModel(trips, persons, packing)
        vm.state.test {
            assertEquals(PackingProgress(0, 1), (expectMostRecentItem() as ListUiState.Content).items.single().progress)
            packing.forTrip("t1").upsert(PackingItem("a", name = "A", checked = true))
            assertEquals(PackingProgress(1, 1), (expectMostRecentItem() as ListUiState.Content).items.single().progress)
        }
    }

    @Test
    fun `en ny resa utan läsbart hushåll kraschar inte – resenärerna väljs själv (RES-2)`() = runTest {
        val noHousehold = FakeCollectionFactory(scope = TestHouseholdScope(householdId = null))
        val people = DefaultPersonRepository(noHousehold)
        val vm = TripEditViewModel(null, DefaultTripRepository(noHousehold), people, TripPacking(DefaultTripRepository(noHousehold), people, DefaultRegimenRepository(noHousehold), DefaultFamilyItemRepository(noHousehold), StubHouseholds(household), DefaultPackingRepository(noHousehold)), FixedClock(), TimeZone.UTC)
        assertEquals(emptyList(), vm.state.value.value.travellerIds)
        assertFalse(vm.state.value.canSave)
    }

    @Test
    fun `en arkiverad resenär i den sparade resan går att välja bort och välja igen`() = runTest {
        seedHousehold()
        trips.upsert(Trip("t1", "Kreta", LocalDate(2026, 10, 1), 7, listOf("anna", "lova")))
        val vm = editor("t1")
        vm.people.test {
            assertEquals(setOf("Anna", "Erik", "Lova"), expectMostRecentItem().map { it.name }.toSet())
            vm.onEvent(TripEditEvent.Traveller("lova"))
            vm.onEvent(TripEditEvent.Traveller("lova"))
            assertEquals(setOf("Anna", "Erik", "Lova"), vm.people.value.map { it.name }.toSet(), "Lova finns kvar att välja")
            assertEquals(listOf("anna", "lova"), vm.state.value.value.travellerIds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Packlistor som inte går att skriva (nyare data än appen), resten som vanligt. */
    private fun failingPacking() = TripPacking(
        trips, persons, regimens, familyItems, StubHouseholds(household),
        DefaultPackingRepository(FakeCollectionFactory(collections.store, TestHouseholdScope(version = Schema.CURRENT_VERSION + 1))),
    )

    @Test
    fun `går packlistan inte att skriva sparas ingen ny resa (RES-3)`() = runTest {
        seedHousehold()
        val vm = TripEditViewModel(null, trips, persons, failingPacking(), FixedClock(), TimeZone.UTC)
        vm.effects.test {
            vm.onEvent(TripEditEvent.Save(defaultName = "Resa 22 sep"))
            assertTrue(awaitItem() is EditorEffect.Failed)
        }
        assertEquals(emptyList(), trips.getAll().getOrThrow())
    }

    @Test
    fun `misslyckas omräkningen sparas ingenting och formuläret står kvar (RES-4)`() = runTest {
        seedHousehold()
        trips.upsert(Trip("t1", "Kreta", LocalDate(2026, 10, 1), 7, listOf("anna")))
        val vm = TripEditViewModel("t1", trips, persons, failingPacking(), FixedClock(), TimeZone.UTC)
        vm.onEvent(TripEditEvent.Days(10))
        vm.effects.test {
            vm.onEvent(TripEditEvent.Save(""))
            vm.onEvent(TripEditEvent.Recount(true))
            assertTrue(awaitItem() is EditorEffect.Failed)
        }
        assertEquals(7, trips.get("t1").getOrThrow()!!.days, "resan och listan hänger ihop")
        assertTrue(vm.state.value.isDirty)
    }

    @Test
    fun `resor med samma start sorteras i svensk ordning (RES-1)`() = runTest {
        trips.upsert(Trip("o", "Örebro", LocalDate(2026, 10, 1), 2))
        trips.upsert(Trip("a", "Åre", LocalDate(2026, 10, 1), 2))
        trips.upsert(Trip("z", "Zürich", LocalDate(2026, 10, 1), 2))
        TripsViewModel(trips, persons, packing).state.test {
            assertEquals(listOf("Zürich", "Åre", "Örebro"), (expectMostRecentItem() as ListUiState.Content).items.map { it.trip.name })
        }
    }
}
