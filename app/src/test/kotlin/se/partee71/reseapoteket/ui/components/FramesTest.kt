package se.partee71.reseapoteket.ui.components

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.model.QuantityRule
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.testing.captureLightAndDark
import se.partee71.reseapoteket.testing.captureScreenLightAndDark
import se.partee71.reseapoteket.testing.clickWithoutRipple
import se.partee71.reseapoteket.ui.common.ArchiveEvent
import se.partee71.reseapoteket.ui.common.DetailUiState
import se.partee71.reseapoteket.ui.common.EditorEffect
import se.partee71.reseapoteket.ui.common.EditorState
import se.partee71.reseapoteket.ui.common.EditorUiState
import se.partee71.reseapoteket.ui.common.ListUiState
import se.partee71.reseapoteket.ui.common.Validator
import se.partee71.reseapoteket.ui.runEditScreenContract
import se.partee71.reseapoteket.ui.runListScreenContract
import se.partee71.reseapoteket.ui.theme.AppColors
import se.partee71.reseapoteket.ui.theme.ReseApoteketTheme
import se.partee71.reseapoteket.ui.theme.Tone

/** Ramarna testas grundligt en gång här; skärmarna kör bara kontrakten (skill testing-strategy). */
@RunWith(RobolectricTestRunner::class)
class FramesTest {

    @get:Rule
    val rule = createComposeRule()

    private val empty = EmptyContent(R.drawable.ic_family_items, "Inga familjeartiklar än", "Solkräm, plåster, febertermometer – saker hela familjen delar.", "Lägg till")
    private val items = listOf("Solkräm" to "Krämer & salvor", "Hydrokortison 1 %" to "Krämer & salvor", "Plåster" to "Plåster & förband")

    private fun string(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

    private fun group(item: Pair<String, String>) = ListGroup(item.second, tone = if (item.second.startsWith("Plåster")) Tone.Green else Tone.Primary)

    @Test
    fun `EntityListScreen uppfyller listkontraktet`() = rule.runListScreenContract("Solkräm", "Solkräm", empty.title, empty.actionLabel) { state, onAdd, onRetry ->
        EntityListScreen("Familjeartiklar", state, empty, onAdd, key = { it }, onRetry = onRetry) { ItemRow(it) }
    }

    @Test
    fun `grupperad lista visar en rubrik med antal per grupp`() {
        rule.setContent {
            ReseApoteketTheme {
                EntityListScreen("Familjeartiklar", ListUiState.Content(items), empty, {}, key = { it.first }, group = ::group) { ItemRow(it.first) }
            }
        }
        rule.onNodeWithText("Krämer & salvor").assertIsDisplayed()
        rule.onNodeWithText("2").assertIsDisplayed()
        rule.onNodeWithText("Plåster & förband").assertIsDisplayed()
    }

    @Test
    fun `undergrupper, två kolumner, en hopfälld grupp och rubrik överst`() {
        val rows = listOf("A1" to "a", "A2" to "a", "B1" to "b", "Ö1" to "ö", "Ö2" to "ö", "Ö3" to "ö", "D1" to "d")
        val groups = mapOf(
            "a" to ListGroup("Mediciner", count = "1 / 2"),
            "b" to ListGroup("Mediciner"),
            "ö" to ListGroup("Övrigt", columns = 2),
            "d" to ListGroup("Dolda", collapsible = true),
        )
        rule.setContent {
            ReseApoteketTheme {
                EntityListScreen(
                    "Gran Canaria",
                    ListUiState.Content(rows),
                    empty,
                    {},
                    key = { it.first },
                    group = { groups.getValue(if (it.second == "b") "a" else it.second) },
                    subtitle = "7 dagar",
                    header = { ItemRow("Rubrikplats") },
                    subgroup = { row -> ListSubgroup(if (row.second == "b") "Erik" else "Anna", AppColors.person(0)).takeIf { row.second in setOf("a", "b") } },
                    archive = ListArchive(showToggle = false),
                ) { ItemRow(it.first) }
            }
        }
        rule.onNodeWithText("Rubrikplats").assertIsDisplayed()
        rule.onNodeWithText("7 dagar").assertIsDisplayed()
        rule.onNodeWithText("1 / 2").assertIsDisplayed()
        rule.onNodeWithText("Anna").assertIsDisplayed()
        rule.onNodeWithText("Erik").assertIsDisplayed()
        rule.onNodeWithContentDescription("Fler val").assertDoesNotExist()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Ö2"))
        val left = rule.onNodeWithText("Ö1").fetchSemanticsNode().positionInRoot
        val right = rule.onNodeWithText("Ö2").fetchSemanticsNode().positionInRoot
        assertEquals(left.y, right.y, "två kolumner: samma rad")
        rule.onNodeWithText("D1").assertDoesNotExist()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Dolda"))
        rule.onNodeWithText("Dolda").performClick()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("D1"))
        rule.onNodeWithText("D1").assertExists()
    }

