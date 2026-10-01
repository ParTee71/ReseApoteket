package se.partee71.reseapoteket.ui.auth

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import se.partee71.reseapoteket.core.model.Household
import se.partee71.reseapoteket.core.schema.Schema
import se.partee71.reseapoteket.data.auth.AuthRepository
import se.partee71.reseapoteket.data.auth.AuthUser
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.testing.HouseholdFixture
import se.partee71.reseapoteket.testing.MainDispatcherRule

/**
 * Robolectric bara för att kunna skicka en `Context` i händelsen; allt annat är fakes. Köad
 * dispatcher: med en okonfinerad körs DataStore-skrivningar inuti sina egna flödesinsamlare och
 * snurrar i evighet – och ordningsproblem ska inte döljas (som i HouseholdSessionTest).
 */
@RunWith(RobolectricTestRunner::class)
class AuthViewModelTest {

    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = RuntimeEnvironment.getApplication()
    private val auth = FakeAuthRepository()

    private fun TestScope.viewModel(fixture: HouseholdFixture = HouseholdFixture(backgroundScope, folder.root)) =
        fixture to AuthViewModel(auth, fixture.session, fixture.ensureHousehold)

    private suspend fun AuthViewModel.awaitGate(gate: AuthGate) = state.first { it.gate == gate && !it.busy }

    @Test
    fun `utloggad visar inloggningen utan fel`() = runTest(main.dispatcher) {
        val (_, vm) = viewModel()
        assertEquals(AuthUiState(), vm.state.value)
        assertEquals(AuthUiState(AuthGate.SignedOut), vm.awaitGate(AuthGate.SignedOut))
    }

    @Test
    fun `lyckad inloggning skapar hushållet och släpper in användaren`() = runTest(main.dispatcher) {
        val (fixture, vm) = viewModel()
        vm.awaitGate(AuthGate.SignedOut)

        vm.onEvent(AuthEvent.SignIn(context))

        assertEquals(AuthUiState(AuthGate.Ready), vm.awaitGate(AuthGate.Ready))
        assertEquals(listOf("uid-anna"), fixture.households().single().memberUids)
        assertEquals(1, auth.signInCalls)
    }

    @Test
    fun `avbruten inloggning är inget fel`() = runTest(main.dispatcher) {
        val (_, vm) = viewModel()
        auth.nextSignIn = Result.failure(DataError.Cancelled)

        vm.onEvent(AuthEvent.SignIn(context))

        assertEquals(AuthUiState(AuthGate.SignedOut), vm.awaitGate(AuthGate.SignedOut))
    }

    @Test
    fun `misslyckad inloggning visar felet tills det visats`() = runTest(main.dispatcher) {
        val (_, vm) = viewModel()
        auth.nextSignIn = Result.failure(DataError.Offline)

        vm.onEvent(AuthEvent.SignIn(context))

        assertEquals(DataError.Offline, vm.state.first { it.error != null }.error)
        vm.onEvent(AuthEvent.ErrorShown)
        assertEquals(AuthUiState(AuthGate.SignedOut), vm.state.first { it.error == null })
    }

    @Test
    fun `inloggad användare med sparat hushåll går direkt in utan att fråga servern`() = runTest(main.dispatcher) {
        val fixture = HouseholdFixture(backgroundScope, folder.root)
        fixture.storeHousehold(Household("h1", "Familjen", listOf("uid-anna")))
        fixture.session.select("h1")
        auth.authState.value = AuthUser("uid-anna")
        val (_, vm) = viewModel(fixture)

        vm.awaitGate(AuthGate.Ready)
        assertEquals(0, fixture.directory.calls)
    }

    @Test
    fun `hushållet kunde inte väljas - felet visas och knappen försöker igen utan ny inloggning`() = runTest(main.dispatcher) {
        val fixture = HouseholdFixture(backgroundScope, folder.root)
        fixture.directory.failure = DataError.Offline
        auth.authState.value = AuthUser("uid-anna")
        val (_, vm) = viewModel(fixture)

        val failed = vm.state.first { it.error != null }
        assertEquals(AuthGate.NeedsHousehold, failed.gate)
        assertEquals(DataError.Offline, failed.error)

        fixture.directory.failure = null
        vm.onEvent(AuthEvent.SignIn(context))

        vm.awaitGate(AuthGate.Ready)
        assertEquals(0, auth.signInCalls)
        assertEquals(1, fixture.households().size)
    }

    @Test
    fun `utloggning och inloggning med ett annat konto ger det kontots hushåll`() = runTest(main.dispatcher) {
        val fixture = HouseholdFixture(backgroundScope, folder.root)
        fixture.storeHousehold(Household("annas", "Annas", listOf("uid-anna")))
        fixture.storeHousehold(Household("eriks", "Eriks", listOf("uid-erik")))
        val (_, vm) = viewModel(fixture)
        vm.onEvent(AuthEvent.SignIn(context))
        vm.awaitGate(AuthGate.Ready)
        assertEquals("annas", fixture.session.householdId.value)

        auth.signOut()
        vm.awaitGate(AuthGate.SignedOut)
        auth.nextSignIn = Result.success(AuthUser("uid-erik"))
        vm.onEvent(AuthEvent.SignIn(context))

        vm.awaitGate(AuthGate.Ready)
        assertEquals("eriks", fixture.session.householdId.value)
    }

