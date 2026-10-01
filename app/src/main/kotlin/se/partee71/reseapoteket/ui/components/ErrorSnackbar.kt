package se.partee71.reseapoteket.ui.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.ui.common.toMessage

/**
 * Ett fel från en åtgärd i ramen (t.ex. arkivera) som meddelande i [hostState], med samma
 * texter som överallt (NFR-5). [onShown] anropas när det visats, så att ViewModeln släpper det.
 */
@Composable
internal fun ErrorSnackbar(error: DataError?, hostState: SnackbarHostState, onShown: () -> Unit) {
    val context = LocalContext.current
    val shown by rememberUpdatedState(onShown)
    LaunchedEffect(error) {
        if (error == null) return@LaunchedEffect
        hostState.showSnackbar(context.getString(error.toMessage()))
        shown()
    }
}