    @Test
    fun `en utfälld grupp står kvar efter att skärmen återskapats`() {
        val restore = StateRestorationTester(rule)
        restore.setContent {
            ReseApoteketTheme {
                EntityListScreen("Gran Canaria", ListUiState.Content(listOf("A1", "D1")), empty, {}, key = { it }, group = { ListGroup(if (it == "D1") "Dolda" else "Övrigt", collapsible = it == "D1") }) { ItemRow(it) }
            }
        }
        rule.onNodeWithText("D1").assertDoesNotExist()
        rule.onNodeWithText("Dolda").performClick()
        rule.onNodeWithText("D1").assertIsDisplayed()
        restore.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("D1").assertIsDisplayed()
    }

    @Test
    fun `Visa arkiverade finns i menyn där arkivering finns`() {
        var toggles = 0
        rule.setContent {
            ReseApoteketTheme {
                EntityListScreen("Familjeartiklar", ListUiState.Content(listOf("Solkräm")), empty, {}, key = { it }, archive = ListArchive { if (it == ArchiveEvent.ToggleArchived) toggles++ }) { ItemRow(it) }
            }
        }
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Visa arkiverade").performClick()
        assertEquals(1, toggles)
    }

    @Test
    fun `EntityEditScreen uppfyller redigeringskontraktet`() {
        val editor = EditorState("", Validator { if (it.isBlank()) mapOf("name" to R.string.error_no_household) else emptyMap() })
        rule.runEditScreenContract(
            editor = editor,
            makeInvalid = { update("name") { "x" }; update("name") { "" } },
            makeValid = { update("name") { "Solkräm" } },
            invalidMessage = string(R.string.error_no_household),
        ) { state, effects, onSave, onClose ->
            EntityEditScreen("Ny familjeartikel", state, effects, onSave, onClose) {
                AppTextField(state.value, { v -> editor.update("name") { v } }, "Namn", error = state.errorFor("name")?.let { string(it) })
            }
        }
    }

    @Test
    fun `radering i redigeringens meny kräver bekräftelse`() {
        var deleted = 0
        var archived = 0
        rule.setContent {
            ReseApoteketTheme {
                EntityEditScreen(
                    "Solkräm", EditorUiState("Solkräm"), emptyFlow(), {}, {},
                    onArchive = { archived++ },
                    delete = DeleteAction("Radera Solkräm?", "Det går inte att ångra.") { deleted++ },
                ) {}
            }
        }
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Radera").performClick()
        assertEquals(0, deleted)
        rule.onNodeWithText("Radera Solkräm?").assertIsDisplayed()
        rule.onNodeWithText("Radera").performClick()
        assertEquals(1, deleted)
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Arkivera").performClick()
        assertEquals(1, archived)
    }

