package se.partee71.reseapoteket.ui.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.ui.components.AppButton
import se.partee71.reseapoteket.ui.components.EmptyState

/** Hushållet är sparat med en nyare app (BCK-5, HUS-3): inget visas eller ändras förrän appen uppdaterats. */
@Composable
fun UpdateRequiredScreen(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val releasesUrl = stringResource(R.string.releases_url)
    EmptyState(
        icon = R.drawable.ic_download,
        title = stringResource(R.string.update_required_title),
        message = stringResource(R.string.update_required_message),
        modifier = modifier,
        note = stringResource(R.string.update_required_note),
        fullScreen = true,
        action = {
            AppButton(
                text = stringResource(R.string.update_required_action),
                onClick = { uriHandler.openUri(releasesUrl) },
                modifier = Modifier.fillMaxWidth(),
                icon = R.drawable.ic_download,
            )
        },
    )
}
