package se.partee71.reseapoteket.ui.persons

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.DayOfWeek
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.reseapoteket.core.model.DoseSchedule
import se.partee71.reseapoteket.core.model.ItemUnit
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.core.model.Regimen
import se.partee71.reseapoteket.core.model.RegimenKind
import se.partee71.reseapoteket.testing.captureLightAndDark
import se.partee71.reseapoteket.ui.common.ArchiveEvent
import se.partee71.reseapoteket.ui.common.DetailUiState
import se.partee71.reseapoteket.ui.common.EditorEvents
import se.partee71.reseapoteket.ui.common.EditorState
import se.partee71.reseapoteket.ui.common.EditorUiState
import se.partee71.reseapoteket.ui.common.ListUiState
import se.partee71.reseapoteket.ui.components.ListArchive
import se.partee71.reseapoteket.ui.runEditScreenContract
import se.partee71.reseapoteket.ui.theme.AppColors
import se.partee71.reseapoteket.ui.theme.ReseApoteketTheme

/** Personskärmarna (PER-1–4): ramarnas kontrakt och det som är unikt för personer. */
@RunWith(RobolectricTestRunner::class)
class PersonScreensTest {

    @get:Rule
    val rule = createComposeRule()

    private val anna = Person("anna", "Anna", "🦊", AppColors.PERSON_HEX[0])
    private val gunnar = Person("gunnar", "Morfar Gunnar", "🦉", AppColors.PERSON_HEX[3])
    private val detail = PersonDetail(
        anna,
        medicines = listOf(
            Regimen("r1", RegimenKind.MEDICINE, "Levaxin", strength = "50 µg", schedule = DoseSchedule.Daily(1, 1)),
            Regimen("r2", RegimenKind.MEDICINE, "Enalapril", strength = "10 mg", schedule = DoseSchedule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), 1)),
            Regimen("r3", RegimenKind.MEDICINE, "Alvedon", strength = "500 mg", schedule = DoseSchedule.AsNeeded(20)),
            Regimen("r4", RegimenKind.MEDICINE, "Hydrokortison", strength = "1 %", unit = ItemUnit.TUB, schedule = DoseSchedule.AsNeeded(1), active = false)
        ),
        supplements = listOf(Regimen("s1", RegimenKind.SUPPLEMENT, "D-vitamin", strength = "10 µg", unit = ItemUnit.KAPSEL, schedule = DoseSchedule.Daily(1, 1)))
    )

    @Test
    fun `redigeringen uppfyller formulärkontraktet`() {
        val editor = EditorState(gunnar.copy(name = ""), PersonValidator)
        rule.runEditScreenContract(
            editor = editor,
            makeInvalid = {
                update(PersonValidator.NAME) { it.copy(name = "x") }
                update(PersonValidator.NAME) { it.copy(name = "") }
            },
            makeValid = { update(PersonValidator.NAME) { it.copy(name = "Morfar Gunnar") } },
            invalidMessage = "Ange ett namn"
        ) { state, events, onSave, onClose ->
            PersonEditContent(isNew = true, state, events, { event ->
                when (event) {
                    is PersonEditEvent.Name -> editor.update(PersonValidator.NAME) { it.copy(name = event.name) }
                    PersonEditEvent.Save -> onSave()
                    else -> Unit
                }
            }, onClose)
        }
    }

    @Test
    fun `emoji och färg väljs i formuläret`() {
        val events = mutableListOf<PersonEditEvent>()
        rule.setContent { ReseApoteketTheme { PersonEditContent(true, EditorUiState(gunnar), EditorEvents.None, { events += it }, {}) } }
        rule.onNodeWithContentDescription("🐯").performClick()
        rule.onNodeWithContentDescription("Färg: petrol").performClick()
        assertEquals(listOf(PersonEditEvent.Emoji("🐯"), PersonEditEvent.PersonColor(AppColors.PERSON_HEX[5])), events)
    }

    @Test
    fun `svep arkiverar och Visa arkiverade växlar (PER-3)`() {
        val events = mutableListOf<ArchiveEvent>()
        rule.setContent {
            ReseApoteketTheme {
                PersonsContent(ListUiState.Content(persistentListOf(PersonListItem(anna, 1))), {}, {}, {}, archive = ListArchive { events += it })
            }
        }
        rule.onNodeWithText("Anna").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Visa arkiverade").performClick()
        assertEquals(listOf(ArchiveEvent.Archive(anna.id, anna.name), ArchiveEvent.ToggleArchived), events)
    }

    @Test
    fun `persondetaljen visar sektionerna och lägger till i rätt sektion (PER-4)`() {
        val added = mutableListOf<RegimenKind>()
        val opened = mutableListOf<String>()
        var edits = 0
        rule.setContent {
            ReseApoteketTheme {
                PersonDetailContent(DetailUiState.Content(detail), {}, {}, { edits++ }, { added += it }, { opened += it.id })
            }
        }
        rule.onNodeWithText("Anna").assertIsDisplayed()
        rule.onNodeWithText("4 recept · 1 kosttillskott").assertIsDisplayed()
        rule.onNodeWithText("Levaxin 50 µg").performClick()
        rule.onNodeWithText("Lägg till kosttillskott").performClick()
        rule.onNodeWithContentDescription("Redigera").performClick()
        assertEquals(listOf("r1"), opened)
        assertEquals(listOf(RegimenKind.SUPPLEMENT), added)
        assertEquals(1, edits)
    }

    @Test
    fun `receptraderna visar doseringstyp, och ett pausat recept säger att det inte räknas (REC-3, REC-5)`() {
        rule.setContent { ReseApoteketTheme { PersonDetailContent(DetailUiState.Content(detail), {}, {}, {}, {}, {}) } }
        rule.onNodeWithText("1 tablett mån, ons, fre").assertIsDisplayed()
        rule.onNodeWithText("Veckodagar").assertIsDisplayed()
        rule.onNodeWithText("Vid behov").assertIsDisplayed()
        rule.onNodeWithText("Pausat").assertIsDisplayed()
        rule.onNodeWithText("Pausat – räknas inte med").assertIsDisplayed()
    }

    @Test
    fun `en arkiverad person kan återställas från detaljen (PER-3)`() {
        val events = mutableListOf<PersonDetailEvent>()
        rule.setContent {
            ReseApoteketTheme {
                PersonDetailContent(DetailUiState.Content(detail.copy(person = anna.copy(archived = true))), { events += it }, {}, {}, {}, {})
            }
        }
        rule.onNodeWithText("Arkiverad").assertIsDisplayed()
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Återställ").performClick()
        assertEquals(listOf<PersonDetailEvent>(PersonDetailEvent.SetArchived(false)), events)
    }

    @Test
    fun `skärmdumpar`() {
        captureLightAndDark("PersonDetailScreen_innehall") { PersonDetailContent(DetailUiState.Content(detail), {}, {}, {}, {}, {}) }
        captureLightAndDark("PersonDetailScreen_tom") {
            PersonDetailContent(DetailUiState.Content(PersonDetail(Person("lova", "Lova", "🐰", AppColors.PERSON_HEX[2]), emptyList(), emptyList())), {}, {}, {}, {}, {})
        }
        captureLightAndDark("PersonEditScreen_ny") { PersonEditContent(true, EditorUiState(gunnar, isDirty = true), EditorEvents.None, {}, {}) }
    }
}
