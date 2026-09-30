package se.partee71.reseapoteket.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.ui.common.toMessage
import se.partee71.reseapoteket.ui.components.AppButton
import se.partee71.reseapoteket.ui.components.AppSnackbarHost
import se.partee71.reseapoteket.ui.components.EmptyState
import se.partee71.reseapoteket.ui.theme.Spacing

/** Inloggningen (AUTH-1–3). Fel visas som snackbar ovanför knappen; avbrott visas inte alls. */
@Composable
fun SignInScreen(state: AuthUiState, onEvent: (AuthEvent) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val error = state.error
    LaunchedEffect(error) {
        if (error != null) {
            snackbar.showSnackbar(context.getString(error.toMessage()))
            onEvent(AuthEvent.ErrorShown)
        }
    }
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        EmptyState(
            icon = R.drawable.ic_luggage,
            title = stringResource(R.string.app_name),
            message = stringResource(R.string.sign_in_welcome),
            note = stringResource(R.string.sign_in_note),
            fullScreen = true,
            action = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    AppSnackbarHost(snackbar)
                    AppButton(
                        text = stringResource(if (state.busy) R.string.sign_in_busy else R.string.sign_in_google),
                        onClick = { onEvent(AuthEvent.SignIn(context)) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = R.drawable.ic_login,
                        loading = state.busy,
                    )
                }
            },
        )
    }
}
