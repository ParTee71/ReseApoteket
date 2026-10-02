package se.partee71.reseapoteket.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import se.partee71.reseapoteket.core.model.Household
import se.partee71.reseapoteket.core.schema.HouseholdCodec
import se.partee71.reseapoteket.data.FixedClock
import se.partee71.reseapoteket.data.auth.AuthUser
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.datastore.SettingsStore
import se.partee71.reseapoteket.data.datastore.ThemeMode
import se.partee71.reseapoteket.data.export.ExportFiles
import se.partee71.reseapoteket.data.export.HouseholdExport
import se.partee71.reseapoteket.testing.FakeDataStore
import se.partee71.reseapoteket.testing.MainDispatcherRule
import se.partee71.reseapoteket.testing.StubHouseholds
import se.partee71.reseapoteket.testing.captureLightAndDark
import se.partee71.reseapoteket.testing.captureScreenLightAndDark
import se.partee71.reseapoteket.testing.clickWithoutRipple
import se.partee71.reseapoteket.ui.auth.FakeAuthRepository
import se.partee71.reseapoteket.ui.theme.ReseApoteketTheme

/** Inställningar (SET-1–6): hushåll, packlista, tema, konto, data & backup och om. */
@RunWith(RobolectricTestRunner::class)
class SettingsTest {

    @get:Rule
    val rule = createComposeRule()

    @get:Rule
    val main = MainDispatcherRule()

    private val svensson = Household("h1", "Familjen Svensson", listOf("uid-anna"), marginDays = 2, schemaVersion = 2)
    private val anna = AuthUser("uid-anna", "Anna Svensson", "anna.svensson@gmail.com")
    private val households = StubHouseholds(MutableStateFlow(svensson))
    private val auth = FakeAuthRepository().apply { authState.value = anna }
    private val settings = SettingsStore(FakeDataStore())
    private var exported: Result<String> = Result.success("""{"documents":[]}""")
    private val saved = mutableListOf<String>()
    private var saveResult: Result<String> = Result.success("content://se.partee71.reseapoteket.exports/exports/fil.json")
    private val export = object : HouseholdExport {
        override suspend fun export() = exported
    }
    private val files = object : ExportFiles {
        override suspend fun save(name: String, text: String): Result<String> = saveResult.also { saved += name }
    }

    private fun viewModel() = SettingsViewModel(settings, households, auth, export, files, FixedClock(), TimeZone.UTC)

    private val state = SettingsUiState(svensson, anna, ThemeMode.System, moveCheckedLast = true)

    private fun show(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit = {}, onEditHousehold: () -> Unit = {}, onOpenGallery: (() -> Unit)? = null) {
        rule.setContent { ReseApoteketTheme { SettingsContent(state, onEvent, "0.1.0", onEditHousehold, onOpenGallery) } }
    }

    private fun scrollTo(text: String) = rule.onNode(hasScrollAction()).performScrollToNode(hasText(text))

    @Test
    fun `tema och Flytta avprickade sist sparas på enheten (SET-1, SET-3)`() = runTest {
        val vm = viewModel()
        vm.state.test {
            assertEquals(ThemeMode.System, awaitItem().themeMode)
            vm.onEvent(SettingsEvent.SetTheme(ThemeMode.Dark))
            assertEquals(ThemeMode.Dark, expectMostRecentItem().themeMode)
            vm.onEvent(SettingsEvent.SetMoveCheckedLast(false))
            assertFalse(expectMostRecentItem().moveCheckedLast)
        }
    }

    @Test
    fun `marginaldagar skriver bara sitt fält och stannar inom 0–7 (SET-2)`() = runTest {
        val vm = viewModel()
        vm.state.test {
            awaitItem()
            vm.onEvent(SettingsEvent.SetMarginDays(3))
            assertEquals(3, expectMostRecentItem().household?.marginDays)
            vm.onEvent(SettingsEvent.SetMarginDays(12))
            assertEquals(7, expectMostRecentItem().household?.marginDays)
            vm.onEvent(SettingsEvent.SetMarginDays(7))
            assertEquals(listOf(setOf(HouseholdCodec.MARGIN_DAYS), setOf(HouseholdCodec.MARGIN_DAYS)), households.updates)
            assertEquals("Familjen Svensson", households.current.value?.name)

            households.nextUpdate = Result.failure(DataError.UpdateRequired)
            vm.onEvent(SettingsEvent.SetMarginDays(1))
            assertEquals(SettingsError(SettingsSection.Household, DataError.UpdateRequired), expectMostRecentItem().error)
        }
    }

    @Test
    fun `ett fel från en annan åtgärd under exporten står kvar när exporten blir klar (SET-7)`() = runTest {
        val done = CompletableDeferred<Result<String>>()
        val slow = object : HouseholdExport {
            override suspend fun export() = done.await()
        }
        val vm = SettingsViewModel(settings, households, auth, slow, files, FixedClock(), TimeZone.UTC)
        vm.state.test {
            awaitItem()
            vm.onEvent(SettingsEvent.Export)
            assertEquals(true, expectMostRecentItem().exporting)
            auth.nextSignOut = Result.failure(DataError.Offline)
            vm.onEvent(SettingsEvent.SignOut)
            done.complete(Result.success("{}"))
            val after = expectMostRecentItem()
            assertFalse(after.exporting)
            assertEquals(SettingsError(SettingsSection.Account, DataError.Offline), after.error)
        }
    }

