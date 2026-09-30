package se.partee71.reseapoteket.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.ui.auth.AuthEvent
import se.partee71.reseapoteket.ui.auth.AuthGate
import se.partee71.reseapoteket.ui.auth.AuthUiState
import se.partee71.reseapoteket.ui.auth.AuthViewModel
import se.partee71.reseapoteket.ui.auth.SignInScreen
import se.partee71.reseapoteket.ui.auth.UpdateRequiredScreen
import se.partee71.reseapoteket.ui.theme.AppTypography

/** Appens rot: inloggningen styr vad som visas (AUTH-1). */
@Composable
fun AppRoot(modifier: Modifier = Modifier, viewModel: AuthViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AppRootContent(state, viewModel::onEvent, modifier)
}

/** Appens bakgrund sätts här, en gång; skärmarna ritar ovanpå den. */
@Composable
fun AppRootContent(state: AuthUiState, onEvent: (AuthEvent) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (state.gate) {
            AuthGate.SignedOut, AuthGate.NeedsHousehold -> SignInScreen(state, onEvent)
            AuthGate.UpdateRequired -> UpdateRequiredScreen()
            AuthGate.Loading -> Unit
            AuthGate.Ready -> MainPlaceholder()
        }
    }
}

/** Huvudflödet – blir Navigation 3-värden (`NavDisplay`) i #20. */
@Composable
private fun MainPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.app_name),
            style = AppTypography.screenTitle,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
