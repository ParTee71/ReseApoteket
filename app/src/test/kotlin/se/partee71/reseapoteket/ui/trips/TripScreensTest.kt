package se.partee71.reseapoteket.ui.trips

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.engine.PackingPreviewText
import se.partee71.reseapoteket.core.engine.PackingProgress
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.core.model.Trip
import se.partee71.reseapoteket.core.model.TripStatus
import se.partee71.reseapoteket.data.common.AppErrors
import se.partee71.reseapoteket.testing.captureLightAndDark
import se.partee71.reseapoteket.ui.common.ArchiveEvent
import se.partee71.reseapoteket.ui.components.ListArchive
import se.partee71.reseapoteket.ui.runEditScreenContract
import se.partee71.reseapoteket.ui.theme.AppColors
import se.partee71.reseapoteket.ui.theme.ReseApoteketTheme
import se.partee71.ui.common.EditorEvents
import se.partee71.ui.common.EditorState
import se.partee71.ui.common.EditorUiState
import se.partee71.ui.common.ListUiState

/** Reseskärmarna (RES-1–4): formulärkontraktet och det som är unikt för resor. */
@RunWith(RobolectricTestRunner::class)
class TripScreensTest {

    @get:Rule
    val rule = createComposeRule()

    private val people = listOf(
        Person("anna", "Anna", "🦊", AppColors.PERSON_HEX[0]),
        Person("erik", "Erik", "🐻", AppColors.PERSON_HEX[1]),
        Person("lova", "Lova", "🐰", AppColors.PERSON_HEX[2]),
        Person("gunnar", "Morfar Gunnar", "🦉", AppColors.PERSON_HEX[3])
    )
    private val trip = Trip("t1", "Gran Canaria", LocalDate(2026, 10, 2), 7, people.map { it.id })
    private val preview = PackingPreviewText("För 4 resenärer i 7 dagar", "16 saker att packa", "6 recept · 3 kosttillskott · 7 familjeartiklar")

    @Test
    fun `formuläret uppfyller formulärkontraktet`() {
        val editor = EditorState(trip.copy(travellerIds = emptyList()), TripValidator, AppErrors)
        rule.runEditScreenContract(
            editor = editor,
            makeInvalid = {
                update(TripValidator.TRAVELLERS) { it.copy(travellerIds = listOf("anna")) }
                update(TripValidator.TRAVELLERS) { it.copy(travellerIds = emptyList()) }
            },
            makeValid = { update(TripValidator.TRAVELLERS) { it.copy(travellerIds = listOf("anna")) } },
            invalidMessage = "Välj minst en som åker med"
        ) { state, events, onSave, onClose ->
            TripEditContent(false, state, people, preview, events, { if (it is TripEditEvent.Save) onSave() }, onClose, stored = trip)
        }
    }

    @Test
    fun `en ny resa heter Ny resa, sparas med Skapa packlista och föreslår namnet Resa datum (RES-2, RES-3)`() {
        val events = mutableListOf<TripEditEvent>()
        val new = trip.copy(name = "")
        rule.setContent { ReseApoteketTheme { TripEditContent(true, EditorUiState(new, isDirty = true), people, preview, EditorEvents.None, { events += it }, {}) } }
        rule.onNodeWithText("Ny resa").assertIsDisplayed()
        rule.onNodeWithText("Resa 2 okt").assertIsDisplayed()
        rule.onNodeWithText("Skapa packlista").performClick()
        assertEquals(listOf<TripEditEvent>(TripEditEvent.Save("Resa 2 okt")), events)
    }

    @Test
    fun `Räkna om packlistan frågar med Behåll listan och Räkna om (RES-4)`() {
        val events = mutableListOf<TripEditEvent>()
        rule.setContent { ReseApoteketTheme { TripEditContent(false, EditorUiState(trip), people, preview, EditorEvents.None, { events += it }, {}, stored = trip, askRecount = true) } }
        rule.onNodeWithText("Räkna om packlistan?").assertIsDisplayed()
        rule.onNodeWithText("Behåll listan").performClick()
        rule.onNodeWithText("Räkna om").performClick()
        assertEquals(listOf<TripEditEvent>(TripEditEvent.Recount(false), TripEditEvent.Recount(true)), events)
    }