    @Test
    fun `utloggning loggar ut, ett fel visas i kontokortet (SET-4)`() = runTest {
        val vm = viewModel()
        vm.state.test {
            awaitItem()
            auth.nextSignOut = Result.failure(DataError.Offline)
            vm.onEvent(SettingsEvent.SignOut)
            assertEquals(SettingsError(SettingsSection.Account, DataError.Offline), expectMostRecentItem().error)
            auth.nextSignOut = Result.success(Unit)
            vm.onEvent(SettingsEvent.SignOut)
            val after = expectMostRecentItem()
            assertNull(after.account)
            assertNull(after.error)
        }
        assertEquals(2, auth.signOutCalls)
    }

    @Test
    fun `export sparar filen med dagens datum och öppnar delningsmenyn en gång (SET-5)`() = runTest {
        val vm = viewModel()
        vm.shares.test {
            vm.onEvent(SettingsEvent.Export)
            assertEquals(ShareExport("content://se.partee71.reseapoteket.exports/exports/fil.json"), awaitItem())
            expectNoEvents()
        }
        assertEquals(listOf("reseapoteket-2026-09-21.json"), saved)
    }

    @Test
    fun `en misslyckad export visas i datakortet och delar ingenting (SET-5)`() = runTest {
        val vm = viewModel()
        exported = Result.failure(DataError.Offline)
        vm.state.test {
            awaitItem()
            vm.shares.test {
                vm.onEvent(SettingsEvent.Export)
                expectNoEvents()
            }
            val failed = expectMostRecentItem()
            assertEquals(SettingsError(SettingsSection.Data, DataError.Offline), failed.error)
            assertFalse(failed.exporting)
        }
        assertEquals(emptyList(), saved)
    }

    @Test
    fun `raderna skickar sina händelser och Logga ut frågar först (SET-2, SET-4, SET-5)`() {
        val events = mutableListOf<SettingsEvent>()
        var edits = 0
        show(state, { events += it }, onEditHousehold = { edits++ })
        rule.onNodeWithText("Familjen Svensson").performClick()
        assertEquals(1, edits)
        rule.onNodeWithContentDescription("Öka Marginaldagar").performClick()
        rule.onNodeWithText("Mörkt").performClick()
        scrollTo("Logga ut")
        rule.onNodeWithText("Anna Svensson").assertIsDisplayed()
        rule.onNodeWithText("anna.svensson@gmail.com").assertIsDisplayed()
        rule.onNodeWithText("Logga ut").performClick()
        rule.onNodeWithText("Logga ut?").assertIsDisplayed()
        rule.onNodeWithText("Avbryt").performClick()
        assertEquals(listOf(SettingsEvent.SetMarginDays(3), SettingsEvent.SetTheme(ThemeMode.Dark)), events, "Avbryt loggar inte ut")
        rule.onNodeWithText("Logga ut").performClick()
        rule.onAllNodesWithText("Logga ut").onLast().performClick()
        scrollTo("Exportera som JSON")
        rule.onNodeWithText("Exportera som JSON").performClick()
        assertEquals(
            listOf(SettingsEvent.SetMarginDays(3), SettingsEvent.SetTheme(ThemeMode.Dark), SettingsEvent.SignOut, SettingsEvent.Export),
            events,
        )
    }

    @Test
    fun `ett fel visas i kortet där åtgärden gjordes, och data och version syns (SET-5)`() {
        show(state.copy(error = SettingsError(SettingsSection.Data, DataError.Offline)))
        scrollTo("Dataformat 2 · appversion 0.1.0")
        rule.onNodeWithText("Dataformat 2 · appversion 0.1.0").assertIsDisplayed()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Ingen anslutning", substring = true))
        rule.onNodeWithText("Ingen anslutning", substring = true).assertIsDisplayed()
    }

    @Test
    fun `komponentgalleriet nås bara i debug (SET-6)`() {
        var opened = 0
        show(state, onOpenGallery = { opened++ })
        scrollTo("Komponenter")
        rule.onNodeWithText("Komponenter").performClick()
        assertEquals(1, opened)
    }

    @Test
    @Config(qualifiers = "w390dp-h1500dp-xxhdpi")
    fun `Inställningar - standard`() = captureLightAndDark("SettingsScreen_standard") {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) { SettingsContent(state, {}, "0.1.0", {}, onOpenGallery = {}) }
    }

    @Test
    fun `Inställningar - logga ut`() = rule.captureScreenLightAndDark("SettingsScreen_logga_ut", open = {
        scrollTo("Logga ut")
        onNodeWithText("Logga ut").clickWithoutRipple()
    }) {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) { SettingsContent(state, {}, "0.1.0", {}, onOpenGallery = null) }
    }
}
