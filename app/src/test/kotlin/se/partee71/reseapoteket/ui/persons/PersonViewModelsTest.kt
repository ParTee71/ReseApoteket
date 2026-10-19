package se.partee71.reseapoteket.ui.persons

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.core.model.Regimen
import se.partee71.reseapoteket.core.model.RegimenKind
import se.partee71.reseapoteket.core.schema.Schema
import se.partee71.reseapoteket.data.FakeCollectionFactory
import se.partee71.reseapoteket.data.TestHouseholdScope
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.repository.DefaultPersonRepository
import se.partee71.reseapoteket.data.repository.DefaultRegimenRepository
import se.partee71.reseapoteket.testing.MainDispatcherRule
import se.partee71.reseapoteket.ui.common.ArchiveEvent
import se.partee71.reseapoteket.ui.components.PERSON_EMOJIS
import se.partee71.reseapoteket.ui.theme.AppColors
import se.partee71.ui.common.DetailUiState
import se.partee71.ui.common.ListUiState
import se.partee71.ui.components.UndoRequest

/** Personernas ViewModels (PER-1–4) mot `FakeCollection` – ramarnas beteende testas i ramarnas tester. */
class PersonViewModelsTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val scope = TestHouseholdScope()
    private val collections = FakeCollectionFactory(scope = scope)
    private val persons = DefaultPersonRepository(collections)
    private val regimens = DefaultRegimenRepository(collections)

    private fun ListUiState<PersonListItem>.items() = (this as ListUiState.Content).items

    @Test
    fun `listan räknar recept och kosttillskott som inte är arkiverade (PER-1)`() = runTest {
        persons.upsert(Person("anna", "Anna"))
        persons.upsert(Person("lova", "Lova", sortOrder = 1))
        regimens.forPerson("anna").upsert(Regimen("r1", RegimenKind.MEDICINE, "Levaxin"))
        regimens.forPerson("anna").upsert(Regimen("r2", RegimenKind.MEDICINE, "Enalapril"))
        regimens.forPerson("anna").upsert(Regimen("r3", RegimenKind.MEDICINE, "Gammal", archived = true))
        regimens.forPerson("anna").upsert(Regimen("s1", RegimenKind.SUPPLEMENT, "D-vitamin"))

        PersonsViewModel(persons, regimens).state.test {
            assertEquals(
                listOf(PersonListItem(Person("anna", "Anna"), 2, 1), PersonListItem(Person("lova", "Lova", sortOrder = 1), 0, 0)),
                expectMostRecentItem().items()
            )
        }
    }

    @Test
    fun `svep arkiverar, Ångra återställer och Visa arkiverade visar dem sist (PER-3)`() = runTest {
        persons.upsert(Person("anna", "Anna"))
        persons.upsert(Person("erik", "Erik", sortOrder = 1))
        val vm = PersonsViewModel(persons, regimens)
        vm.state.test {
            vm.archive.onEvent(ArchiveEvent.Archive("anna", "Anna"))
            assertEquals(listOf("Erik"), expectMostRecentItem().items().map { it.person.name })
            assertEquals(UndoRequest("anna", "Anna"), vm.archive.undo.value)

            vm.archive.onEvent(ArchiveEvent.ToggleArchived)
            assertTrue(vm.archive.showingArchived.value)
            assertEquals(listOf("Erik", "Anna"), expectMostRecentItem().items().map { it.person.name })

            vm.archive.onEvent(ArchiveEvent.Undo)
            assertNull(vm.archive.undo.value)
            assertFalse(expectMostRecentItem().items().first { it.person.id == "anna" }.person.archived)
        }
    }

    @Test
    fun `ny person sparas sist i listan med standardemoji och -färg och trimmat namn (PER-2)`() = runTest {
        persons.upsert(Person("anna", "Anna", sortOrder = 4))
        val vm = PersonEditViewModel(null, persons)
        assertTrue(vm.isNew)
        assertFalse(vm.state.value.loading)
        vm.state.test {
            vm.onEvent(PersonEditEvent.Name("  Morfar Gunnar "))
            vm.onEvent(PersonEditEvent.Emoji("🦉"))
            vm.onEvent(PersonEditEvent.PersonColor(AppColors.PERSON_HEX[3]))
            vm.onEvent(PersonEditEvent.Save)
            assertTrue(expectMostRecentItem().done)
        }
        val saved = persons.getAll().getOrThrow().single { it.id != "anna" }
        assertEquals(Person(saved.id, "Morfar Gunnar", "🦉", AppColors.PERSON_HEX[3], sortOrder = 5), saved)
    }

    @Test
    fun `ny person börjar med första emojin och färgen och kan inte sparas utan namn`() = runTest {
        val vm = PersonEditViewModel(null, persons)
        assertEquals(PERSON_EMOJIS.first(), vm.state.value.value.emoji)
        assertEquals(AppColors.PERSON_HEX.first(), vm.state.value.value.colorHex)
        vm.onEvent(PersonEditEvent.Name("   "))
        assertFalse(vm.state.value.canSave)
        assertEquals(R.string.name_required, vm.state.value.errorFor(PersonValidator.NAME))
    }

    @Test
    fun `redigering laddar personen och sparar ändringen`() = runTest {
        persons.upsert(Person("anna", "Anna", "🦊", AppColors.PERSON_HEX[0], sortOrder = 2))
        val vm = PersonEditViewModel("anna", persons)
        assertFalse(vm.state.value.loading)
        assertEquals("Anna", vm.state.value.value.name)
        vm.onEvent(PersonEditEvent.Emoji("🐯"))
        vm.state.test {
            vm.onEvent(PersonEditEvent.Save)
            assertTrue(expectMostRecentItem().done)
        }
        assertEquals(Person("anna", "Anna", "🐯", AppColors.PERSON_HEX[0], sortOrder = 2), persons.get("anna").getOrThrow())
    }

    @Test
    fun `en person som inte finns visar läsfel`() = runTest {
        val vm = PersonEditViewModel("borta", persons)
        vm.state.test { assertEquals(DataError.NotFound, expectMostRecentItem().loadError?.cause) }
    }

    @Test
    fun `persondetaljen delar upp recept och kosttillskott och döljer arkiverade (PER-4)`() = runTest {
        persons.upsert(Person("anna", "Anna"))
        regimens.forPerson("anna").upsert(Regimen("r1", RegimenKind.MEDICINE, "Levaxin"))
        regimens.forPerson("anna").upsert(Regimen("r2", RegimenKind.MEDICINE, "Gammal", archived = true))
        regimens.forPerson("anna").upsert(Regimen("s1", RegimenKind.SUPPLEMENT, "D-vitamin"))
        val vm = PersonDetailViewModel("anna", persons, regimens)
        vm.state.test {
            val detail = (expectMostRecentItem() as DetailUiState.Content).value
            assertEquals(listOf("Levaxin"), detail.medicines.map { it.name })
            assertEquals(listOf("D-vitamin"), detail.supplements.map { it.name })

            vm.onEvent(PersonDetailEvent.SetArchived(true))
            assertTrue((expectMostRecentItem() as DetailUiState.Content).value.person.archived)
            vm.onEvent(PersonDetailEvent.SetArchived(false))
            assertFalse((expectMostRecentItem() as DetailUiState.Content).value.person.archived)
        }
    }

    @Test
    fun `persondetaljen för en person som inte finns är ett fel`() = runTest {
        PersonDetailViewModel("borta", persons, regimens).state.test {
            assertEquals(DataError.NotFound, (expectMostRecentItem() as DetailUiState.Error).error.cause)
        }
    }

    @Test
    fun `ett misslyckat arkiverande visas som fel och ger inget ångra (NFR-5)`() = runTest {
        persons.upsert(Person("anna", "Anna"))
        persons.upsert(Person("erik", "Erik"))
        val list = PersonsViewModel(persons, regimens)
        val detail = PersonDetailViewModel("erik", persons, regimens)
        scope.setVersion(Schema.CURRENT_VERSION + 1) // nyare data än appen: skrivskyddat

        list.archive.onEvent(ArchiveEvent.Archive("anna", "Anna"))
        assertEquals(DataError.UpdateRequired, list.archive.error.value?.cause)
        assertNull(list.archive.undo.value)
        list.archive.onEvent(ArchiveEvent.ErrorShown)
        assertNull(list.archive.error.value)

        detail.onEvent(PersonDetailEvent.SetArchived(true))
        assertEquals(DataError.UpdateRequired, detail.error.value?.cause)
        detail.onEvent(PersonDetailEvent.ErrorShown)
        assertNull(detail.error.value)
    }

    @Test
    fun `en person utan emoji eller färg får förvalen i formuläret, en okänd färg står kvar`() = runTest {
        persons.upsert(Person("anna", "Anna"))
        persons.upsert(Person("erik", "Erik", "🦖", "#123456"))
        assertEquals(PERSON_EMOJIS.first(), PersonEditViewModel("anna", persons).state.value.value.emoji)
        assertEquals(AppColors.PERSON_HEX.first(), PersonEditViewModel("anna", persons).state.value.value.colorHex)
        assertFalse(PersonEditViewModel("anna", persons).state.value.isDirty)
        val erik = PersonEditViewModel("erik", persons).state.value.value
        assertEquals("🦖" to "#123456", erik.emoji to erik.colorHex, "en nyare apps värden skrivs inte över")
    }
}