    @Test
    fun `en resenär väljs och väljs bort med personchipet`() {
        val events = mutableListOf<TripEditEvent>()
        rule.setContent { ReseApoteketTheme { TripEditContent(true, EditorUiState(trip), people, preview, EditorEvents.None, { events += it }, {}) } }
        rule.onNodeWithText("Erik").performScrollTo().performClick()
        assertEquals(listOf<TripEditEvent>(TripEditEvent.Traveller("erik")), events)
    }

    @Test
    fun `svep arkiverar en resa och Visa arkiverade växlar, en arkiverad resa sveps inte (RES-1)`() {
        val events = mutableListOf<ArchiveEvent>()
        val opened = mutableListOf<String>()
        val archived = Trip("t2", "Rom", LocalDate(2025, 4, 10), 5, listOf("anna"), archived = true)
        rule.setContent {
            ReseApoteketTheme {
                TripsContent(ListUiState.Content(persistentListOf(TripListItem(trip, people), TripListItem(archived, people.take(1)))), {}, {}, { opened += it }, archive = ListArchive(showing = true) { events += it })
            }
        }
        val archiveAction = SemanticsMatcher("har Arkivera") { node -> node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().any { it.label == "Arkivera" } }
        rule.onNodeWithText("Rom").assert(!archiveAction)
        rule.onNodeWithText("Gran Canaria").assert(archiveAction).performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithText("Rom").performClick()
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Dölj arkiverade").performClick()
        assertEquals(listOf(ArchiveEvent.Archive("t1", "Gran Canaria"), ArchiveEvent.ToggleArchived), events)
        assertEquals(listOf("t2"), opened)
    }

    @Test
    fun `en sparad resa kan arkiveras och en arkiverad återställas från formuläret`() {
        val events = mutableListOf<TripEditEvent>()
        var stored by mutableStateOf(trip)
        rule.setContent { ReseApoteketTheme { TripEditContent(false, EditorUiState(stored), people, preview, EditorEvents.None, { events += it }, {}, stored = stored) } }
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Arkivera").performClick()
        stored = trip.copy(archived = true)
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Återställ").performClick()
        assertEquals(listOf<TripEditEvent>(TripEditEvent.Archive, TripEditEvent.Restore), events)
    }

    @Test
    fun `skärmdumpar - formuläret`() {
        captureLightAndDark("TripEditScreen_ny") {
            TripEditContent(true, EditorUiState(trip, isDirty = true), people, preview, EditorEvents.None, {}, {})
        }
        captureLightAndDark("TripEditScreen_ingen_resenar") {
            val none = trip.copy(name = "", travellerIds = emptyList())
            TripEditContent(
                true,
                EditorUiState(none, errors = persistentMapOf(TripValidator.TRAVELLERS to R.string.trip_travellers_required), isValid = false, isDirty = true),
                people,
                PackingPreviewText("För 0 resenärer i 7 dagar", "0 saker att packa", null),
                EditorEvents.None,
                {},
                {}
            )
        }
    }

    @Test
    fun `skärmdumpar - listan`() {
        val list = listOf(
            TripListItem(trip, people, PackingProgress(9, 16)),
            TripListItem(Trip("t2", "Fjällen", LocalDate(2026, 12, 19), 8, listOf("anna", "erik")), people.take(2), PackingProgress(0, 11)),
            TripListItem(Trip("t3", "Kreta", LocalDate(2026, 7, 4), 9, listOf("anna", "erik", "lova"), TripStatus.PACKED), people.take(3), PackingProgress(14, 14)),
            TripListItem(Trip("t4", "Göteborg", LocalDate(2026, 5, 15), 2, listOf("anna"), TripStatus.DONE), people.take(1), PackingProgress(6, 6)),
            TripListItem(Trip("t5", "Rom", LocalDate(2025, 4, 10), 5, listOf("anna", "erik"), TripStatus.DONE, archived = true), people.take(2), PackingProgress(9, 10))
        )
        captureLightAndDark("TripsScreen_innehall") { TripsContent(ListUiState.Content(list.toImmutableList()), {}, {}, {}, archive = ListArchive(showing = true)) }
    }
}
