package se.partee71.reseapoteket.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.withTimeoutOrNull
import se.partee71.reseapoteket.R

/** Något som just dolts och kan ångras; [id] skiljer två döljningar av samma namn åt. */
data class UndoRequest(val id: String, val name: String)

/**
 * Den enda ångra-mekanismen: "%s arkiverad · Ångra" i [hostState] i 5 s. [onUndo] vid tryck
 * på Ångra, annars [onDismissed]. Visar ingenting själv – meddelandet syns i `AppSnackbarHost`.
 */
@Composable
fun UndoSnackbar(request: UndoRequest?, hostState: SnackbarHostState, onUndo: () -> Unit, onDismissed: () -> Unit) {
    val message = request?.let { stringResource(R.string.archived_format, it.name) }
    val action = stringResource(R.string.undo)
    val undo by rememberUpdatedState(onUndo)
    val dismissed by rememberUpdatedState(onDismissed)
    LaunchedEffect(request) {
        if (message == null) return@LaunchedEffect
        val result = withTimeoutOrNull(UNDO_MILLIS) {
            hostState.showSnackbar(message, actionLabel = action, withDismissAction = false, duration = SnackbarDuration.Indefinite)
        }
        if (result == SnackbarResult.ActionPerformed) undo() else dismissed()
    }
}

/** Hur länge ångra erbjuds. */
const val UNDO_MILLIS = 5_000L
