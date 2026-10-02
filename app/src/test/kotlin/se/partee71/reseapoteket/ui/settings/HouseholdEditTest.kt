package se.partee71.reseapoteket.ui.settings

import androidx.compose.ui.test.junit4.v2.createComposeRule
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.reseapoteket.core.model.Household
import se.partee71.reseapoteket.core.schema.HouseholdCodec
import se.partee71.reseapoteket.testing.MainDispatcherRule
import se.partee71.reseapoteket.testing.StubHouseholds
import se.partee71.reseapoteket.testing.captureLightAndDark
import se.partee71.reseapoteket.ui.common.EditorState
import se.partee71.reseapoteket.ui.common.EditorUiState
import se.partee71.reseapoteket.ui.runEditScreenContract

/** Hushållets namn (SET-2): formulärkontraktet och att bara namnet skrivs. */
@RunWith(RobolectricTestRunner::class)
class HouseholdEditTest {

    @get:Rule
    val rule = createComposeRule()

    @get:Rule
    val main = MainDispatcherRule()

    private val svensson = Household("h1", "Familjen Svensson", listOf("uid-anna"), marginDays = 2)

    @Test
    fun `formuläret uppfyller formulärkontraktet`() {
        rule.runEditScreenContract(
            editor = EditorState(svensson, HouseholdValidator),
            makeInvalid = { update(HouseholdValidator.NAME) { it.copy(name = " ") } },
            makeValid = { update(HouseholdValidator.NAME) { it.copy(name = "Familjen Berg") } },
            invalidMessage = "Ange ett namn",
        ) { state, effects, onSave, onClose ->
            HouseholdEditContent(state, effects, { if (it is HouseholdEditEvent.Save) onSave() }, onClose)
        }
    }

    @Test
    fun `bara namnet skrivs, trimmat – marginaldagar som ändrats under tiden står kvar`() = runTest {
        val households = StubHouseholds(MutableStateFlow(svensson))
        val vm = HouseholdEditViewModel(households)
        assertEquals("Familjen Svensson", vm.state.value.value.name)
        households.current.value = svensson.copy(marginDays = 5)
        vm.onEvent(HouseholdEditEvent.Name("  Familjen Berg "))
        vm.onEvent(HouseholdEditEvent.Save)
        assertEquals(svensson.copy(name = "Familjen Berg", marginDays = 5), households.current.value)
        assertEquals(listOf(setOf(HouseholdCodec.NAME)), households.updates)
    }

    @Test
    fun `har ett annat hushåll valts under tiden sparas ingenting`() = runTest {
        val households = StubHouseholds(MutableStateFlow(svensson))
        val vm = HouseholdEditViewModel(households)
        households.current.value = Household("h2", "Annat hushåll")
        vm.onEvent(HouseholdEditEvent.Name("Familjen Berg"))
        vm.onEvent(HouseholdEditEvent.Save)
        assertEquals(emptyList(), households.updates)
        assertEquals("Annat hushåll", households.current.value?.name)
    }

    @Test
    fun `Hushåll - namn`() = captureLightAndDark("HouseholdEditScreen_namn") {
        HouseholdEditContent(EditorUiState(svensson), emptyFlow(), {}, {})
    }
}