    @Test
    fun `EntityEditScreen - lyckad sparning går till onSaved, bakåt till onClose`() {
        var saved = 0
        var closed = 0
        val effects = kotlinx.coroutines.flow.MutableSharedFlow<EditorEffect>(extraBufferCapacity = 1)
        rule.setContent {
            ReseApoteketTheme { EntityEditScreen("Ny resa", EditorUiState("Kreta"), effects, {}, { closed++ }, onSaved = { saved++ }) {} }
        }
        rule.runOnIdle { effects.tryEmit(EditorEffect.Done) }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Tillbaka").performClick()
        assertEquals(1 to 1, saved to closed)
    }

    @Test
    fun `ConfirmDialog - nej-knappen och tryck utanför kan betyda olika saker`() {
        var declined = 0
        var dismissed = 0
        rule.setContent {
            ReseApoteketTheme {
                ConfirmDialog("Räkna om packlistan?", "…", "Räkna om", {}, { dismissed++ }, dismissLabel = "Behåll listan", onDecline = { declined++ })
            }
        }
        rule.onNodeWithText("Behåll listan").performClick()
        assertEquals(1 to 0, declined to dismissed)
        // Bakåt (som tryck utanför) stänger bara – "Räkna om packlistan?" sparar då ingenting (RES-4).
        Espresso.pressBack()
        rule.waitForIdle()
        assertEquals(1 to 1, declined to dismissed)
    }

    @Test
    fun `EntityEditScreen - arkivera med osparade ändringar frågar först (NFR-2)`() {
        var archived = 0
        rule.setContent {
            ReseApoteketTheme { EntityEditScreen("Solkräm", EditorUiState("Solkräm 50", isValid = true, isDirty = true), emptyFlow(), {}, {}, onArchive = { archived++ }) {} }
        }
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Arkivera").performClick()
        rule.onNodeWithText("Fortsätt redigera").performClick()
        assertEquals(0, archived)
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Arkivera").performClick()
        rule.onNodeWithText("Släng").performClick()
        assertEquals(1, archived)
    }

