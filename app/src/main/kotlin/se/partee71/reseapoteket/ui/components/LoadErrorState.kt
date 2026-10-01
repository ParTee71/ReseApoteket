package se.partee71.reseapoteket.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.ui.common.toMessage

/** Läsfelet i ramarna (lista, redigering, detalj): samma ikon, text och "Försök igen" överallt (NFR-1, NFR-5). */
@Composable
internal fun LoadErrorState(title: String, error: DataError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        icon = R.drawable.ic_wifi_off,
        title = title,
        message = stringResource(error.toMessage()),
        modifier = modifier,
        isError = true,
        action = { AppButton(stringResource(R.string.retry), onRetry, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary) },
    )
}
