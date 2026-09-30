package se.partee71.reseapoteket.ui.auth

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
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

/** Robolectric bara för att kunna skicka en `Context` i händelsen; allt annat är fakes. */
@RunWith(RobolectricTestRunner::class)
class AuthViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

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
        assertNull(vm.awaitGate(AuthGate.SignedOut).error)
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
