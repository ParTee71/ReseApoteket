package se.partee71.reseapoteket.ui.packing

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Rule
import org.junit.Test
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.engine.PackingGroup
import se.partee71.reseapoteket.core.engine.PackingProgress
import se.partee71.reseapoteket.core.model.DoseSchedule
import se.partee71.reseapoteket.core.model.FamilyItem
import se.partee71.reseapoteket.core.model.Household
import se.partee71.reseapoteket.core.model.PackingItem
import se.partee71.reseapoteket.core.model.PackingSection
import se.partee71.reseapoteket.core.model.PackingSource
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.core.model.QuantityRule
import se.partee71.reseapoteket.core.model.Regimen
import se.partee71.reseapoteket.core.model.Trip
import se.partee71.reseapoteket.core.model.TripStatus
import se.partee71.reseapoteket.core.schema.Schema
import se.partee71.reseapoteket.core.schema.TripCodec
import se.partee71.reseapoteket.data.FakeCollectionFactory
import se.partee71.reseapoteket.data.FixedClock
import se.partee71.reseapoteket.data.TestHouseholdScope
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.datastore.SettingsStore
import se.partee71.reseapoteket.data.repository.DefaultFamilyItemRepository
import se.partee71.reseapoteket.data.repository.DefaultPackingRepository
import se.partee71.reseapoteket.data.repository.DefaultPersonRepository
import se.partee71.reseapoteket.data.repository.DefaultRegimenRepository
import se.partee71.reseapoteket.data.repository.DefaultTripRepository
import se.partee71.reseapoteket.data.repository.TripRepository
import se.partee71.reseapoteket.domain.PackingUpdate
import se.partee71.reseapoteket.domain.TripPacking
import se.partee71.reseapoteket.testing.FakeDataStore
import se.partee71.reseapoteket.testing.MainDispatcherRule
import se.partee71.reseapoteket.testing.StubHouseholds
import se.partee71.reseapoteket.ui.common.ArchiveEvent
import se.partee71.reseapoteket.ui.common.ListUiState
import se.partee71.reseapoteket.ui.common.TravelDayChange
import se.partee71.reseapoteket.ui.components.UndoRequest

