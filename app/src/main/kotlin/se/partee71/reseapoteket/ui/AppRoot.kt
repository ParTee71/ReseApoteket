package se.partee71.reseapoteket.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.partee71.reseapoteket.navigation.AppNavigation
import se.partee71.reseapoteket.ui.auth.AuthEvent
import se.partee71.reseapoteket.ui.auth.AuthGate
import se.partee71.reseapoteket.ui.auth.AuthUiState
import se.partee71.reseapoteket.ui.auth.AuthViewModel
import se.partee71.reseapoteket.ui.auth.SignInScreen
import se.partee71.reseapoteket.ui.auth.UpdateRequiredScreen
import se.partee71.reseapoteket.ui.components.AppLoading

/**
 * Appens rot: inloggningen styr vad som visas (AUTH-1). [openTrip] är en resa som en påminnelse
 * öppnar (PÅM-7) – den visas när appen är inloggad, och [onTripOpened] släpper den.
 */
@Composable
fun AppRoot(
    modifier: Modifier = Modifier,
    openTrip: String? = null,
    onTripOpened: () -> Unit = {},
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AppRootContent(state, viewModel::onEvent, modifier) { AppNavigation(openTrip = openTrip, onTripOpened = onTripOpened) }
}

/** Appens bakgrund sätts här, en gång; skärmarna ritar ovanpå den. */
@Composable
fun AppRootContent(
    state: AuthUiState,
    onEvent: (AuthEvent) -> Unit,
    modifier: Modifier = Modifier,
    main: @Composable () -> Unit = { AppNavigation() }
) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (state.gate) {
            AuthGate.SignedOut, AuthGate.NeedsHousehold -> SignInScreen(state, onEvent)

            AuthGate.UpdateRequired -> UpdateRequiredScreen()

            // Syns bara en kort stund vid start; hänger något ser användaren att appen arbetar.
            AuthGate.Loading -> AppLoading()

            AuthGate.Ready -> main()
        }
    }
}