    @Test
    fun `utloggad med ett kvarglömt hushållsval rensar valet`() = runTest(main.dispatcher) {
        val fixture = HouseholdFixture(backgroundScope, folder.root)
        fixture.session.select("nagon-annans")
        val (_, vm) = viewModel(fixture)

        vm.awaitGate(AuthGate.SignedOut)
        fixture.session.householdId.first { it == null }
    }

    @Test
    fun `borttagen ur det valda hushållet - appen fastnar inte utan väljer ett annat (HUS-5)`() = runTest(main.dispatcher) {
        val fixture = HouseholdFixture(backgroundScope, folder.root)
        fixture.storeHousehold(Household("eriks", "Eriks", listOf("uid-erik")))
        fixture.storeHousehold(Household("annas", "Annas", listOf("uid-anna")))
        fixture.denied.value = setOf("eriks")
        fixture.session.select("eriks")
        auth.authState.value = AuthUser("uid-anna")
        val (_, vm) = viewModel(fixture)

        assertEquals(AuthUiState(AuthGate.Ready), vm.awaitGate(AuthGate.Ready))
        assertEquals("annas", fixture.session.householdId.value)
    }

    @Test
    fun `borttagen ur sitt enda hushåll - ett nytt skapas (HUS-5, HUS-2)`() = runTest(main.dispatcher) {
        val fixture = HouseholdFixture(backgroundScope, folder.root)
        fixture.storeHousehold(Household("raderat", "Familjen", listOf("uid-erik")))
        fixture.denied.value = setOf("raderat")
        fixture.session.select("raderat")
        auth.authState.value = AuthUser("uid-anna")
        val (_, vm) = viewModel(fixture)

        vm.awaitGate(AuthGate.Ready)
        val created = fixture.households().single { "uid-anna" in it.memberUids }
        assertEquals(created.id, fixture.session.householdId.value)
    }

    @Test
    fun `byte av konto medan hushållet väljs - det nya kontot får sitt eget hushåll och inget fel`() = runTest(main.dispatcher) {
        val fixture = HouseholdFixture(backgroundScope, folder.root)
        fixture.storeHousehold(Household("annas", "Annas", listOf("uid-anna")))
        fixture.storeHousehold(Household("eriks", "Eriks", listOf("uid-erik")))
        val hold = CompletableDeferred<Unit>()
        fixture.directory.hold = hold
        val (_, vm) = viewModel(fixture)
        auth.authState.value = AuthUser("uid-anna")
        vm.state.first { it.busy }

        auth.signOut()
        auth.authState.value = AuthUser("uid-erik")
        vm.state.first { it.gate == AuthGate.NeedsHousehold }
        hold.complete(Unit)

        assertEquals(AuthUiState(AuthGate.Ready), vm.awaitGate(AuthGate.Ready))
        assertEquals("eriks", fixture.session.householdId.value)
    }

    @Test
    fun `appen visas inte förrän hushållets version är känd`() = runTest(main.dispatcher) {
        val fixture = HouseholdFixture(backgroundScope, folder.root)
        fixture.storeHousehold(Household("h1", "Familjen", listOf("uid-anna"), schemaVersion = Schema.CURRENT_VERSION + 1))
        fixture.versionsKnown.value = false
        fixture.session.select("h1")
        auth.authState.value = AuthUser("uid-anna")
        val (_, vm) = viewModel(fixture)

        assertEquals(AuthGate.Loading, vm.awaitGate(AuthGate.Loading).gate)
        fixture.versionsKnown.value = true
        vm.awaitGate(AuthGate.UpdateRequired)
    }

    @Test
    fun `hushåll med nyare schemaversion visar Uppdatera appen`() = runTest(main.dispatcher) {
        val fixture = HouseholdFixture(backgroundScope, folder.root)
        fixture.storeHousehold(Household("h1", "Familjen", listOf("uid-anna"), schemaVersion = Schema.CURRENT_VERSION + 1))
        val (_, vm) = viewModel(fixture)

        vm.onEvent(AuthEvent.SignIn(context))

        vm.awaitGate(AuthGate.UpdateRequired)
        assertEquals(1, fixture.households().size)
    }
}

/** Inloggning utan Google: [nextSignIn] bestämmer utfallet, lyckad inloggning sätter användaren. */
class FakeAuthRepository : AuthRepository {
    override val authState = MutableStateFlow<AuthUser?>(null)
    var nextSignIn: Result<AuthUser> = Result.success(AuthUser("uid-anna"))
    var signInCalls = 0
        private set

    override suspend fun signInWithGoogle(activityContext: Context): Result<AuthUser> {
        signInCalls++
        return nextSignIn.onSuccess { authState.value = it }
    }

    override suspend fun signOut(): Result<Unit> {
        authState.value = null
        return Result.success(Unit)
    }
}