/** Packlistan (PAK-1–5) mot `FakeCollection`; ordningen och statusen testas i `:core`. */
class PackingListViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val collections = FakeCollectionFactory()
    private val trips = WholeTripWrites(DefaultTripRepository(collections))
    private val persons = DefaultPersonRepository(collections)
    private val regimens = DefaultRegimenRepository(collections)
    private val familyItems = DefaultFamilyItemRepository(collections)
    private val packing = DefaultPackingRepository(collections)
    private val tripPacking = TripPacking(trips, persons, regimens, familyItems, StubHouseholds(MutableStateFlow(Household("h1", marginDays = 2))), packing)
    private val settings = SettingsStore(FakeDataStore())
    private val kreta = Trip("t1", "Kreta", LocalDate(2026, 10, 2), 7, listOf("anna", "erik"))

    private fun viewModel() = PackingListViewModel("t1", trips, persons, regimens, packing, settings, tripPacking, FixedClock())

    /** Anna med Levaxin (dagligen) och Erik med Alvedon (vid behov), solkräm, och listan framräknad. */
    private suspend fun seed() {
        persons.upsert(Person("anna", "Anna", sortOrder = 0))
        persons.upsert(Person("erik", "Erik", sortOrder = 1))
        regimens.forPerson("anna").upsert(Regimen("levaxin", name = "Levaxin", strength = "50 µg", schedule = DoseSchedule.Daily(1, 1)))
        regimens.forPerson("erik").upsert(Regimen("alvedon", name = "Alvedon", schedule = DoseSchedule.AsNeeded(10)))
        familyItems.upsert(FamilyItem("solkram", "Solkräm", rule = QuantityRule.Fixed(1)))
        tripPacking.save(kreta, PackingUpdate.CREATE).getOrThrow()
    }

    private fun PackingListViewModel.rows() = (state.value as ListUiState.Content).items
    private fun PackingListViewModel.row(name: String) = rows().first { it.item.name.startsWith(name) }
    private suspend fun stored(id: String) = packing.forTrip("t1").get(id).getOrThrow()!!

    @Test
    fun `listan visar sektion och person i ordning, med kort dosering, och packat i rubriken (PAK-1, PAK-2)`() = runTest {
        seed()
        val vm = viewModel()
        assertEquals(listOf("Levaxin 50 µg", "Alvedon", "Solkräm"), vm.rows().map { it.item.name })
        assertEquals(listOf("Anna", "Erik", null), vm.rows().map { it.person?.name })
        assertEquals(listOf("1/dag", "vid behov", null), vm.rows().map { it.schedule })
        val header = vm.header.value!!
        assertEquals(PackingProgress(0, 3), header.progress)
        assertEquals(listOf("Anna", "Erik"), header.travellers.map { it.name })
        assertEquals(PackingProgress(0, 2), header.groups[PackingGroup.MEDICINE])
    }

    @Test
    fun `avprickning sparar tiden, allt packat gör resan packad och firar en gång, en borttagen avprickning planerad (PAK-5)`() = runTest {
        seed()
        val vm = viewModel()
        vm.celebrations.test {
            vm.rows().forEach { vm.onEvent(PackingListEvent.Check(it.item.id, true)) }
            awaitItem()
            assertTrue(vm.rows().all { it.item.checked })
            assertEquals(FixedClock().now(), stored(vm.row("Solkräm").item.id).checkedAt)
            assertEquals(TripStatus.PACKED, trips.get("t1").getOrThrow()!!.status)

            vm.onEvent(PackingListEvent.Check(vm.row("Alvedon").item.id, false))
            assertNull(stored(vm.row("Alvedon").item.id).checkedAt)
            assertEquals(TripStatus.PLANNING, trips.get("t1").getOrThrow()!!.status)
            expectNoEvents()
        }
    }

    @Test
    fun `statusen följer listan även när en rad döljs eller läggs till (PAK-5)`() = runTest {
        seed()
        val vm = viewModel()
        vm.onEvent(PackingListEvent.Check(vm.row("Levaxin").item.id, true))
        vm.onEvent(PackingListEvent.Check(vm.row("Alvedon").item.id, true))
        vm.celebrations.test {
            val solkram = vm.row("Solkräm").item
            vm.hiding.onEvent(ArchiveEvent.Archive(solkram.id, solkram.name))
            awaitItem()
            assertEquals(TripStatus.PACKED, trips.get("t1").getOrThrow()!!.status, "den sista oavprickade dold – allt packat")
        }
        vm.onEvent(PackingListEvent.Add)
        vm.onEvent(PackingListEvent.AddName("Laddare"))
        vm.onEvent(PackingListEvent.AddSave)
        assertEquals(TripStatus.PLANNING, trips.get("t1").getOrThrow()!!.status, "en ny rad är inte packad")
    }

    @Test
    fun `packlistan skriver bara resans status, aldrig hela resan (PAK-5)`() = runTest {
        seed()
        val vm = viewModel()
        trips.written.clear()
        vm.rows().forEach { vm.onEvent(PackingListEvent.Check(it.item.id, true)) }
        assertEquals(TripStatus.PACKED, trips.get("t1").getOrThrow()!!.status)
        vm.onEvent(PackingListEvent.Recount)
        vm.onEvent(PackingListEvent.Check(vm.row("Solkräm").item.id, false))
        assertEquals(TripStatus.PLANNING, trips.get("t1").getOrThrow()!!.status)
        assertEquals(emptyList(), trips.written, "en ändring av resan någon annanstans skrivs inte över")
        assertEquals(listOf(setOf(TripCodec.STATUS), setOf(TripCodec.STATUS)), trips.fields)
    }

    @Test
    fun `Dölj packade på en packad lista packar inte upp resan och firar inte igen (PAK-5)`() = runTest {
        seed()
        val vm = viewModel()
        vm.celebrations.test {
            vm.rows().forEach { vm.onEvent(PackingListEvent.Check(it.item.id, true)) }
            awaitItem()
            vm.onEvent(PackingListEvent.HideChecked)
            assertTrue(vm.rows().all { it.item.hidden })
            assertEquals(TripStatus.PACKED, trips.get("t1").getOrThrow()!!.status)
            vm.onEvent(PackingListEvent.Show(vm.row("Solkräm").item.id))
            assertEquals(TripStatus.PACKED, trips.get("t1").getOrThrow()!!.status)
            expectNoEvents()
        }
    }

    @Test
    fun `att bara öppna en packad lista firar inte och ändrar ingenting`() = runTest {
        seed()
        packing.forTrip("t1").getAll().getOrThrow().forEach { packing.forTrip("t1").upsert(it.copy(checked = true)) }
        val vm = viewModel()
        vm.celebrations.test { expectNoEvents() }
        assertEquals(TripStatus.PLANNING, trips.get("t1").getOrThrow()!!.status, "statusen följer bara egna ändringar")
    }

    @Test
    fun `ett skrivfel visas i den öppna panelen, annars i listan, och försvinner när man ändrar`() = runTest {
        seed()
        val blocked = DefaultPackingRepository(FakeCollectionFactory(collections.store, TestHouseholdScope(version = Schema.CURRENT_VERSION + 1)))
        val vm = PackingListViewModel("t1", trips, persons, regimens, blocked, settings, tripPacking, FixedClock())
        vm.onEvent(PackingListEvent.Check(vm.row("Levaxin").item.id, true))
        assertEquals(DataError.UpdateRequired, vm.hiding.error.value)
        assertNull(vm.sheetError.value)

        vm.onEvent(PackingListEvent.Open(vm.row("Levaxin").item.id))
        vm.onEvent(PackingListEvent.Quantity(5))
        assertEquals(DataError.UpdateRequired, vm.sheetError.value)
        vm.onEvent(PackingListEvent.CloseQuantity)
        assertNull(vm.sheetError.value)

        vm.onEvent(PackingListEvent.Add)
        vm.onEvent(PackingListEvent.AddName("Laddare"))
        vm.onEvent(PackingListEvent.AddSave)
        assertEquals(DataError.UpdateRequired, vm.sheetError.value)
        vm.onEvent(PackingListEvent.AddName("Laddare 2"))
        assertNull(vm.sheetError.value, "ett gammalt fel står inte kvar medan raden ändras")
    }

    @Test
    fun `avprickade hamnar sist hos personen när inställningen är på (SET-3)`() = runTest {
        seed()
        regimens.forPerson("anna").upsert(Regimen("zyrtec", name = "Zyrtec", schedule = DoseSchedule.Daily(1, 1)))
        tripPacking.save(kreta, PackingUpdate.RECOUNT).getOrThrow()
        val vm = viewModel()
        vm.onEvent(PackingListEvent.Check(vm.row("Levaxin").item.id, true))
        assertEquals(listOf("Zyrtec", "Levaxin 50 µg"), vm.rows().filter { it.person?.id == "anna" }.map { it.item.name })
        settings.setMoveCheckedLast(false)
        assertEquals(listOf("Levaxin 50 µg", "Zyrtec"), vm.rows().filter { it.person?.id == "anna" }.map { it.item.name })
    }

    @Test
    fun `mängden ändras och återställs till förslaget (PAK-3)`() = runTest {
        seed()
        val vm = viewModel()
        val levaxin = vm.row("Levaxin").item
        vm.onEvent(PackingListEvent.Open(levaxin.id))
        assertEquals(levaxin.id, vm.editing.value?.item?.id)
        vm.onEvent(PackingListEvent.Quantity(12))
        assertEquals(12, stored(levaxin.id).quantity)
        assertEquals(12, vm.editing.value?.item?.quantity)
        vm.onEvent(PackingListEvent.ResetQuantity)
        assertEquals(levaxin.suggestedQuantity, stored(levaxin.id).quantity)
        vm.onEvent(PackingListEvent.CloseQuantity)
        assertNull(vm.editing.value)
    }

    @Test
    fun `svep döljer med Ångra, en dold rad visas igen, och Dölj packade döljer avprickade (PAK-4)`() = runTest {
        seed()
        val vm = viewModel()
        val solkram = vm.row("Solkräm").item
        vm.hiding.onEvent(ArchiveEvent.Archive(solkram.id, solkram.name))
        assertTrue(stored(solkram.id).hidden)
        assertEquals(UndoRequest(solkram.id, "Solkräm", R.string.hidden_format), vm.hiding.undo.value)
        assertEquals("Solkräm", vm.rows().last().item.name, "dolda sist")
        assertEquals(PackingProgress(0, 2), vm.header.value!!.progress, "dolda räknas inte")
        vm.hiding.onEvent(ArchiveEvent.Undo)
        assertFalse(stored(solkram.id).hidden)

        vm.onEvent(PackingListEvent.Check(solkram.id, true))
        vm.onEvent(PackingListEvent.HideChecked)
        assertTrue(stored(solkram.id).hidden)
        vm.onEvent(PackingListEvent.Show(solkram.id))
        assertFalse(stored(solkram.id).hidden)
    }

    @Test
    fun `en egen rad läggs till, en medicin kräver en person (PAK-4)`() = runTest {
        seed()
        val vm = viewModel()
        vm.onEvent(PackingListEvent.Add)
        assertTrue(vm.adding.value)
        vm.onEvent(PackingListEvent.AddName("Nässpray"))
        vm.onEvent(PackingListEvent.AddSection(PackingSection.MEDICINE))
        vm.onEvent(PackingListEvent.AddSave)
        assertEquals(R.string.packing_person_required, vm.addEditor.state.value.errorFor(ManualItemValidator.PERSON))
        assertTrue(vm.adding.value)
        vm.onEvent(PackingListEvent.AddPerson("erik"))
        vm.onEvent(PackingListEvent.AddQuantity(2))
        vm.onEvent(PackingListEvent.AddSave)
        assertFalse(vm.adding.value)
        val added = vm.row("Nässpray")
        assertEquals(PackingSource.MANUAL, added.item.sourceType)
        assertEquals("Erik", added.person?.name)
        assertEquals(2, added.item.quantity)
        assertEquals("", vm.addEditor.state.value.value.name, "panelen börjar om tom")
    }

    @Test
    fun `Räkna om frågar först och behåller avprickning och egna rader (RES-4)`() = runTest {
        seed()
        val vm = viewModel()
        vm.onEvent(PackingListEvent.Check(vm.row("Levaxin").item.id, true))
        packing.forTrip("t1").upsert(PackingItem("egen", name = "Laddare", sourceType = PackingSource.MANUAL, sortOrder = 9))
        trips.upsert(kreta.copy(days = 10))
        vm.onEvent(PackingListEvent.AskRecount)
        assertTrue(vm.askRecount.value)
        vm.onEvent(PackingListEvent.Recount)
        assertFalse(vm.askRecount.value)
        assertTrue(vm.row("Levaxin").item.checked)
        assertEquals(12, vm.row("Levaxin").item.quantity, "1 × (10 + 2) dagar")
        assertTrue(vm.rows().any { it.item.name == "Laddare" })
    }

    @Test
    fun `en egen rad som packas på resedagen hamnar sist i På resedagen med egen räknare (PAK-6)`() = runTest {
        seed()
        val vm = viewModel()
        vm.onEvent(PackingListEvent.Add)
        vm.onEvent(PackingListEvent.AddName("Laddare"))
        vm.onEvent(PackingListEvent.AddTravelDay(TravelDayChange(true, LocalTime(8, 30))))
        vm.onEvent(PackingListEvent.AddSave)
        val last = vm.rows().last { !it.item.hidden }.item
        assertEquals("Laddare" to LocalTime(8, 30), last.name to last.travelDayTime)
        assertEquals(PackingProgress(0, 1), vm.header.value!!.groups[PackingGroup.TRAVEL_DAY])
    }

    @Test
    fun `en resa som inte finns visar finns inte längre och går inte att lägga till under (#88)`() = runTest {
        val vm = PackingListViewModel("okand", trips, persons, regimens, packing, settings, tripPacking, FixedClock())
        assertEquals(ListUiState.Error(DataError.NotFound), vm.state.value)
        seed()
        val open = viewModel()
        assertEquals(3, open.rows().size)
        trips.delete("t1")
        assertEquals(ListUiState.Error(DataError.NotFound), open.state.value)
    }
}

/** Skrivningar av resor: hela resor ([written]) och fältvisa ([fields]) – packlistan får bara skriva statusen (PAK-5). */
private class WholeTripWrites(private val trips: TripRepository) : TripRepository by trips {
    val written = mutableListOf<Trip>()
    val fields = mutableListOf<Set<String>>()

    override suspend fun upsert(item: Trip): Result<Unit> = trips.upsert(item).onSuccess { written += item }

    override suspend fun batch(upserts: List<Trip>, deletes: List<String>): Result<Unit> = trips.batch(upserts, deletes).onSuccess { written += upserts }

    override suspend fun update(item: Trip, fields: Set<String>): Result<Unit> = trips.update(item, fields).onSuccess { this.fields += fields }

    override suspend fun updateAll(items: List<Trip>, fields: Set<String>): Result<Unit> = trips.updateAll(items, fields).onSuccess { this.fields += fields }
}