    @Test
    fun `LoadErrorState - något som inte finns längre har inget Försök igen (NFR-5)`() {
        rule.setContent { ReseApoteketTheme { LoadErrorState("Kunde inte hämta uppgifterna", DataError.NotFound, {}) } }
        rule.onNodeWithText("Det finns inte längre", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Försök igen").assertDoesNotExist()
    }

    @Test
    fun `EntityEditScreen - Återställ i menyn för något arkiverat`() {
        var restored = 0
        rule.setContent {
            ReseApoteketTheme { EntityEditScreen("Solkräm", EditorUiState("Solkräm"), emptyFlow(), {}, {}, onRestore = { restored++ }) {} }
        }
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Arkivera").assertDoesNotExist()
        rule.onNodeWithText("Återställ").performClick()
        assertEquals(1, restored)
    }

    @Test
    fun `EntityDetailScreen - lägen, försök igen och meny`() {
        var state by mutableStateOf<DetailUiState<String>>(DetailUiState.Loading)
        var retries = 0
        var edits = 0
        var archived = 0
        rule.setContent {
            ReseApoteketTheme {
                EntityDetailScreen(
                    state, header = { DetailHeader(it, "3 recept") }, onBack = {}, onEdit = { edits++ },
                    menu = { listOf(AppMenuItem("Arkivera", { archived++ })) }, onRetry = { retries++ },
                ) { Text("Recept") }
            }
        }
        rule.onNodeWithContentDescription("Laddar").assertExists()
        rule.onNodeWithContentDescription("Redigera").assertDoesNotExist()
        state = DetailUiState.Error(DataError.Offline)
        rule.onNodeWithText("Kunde inte hämta uppgifterna").assertIsDisplayed()
        rule.onNodeWithText("Försök igen").performClick()
        assertEquals(1, retries)
        state = DetailUiState.Content("Anna")
        rule.onNodeWithText("Anna").assertIsDisplayed()
        rule.onNodeWithText("Recept").assertIsDisplayed()
        rule.onNodeWithContentDescription("Redigera").performClick()
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Arkivera").performClick()
        assertEquals(1, edits)
        assertEquals(1, archived)
    }

    @Test
    fun `EntityDetailScreen - innehåll och fel`() {
        captureLightAndDark("EntityDetailScreen_innehall") {
            EntityDetailScreen(
                DetailUiState.Content("Anna"), header = { DetailHeader(it, "3 recept · 1 kosttillskott") }, onBack = {}, onEdit = {},
                menu = { listOf(AppMenuItem("Arkivera", {})) },
                leading = { PersonAvatar("🦊", AppColors.person(0), size = AvatarSize.Large) },
            ) {
                ApotekCard {
                    SectionHeader("Recept", icon = R.drawable.ic_medication, count = "1")
                    ItemRow("Levaxin 50 µg", subtitle = "1 tablett dagligen", navigates = true, onClick = {})
                }
            }
        }
        captureLightAndDark("EntityDetailScreen_fel") {
            EntityDetailScreen<String>(DetailUiState.Error(DataError.Offline), header = { DetailHeader(it) }, onBack = {}) {}
        }
        captureLightAndDark("EntityDetailScreen_finns_inte") {
            EntityDetailScreen<String>(DetailUiState.Error(DataError.NotFound), header = { DetailHeader(it) }, onBack = {}) {}
        }
    }

    @Test
    fun `EntityListScreen - lägen`() {
        captureLightAndDark("EntityListScreen_laddar") { EntityListScreen<String>("Familjeartiklar", ListUiState.Loading, empty, {}, key = { it }) {} }
        captureLightAndDark("EntityListScreen_tom") { EntityListScreen<String>("Familjeartiklar", ListUiState.Empty, empty, {}, key = { it }) {} }
        captureLightAndDark("EntityListScreen_fel") { EntityListScreen<String>("Familjeartiklar", ListUiState.Error(DataError.Offline), empty, {}, key = { it }) {} }
        captureLightAndDark("EntityListScreen_innehall") {
            EntityListScreen("Familjeartiklar", ListUiState.Content(items), empty, {}, key = { it.first }, group = ::group,
                addMenu = listOf(AppMenuItem("Visa arkiverade", {}))) { ItemRow(it.first, subtitle = "1 tub per 7 dagar", navigates = true, onClick = {}) }
        }
    }

    @Test
    fun `EntityEditScreen - ogiltig och giltig`() {
        captureLightAndDark("EntityEditScreen_ogiltig") {
            EntityEditScreen("Ny familjeartikel", EditorUiState("", mapOf("name" to R.string.error_unknown), isValid = false, isDirty = true), emptyFlow(), {}, {}) {
                AppTextField("", {}, "Namn", error = "Ange ett namn")
                QuantityRuleRow(QuantityRule.PerDays(1, 7), {}, { if (it == 1) "tub" else "tuber" })
            }
        }
        captureLightAndDark("EntityEditScreen_giltig") {
            EntityEditScreen("Ny familjeartikel", EditorUiState("Solkräm", isValid = true, isDirty = true), emptyFlow(), {}, {}, onArchive = {}) {
                AppTextField("Solkräm", {}, "Namn")
                PackingPreview("För 4 personer i 10 dagar", "2 tuber", reason = "ceil((10 + 2) / 7) × 1 tub")
            }
        }
    }

    @Test
    fun `EntityEditScreen - släng ändringar`() = rule.captureScreenLightAndDark("EntityEditScreen_slang", open = { onNodeWithContentDescription("Tillbaka").clickWithoutRipple() }) {
        EntityEditScreen("Solkräm", EditorUiState("Solkräm 50", isValid = true, isDirty = true), emptyFlow(), {}, {}) {
            AppTextField("Solkräm 50", {}, "Namn")
        }
    }
}
