package se.partee71.reseapoteket.ui.placeholder

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.ui.components.AppTopBar
import se.partee71.reseapoteket.ui.components.EmptyState
import se.partee71.reseapoteket.ui.components.TopBarSize

/** Undersidor som ännu inte är byggda (#22–#27). Försvinner när den sista är på plats. */
@Composable
fun ComingSoonScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        AppTopBar(stringResource(R.string.coming_soon_title), size = TopBarSize.Small, onBack = onBack)
        EmptyState(
            icon = R.drawable.ic_luggage,
            title = stringResource(R.string.coming_soon_title),
            message = stringResource(R.string.coming_soon_message),
        )
    }
}
