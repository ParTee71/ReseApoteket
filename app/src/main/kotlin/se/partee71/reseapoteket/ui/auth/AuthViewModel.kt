package se.partee71.reseapoteket.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import se.partee71.reseapoteket.core.schema.Schema
import se.partee71.reseapoteket.data.auth.AuthRepository
import se.partee71.reseapoteket.data.auth.AuthUser
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.common.HouseholdVersion
import se.partee71.reseapoteket.data.common.dataError
import se.partee71.reseapoteket.data.household.HouseholdSession
import se.partee71.reseapoteket.domain.EnsureHouseholdUseCase

/** Vad appen visar: inloggning, "Uppdatera appen" eller innehållet (AUTH-1, HUS-3). */
enum class AuthGate { Loading, SignedOut, NeedsHousehold, UpdateRequired, Ready }

data class AuthUiState(
    val gate: AuthGate = AuthGate.Loading,
    /** Inloggning eller val av hushåll pågår. */
    val busy: Boolean = false,
    val error: DataError? = null,
)

sealed interface AuthEvent {
    /** Knappen "Logga in med Google" – loggar in, eller försöker igen med hushållet om användaren redan är inloggad. */
    data class SignIn(val activityContext: Context) : AuthEvent

    data object ErrorShown : AuthEvent
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val session: HouseholdSession,
    private val ensureHousehold: EnsureHouseholdUseCase,
) : ViewModel() {

    private val user: StateFlow<AuthUser?> = auth.authState.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val signingIn = MutableStateFlow(false)
    private val ensuring = MutableStateFlow(false)
    private val error = MutableStateFlow<DataError?>(null)
    private var ensureJob: Job? = null
    private var ensuringUid: String? = null

    private val gate = combine(auth.authState, session.selection, session.schemaVersion, ::gateOf)

    val state: StateFlow<AuthUiState> =
        combine(gate, signingIn, ensuring, error) { gate, signingIn, ensuring, error ->
            AuthUiState(gate, busy = signingIn || ensuring, error = error)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AuthUiState())

    init {
        // Utloggad (även om sessionen försvunnit utifrån) = inget valt hushåll, så att nästa
        // användare på enheten aldrig hamnar i den förras hushåll – och ett pågående val avbryts.
        viewModelScope.launch {
            auth.authState.filter { it == null }.collect { ensureJob?.cancel() }
        }
        viewModelScope.launch {
            combine(auth.authState, session.selection) { user, householdId -> user == null && householdId != null }
                .filter { it }
                .collect { session.select(null) }
        }
        // En inloggad användare utan valt hushåll får ett – en gång per användare; efter ett fel
        // försöker knappen igen.
        viewModelScope.launch {
            combine(auth.authState, session.selection) { user, householdId -> user.takeIf { householdId == null } }
                .distinctUntilChanged()
                .filterNotNull()
                .collect { ensure(it) }
        }
    }

    fun onEvent(event: AuthEvent) {
        when (event) {
            is AuthEvent.SignIn -> user.value?.let(::ensure) ?: signIn(event.activityContext)
            AuthEvent.ErrorShown -> error.value = null
        }
    }

    /**
     * Kontoväljaren kräver aktivitetens kontext, som hålls bara medan väljaren är öppen
     * (skill firebase-auth); roteras skärmen under tiden frigörs den när väljaren stängts.
     */
    private fun signIn(activityContext: Context) {
        if (signingIn.value) return
        viewModelScope.launch {
            signingIn.value = true
            error.value = null
            val result = auth.signInWithGoogle(activityContext)
            signingIn.value = false
            // Avbruten inloggning är inget fel (AUTH-2).
            result.dataError()?.takeIf { it != DataError.Cancelled }?.let { error.value = it }
        }
    }

    /**
     * Väljer hushåll åt [user]. Ett pågående val för en annan användare avbryts, så att den nya
     * användaren aldrig får den förras hushåll eller fel.
     */
    private fun ensure(user: AuthUser) {
        if (ensureJob?.isActive == true && ensuringUid == user.uid) return
        ensureJob?.cancel()
        ensuringUid = user.uid
        // Startas först när jobbet är sparat, så att jämförelsen i finally alltid gäller rätt jobb.
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            ensuring.value = true
            error.value = null
            try {
                ensureHousehold(user.uid).dataError()
                    ?.takeIf { this@AuthViewModel.user.value?.uid == user.uid }
                    ?.let { error.value = it }
            } finally {
                // Ett avbrutet jobb lämnar flaggan åt det som ersatte det.
                if (ensureJob == coroutineContext[Job]) ensuring.value = false
            }
        }
        ensureJob = job
        job.start()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        fun gateOf(user: AuthUser?, householdId: String?, version: HouseholdVersion?): AuthGate = when {
            user == null -> AuthGate.SignedOut
            householdId == null -> AuthGate.NeedsHousehold
            // Versionen avgör om appen får visas – vänta på den i stället för att blinka förbi (HUS-3).
            version?.householdId != householdId -> AuthGate.Loading
            Schema.isNewerThanApp(version.version) -> AuthGate.UpdateRequired
            else -> AuthGate.Ready
        }
    }
}
