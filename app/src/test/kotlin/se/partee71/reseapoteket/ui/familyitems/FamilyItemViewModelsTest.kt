package se.partee71.reseapoteket.ui.familyitems

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.model.FamilyItem
import se.partee71.reseapoteket.core.model.FamilyItemCategory
import se.partee71.reseapoteket.core.model.Household
import se.partee71.reseapoteket.core.model.ItemUnit
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.core.model.QuantityRule
import se.partee71.reseapoteket.data.FakeCollectionFactory
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.repository.DefaultFamilyItemRepository
import se.partee71.reseapoteket.data.repository.DefaultPersonRepository
import se.partee71.reseapoteket.testing.MainDispatcherRule
import se.partee71.reseapoteket.testing.StubHouseholds
import se.partee71.reseapoteket.ui.common.ArchiveEvent
import se.partee71.reseapoteket.ui.common.EditorEffect
import se.partee71.reseapoteket.ui.common.ListUiState
import se.partee71.reseapoteket.ui.components.UndoRequest

/** Familjeartiklarna (FAM-1–4) mot `FakeCollection` – ramarnas beteende testas i ramarnas tester. */
class FamilyItemViewModelsTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val collections = FakeCollectionFactory()
    private val items = DefaultFamilyItemRepository(collections)
    private val persons = DefaultPersonRepository(collections)
    private val household = MutableStateFlow<Household?>(Household("h1", marginDays = 2))

    private fun editor(itemId: String? = null, example: FamilyItemExample? = null, name: String = "") =
        FamilyItemEditViewModel(itemId, example, name, items, persons, StubHouseholds(household))

    private val sunscreen = FamilyItem("f1", "Solkräm", FamilyItemCategory.CREAM, ItemUnit.TUB, QuantityRule.PerDays(1, 7), "Faktor 50", sortOrder = 2)

    private fun ListUiState<FamilyItem>.names() = (this as ListUiState.Content).items.map { it.name }

    @Test
    fun `listan grupperas i kategoriernas ordning, och arkiverade visas sist i sin kategori (FAM-1)`() = runTest {
        items.upsert(FamilyItem("t", "Febertermometer", FamilyItemCategory.TOOL))
        items.upsert(FamilyItem("p", "Plåster", FamilyItemCategory.PLASTER))
        items.upsert(sunscreen)
        items.upsert(FamilyItem("h", "Hydrokortison", FamilyItemCategory.CREAM, archived = true))
        val vm = FamilyItemsViewModel(items)
        vm.state.test {
            assertEquals(listOf("Solkräm", "Plåster", "Febertermometer"), expectMostRecentItem().names())
            vm.archive.onEvent(ArchiveEvent.ToggleArchived)
            assertEquals(listOf("Solkräm", "Hydrokortison", "Plåster", "Febertermometer"), expectMostRecentItem().names())
        }
    }

    @Test
    fun `svep arkiverar och Ångra återställer (FAM-2)`() = runTest {
        items.upsert(sunscreen)
        val vm = FamilyItemsViewModel(items)
        vm.state.test {
            vm.archive.onEvent(ArchiveEvent.Archive("f1", "Solkräm"))
            assertEquals(ListUiState.Empty, expectMostRecentItem())
            assertEquals(UndoRequest("f1", "Solkräm"), vm.archive.undo.value)
            vm.archive.onEvent(ArchiveEvent.Undo)
            assertEquals(listOf("Solkräm"), expectMostRecentItem().names())
            assertNull(vm.archive.undo.value)
        }
    }

    @Test
    fun `en ny artikel börjar som 1 st i Övrigt och sparas sist med trimmade texter (FAM-2)`() = runTest {
        items.upsert(sunscreen)
        val vm = editor()
        assertTrue(vm.isNew)
        assertEquals(FamilyItem(vm.state.value.value.id, rule = QuantityRule.Fixed(1)), vm.state.value.value)
        vm.effects.test {
            vm.onEvent(FamilyItemEditEvent.Name("  Myggmedel "))
            vm.onEvent(FamilyItemEditEvent.Rule(QuantityRule.PerTraveller(1)))
            vm.onEvent(FamilyItemEditEvent.Notes(" Spray "))
            vm.onEvent(FamilyItemEditEvent.Save)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        val saved = items.getAll().getOrThrow().single { it.id != "f1" }
        assertEquals(FamilyItem(saved.id, "Myggmedel", rule = QuantityRule.PerTraveller(1), notes = "Spray", sortOrder = 3), saved)
    }

    @Test
    fun `ett exempel fyller i formuläret men sparar inget förrän användaren sparar (FAM-4)`() = runTest {
        val vm = editor(example = FamilyItemExample.SUNSCREEN, name = "Solkräm")
        val value = vm.state.value.value
        assertEquals(FamilyItem(value.id, "Solkräm", FamilyItemCategory.CREAM, ItemUnit.TUB, QuantityRule.PerDays(1, 7)), value)
        assertTrue(vm.state.value.canSave, "exemplet kan sparas direkt")
        assertTrue(vm.state.value.isDirty, "bakåt frågar Släng ändringar?")
        assertEquals(emptyList(), items.getAll().getOrThrow())
    }

    @Test
    fun `förhandsvisningen räknar hushållets personer och marginal och följer formuläret (FAM-3)`() = runTest {
        persons.upsert(Person("anna", "Anna"))
        persons.upsert(Person("erik", "Erik"))
        persons.upsert(Person("lova", "Lova", archived = true))
        items.upsert(sunscreen.copy(rule = QuantityRule.PerTraveller(10), unit = ItemUnit.ST))
        val vm = editor("f1")
        vm.preview.test {
            val preview = checkNotNull(expectMostRecentItem())
            assertEquals("För 2 resenärer i 7 dagar", preview.context)
            assertEquals("20 st", preview.quantity)
            vm.onEvent(FamilyItemEditEvent.Rule(QuantityRule.PerDays(1, 7)))
            vm.onEvent(FamilyItemEditEvent.UnitChoice(ItemUnit.TUB))
            assertEquals("2 tuber", expectMostRecentItem()?.quantity)
            household.value = Household("h1", marginDays = 0)
            assertEquals("1 tub", expectMostRecentItem()?.quantity)
        }
    }

    @Test
    fun `arkivera och återställ från formuläret stänger det (FAM-2)`() = runTest {
        items.upsert(sunscreen)
        val vm = editor("f1")
        assertFalse(vm.stored.value!!.archived)
        vm.effects.test {
            vm.onEvent(FamilyItemEditEvent.Archive)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        assertTrue(items.get("f1").getOrThrow()!!.archived)
        val again = editor("f1")
        assertTrue(again.stored.value!!.archived)
        again.effects.test {
            again.onEvent(FamilyItemEditEvent.Restore)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        assertFalse(items.get("f1").getOrThrow()!!.archived)
    }

    @Test
    fun `en okänd regel från en nyare app skrivs tillbaka orörd (BCK-5)`() = runTest {
        val future = sunscreen.copy(rule = QuantityRule.Unknown("framtida"))
        items.upsert(future)
        val vm = editor("f1")
        vm.onEvent(FamilyItemEditEvent.Notes("Ny"))
        vm.effects.test {
            vm.onEvent(FamilyItemEditEvent.Save)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        assertEquals(future.copy(notes = "Ny"), items.get("f1").getOrThrow())
    }

    @Test
    fun `en artikel som inte finns visar läsfel`() = runTest {
        editor("borta").state.test { assertEquals(DataError.NotFound, expectMostRecentItem().loadError) }
    }

    @Test
    fun `validering - namn och en mängd på minst 1 (FAM-2, FAM-3)`() {
        fun errors(rule: QuantityRule, name: String = "Solkräm") = FamilyItemValidator.validate(sunscreen.copy(name = name, rule = rule))
        val rule = FamilyItemValidator.RULE
        assertEquals(mapOf(FamilyItemValidator.NAME to R.string.name_required), errors(QuantityRule.Fixed(1), name = " "))
        assertEquals(emptyMap(), errors(QuantityRule.Fixed(1)))
        assertEquals(mapOf(rule to R.string.rule_min_one), errors(QuantityRule.Fixed(0)))
        assertEquals(mapOf(rule to R.string.rule_min_one), errors(QuantityRule.PerTraveller(0)))
        assertEquals(mapOf(rule to R.string.rule_min_one), errors(QuantityRule.PerDays(1, 0)))
        assertEquals(emptyMap(), errors(QuantityRule.PerDays(1, 7)))
        assertEquals(emptyMap(), errors(QuantityRule.Unknown("framtida")))
    }

    @Test
    fun `en artikel som packas på resedagen sparas med valet (PAK-6)`() = runTest {
        val vm = editor()
        vm.effects.test {
            vm.onEvent(FamilyItemEditEvent.Name("Tandborste"))
            vm.onEvent(FamilyItemEditEvent.TravelDay(true, null))
            vm.onEvent(FamilyItemEditEvent.Save)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        val saved = items.getAll().getOrThrow().single()
        assertEquals(true to null, saved.travelDay to saved.travelDayTime)
    }
}
