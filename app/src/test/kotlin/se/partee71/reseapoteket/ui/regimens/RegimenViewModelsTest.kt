package se.partee71.reseapoteket.ui.regimens

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.model.DoseSchedule
import se.partee71.reseapoteket.core.model.Household
import se.partee71.reseapoteket.core.model.ItemUnit
import se.partee71.reseapoteket.core.model.MedicineForm
import se.partee71.reseapoteket.core.model.Regimen
import se.partee71.reseapoteket.core.model.RegimenKind
import se.partee71.reseapoteket.data.FakeCollectionFactory
import se.partee71.reseapoteket.data.FixedClock
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.repository.DefaultRegimenRepository
import se.partee71.reseapoteket.data.repository.HouseholdRepository
import se.partee71.reseapoteket.testing.MainDispatcherRule
import se.partee71.reseapoteket.ui.common.EditorEffect

/** Receptformuläret (REC-1–5) mot `FakeCollection` – ramens beteende testas i ramens tester. */
class RegimenViewModelsTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val collections = FakeCollectionFactory()
    private val regimens = DefaultRegimenRepository(collections)
    private val anna = regimens.forPerson("anna")
    private val household = MutableStateFlow<Household?>(Household("h1", marginDays = 2))

    private fun viewModel(regimenId: String? = null, kind: RegimenKind = RegimenKind.MEDICINE) =
        RegimenEditViewModel("anna", kind, regimenId, regimens, StubHouseholds(household), FixedClock(), TimeZone.UTC)

    private val levaxin = Regimen(
        "r1", RegimenKind.MEDICINE, "Levaxin", MedicineForm.TABLET, "50 µg", ItemUnit.TABLETT, 100,
        DoseSchedule.Daily(1, 1), "På fastande mage", sortOrder = 3,
    )

    @Test
    fun `nytt recept börjar som en tablett dagligen och sparas sist med trimmade texter (REC-1)`() = runTest {
        anna.upsert(levaxin)
        val vm = viewModel()
        assertTrue(vm.isNew)
        assertFalse(vm.state.value.loading)
        assertEquals(DoseSchedule.Daily(1, 1), vm.state.value.value.schedule)
        assertEquals(MedicineForm.TABLET, vm.state.value.value.form)
        assertEquals(ItemUnit.TABLETT, vm.state.value.value.unit)
        vm.effects.test {
            vm.onEvent(RegimenEditEvent.Name("  Alvedon "))
            vm.onEvent(RegimenEditEvent.Strength(" 500 mg "))
            vm.onEvent(RegimenEditEvent.Form(MedicineForm.CAPSULE))
            vm.onEvent(RegimenEditEvent.DoseUnit(ItemUnit.KAPSEL))
            vm.onEvent(RegimenEditEvent.Schedule(DoseSchedule.AsNeeded(20)))
            vm.onEvent(RegimenEditEvent.PackageSize("20"))
            vm.onEvent(RegimenEditEvent.Notes(" Vid feber "))
            vm.onEvent(RegimenEditEvent.Save)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        val saved = anna.getAll().getOrThrow().single { it.id != "r1" }
        assertEquals(
            Regimen(saved.id, RegimenKind.MEDICINE, "Alvedon", MedicineForm.CAPSULE, "500 mg", ItemUnit.KAPSEL, 20, DoseSchedule.AsNeeded(20), "Vid feber", sortOrder = 4),
            saved,
        )
    }

    @Test
    fun `sorten följer med ett nytt recept`() {
        assertEquals(RegimenKind.SUPPLEMENT, viewModel(kind = RegimenKind.SUPPLEMENT).state.value.value.kind)
    }

    @Test
    fun `redigering laddar receptet, pausar det och sparar (REC-5)`() = runTest {
        anna.upsert(levaxin)
        val vm = viewModel("r1")
        assertFalse(vm.isNew)
        assertEquals(levaxin, vm.state.value.value)
        vm.onEvent(RegimenEditEvent.Active(false))
        vm.effects.test {
            vm.onEvent(RegimenEditEvent.Save)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        assertEquals(levaxin.copy(active = false), anna.get("r1").getOrThrow())
    }

    @Test
    fun `en okänd dosering från en nyare app skrivs tillbaka orörd (BCK-5)`() = runTest {
        val future = levaxin.copy(schedule = DoseSchedule.Unknown("framtida"))
        anna.upsert(future)
        val vm = viewModel("r1")
        vm.onEvent(RegimenEditEvent.Notes("Ny anteckning"))
        vm.effects.test {
            vm.onEvent(RegimenEditEvent.Save)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        assertEquals(future.copy(notes = "Ny anteckning"), anna.get("r1").getOrThrow())
    }

    @Test
    fun `radering tar bort receptet och stänger (REC-5)`() = runTest {
        anna.upsert(levaxin)
        val vm = viewModel("r1")
        vm.effects.test {
            vm.onEvent(RegimenEditEvent.Delete)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        assertNull(anna.get("r1").getOrThrow())
    }

    @Test
    fun `ett recept som inte finns visar läsfel`() = runTest {
        viewModel("borta").state.test { assertEquals(DataError.Unknown, expectMostRecentItem().loadError) }
    }

    @Test
    fun `Försök igen läser receptet på nytt efter ett läsfel`() = runTest {
        val vm = viewModel("r1")
        assertEquals(DataError.Unknown, vm.state.value.loadError)
        anna.upsert(levaxin)
        vm.onEvent(RegimenEditEvent.Retry)
        assertNull(vm.state.value.loadError)
        assertEquals(levaxin, vm.state.value.value)
    }

    @Test
    fun `förpackningen tar bara siffror och tomt betyder ingen`() {
        val vm = viewModel()
        vm.onEvent(RegimenEditEvent.PackageSize("1a0"))
        assertEquals(10, vm.state.value.value.packageSize)
        vm.onEvent(RegimenEditEvent.PackageSize("1234567"))
        assertEquals(12345, vm.state.value.value.packageSize)
        vm.onEvent(RegimenEditEvent.PackageSize(""))
        assertNull(vm.state.value.value.packageSize)
        vm.onEvent(RegimenEditEvent.PackageSize("0"))
        assertEquals(R.string.regimen_package_size_invalid, vm.state.value.errorFor(RegimenValidator.PACKAGE_SIZE))
    }

    @Test
    fun `förhandsvisningen räknar med hushållets marginal och följer formuläret (REC-4)`() = runTest {
        anna.upsert(levaxin)
        val vm = viewModel("r1")
        vm.preview.test {
            assertEquals("9 tabletter · 1 karta", expectMostRecentItem().quantity)
            vm.onEvent(RegimenEditEvent.Schedule(DoseSchedule.Daily(2, 1)))
            assertEquals("18 tabletter · 1 karta", expectMostRecentItem().quantity)
            household.value = Household("h1", marginDays = 0)
            val preview = expectMostRecentItem()
            assertEquals("För en 7-dagarsresa", preview.context)
            assertEquals("14 tabletter · 1 karta", preview.quantity)
            assertEquals("2 × 1 tablett/dag × 7 dagar = 14 tabletter → 1 karta à 100", preview.reason)
        }
    }

    @Test
    fun `veckodagar i förhandsvisningen räknas från i dag`() = runTest {
        // FixedClock = 2026-09-21 (en måndag) i UTC; 7 + 2 dagar ger två måndagar.
        val vm = viewModel()
        vm.onEvent(RegimenEditEvent.Schedule(DoseSchedule.Weekdays(setOf(DayOfWeek.MONDAY), 1)))
        vm.preview.test { assertEquals("2 tabletter", expectMostRecentItem().quantity) }
    }

    @Test
    fun `validering - namn och dosering som går att räkna på (REC-2)`() {
        fun errors(schedule: DoseSchedule, name: String = "Levaxin") = RegimenValidator.validate(levaxin.copy(name = name, schedule = schedule))
        val schedule = RegimenValidator.SCHEDULE
        assertEquals(mapOf(RegimenValidator.NAME to R.string.name_required), errors(DoseSchedule.Daily(1, 1), name = " "))
        assertEquals(emptyMap(), errors(DoseSchedule.Daily(1, 1)))
        assertEquals(mapOf(schedule to R.string.regimen_doses_required), errors(DoseSchedule.Daily(0, 1)))
        assertEquals(emptyMap(), errors(DoseSchedule.Weekdays(setOf(DayOfWeek.FRIDAY), 1)))
        assertEquals(mapOf(schedule to R.string.regimen_days_required), errors(DoseSchedule.Weekdays(emptySet(), 1)))
        assertEquals(emptyMap(), errors(DoseSchedule.IntervalDays(2, 1)))
        assertEquals(mapOf(schedule to R.string.regimen_interval_min), errors(DoseSchedule.IntervalDays(1, 1)))
        assertEquals(emptyMap(), errors(DoseSchedule.AsNeeded(1)))
        assertEquals(mapOf(schedule to R.string.regimen_as_needed_required), errors(DoseSchedule.AsNeeded(0)))
        assertEquals(emptyMap(), errors(DoseSchedule.Unknown("framtida")), "en okänd dosering bevaras och stoppar inte sparandet")
    }

    @Test
    fun `ett byte av doseringstyp visar inget fel förrän typens fält ändrats`() {
        val vm = viewModel()
        vm.onEvent(RegimenEditEvent.Schedule(DoseSchedule.Weekdays(emptySet(), 1)))
        assertNull(vm.state.value.errorFor(RegimenValidator.SCHEDULE))
        assertFalse(vm.state.value.canSave)
        vm.onEvent(RegimenEditEvent.Schedule(DoseSchedule.Weekdays(setOf(DayOfWeek.MONDAY), 1)))
        vm.onEvent(RegimenEditEvent.Schedule(DoseSchedule.Weekdays(emptySet(), 1)))
        assertEquals(R.string.regimen_days_required, vm.state.value.errorFor(RegimenValidator.SCHEDULE))
        assertNull(vm.state.value.errorFor(RegimenValidator.NAME))
    }

    @Test
    fun `det lagrade namnet följer inte namnfältet`() = runTest {
        anna.upsert(levaxin)
        val vm = viewModel("r1")
        vm.onEvent(RegimenEditEvent.Name("Något annat"))
        assertEquals("Levaxin 50 µg", vm.storedName.value)
    }
}

/** Bara det aktiva hushållet – formuläret läser marginalen därifrån. */
private class StubHouseholds(override val current: MutableStateFlow<Household?>) : HouseholdRepository {
    override suspend fun create(name: String, ownerUid: String): Result<String> = error("används inte")

    override suspend fun select(householdId: String) = error("används inte")

    override suspend fun update(household: Household): Result<Unit> = error("används inte")
}
