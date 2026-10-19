package se.partee71.reseapoteket.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import se.partee71.reseapoteket.R
import se.partee71.ui.components.AppButton
import se.partee71.ui.components.ButtonVariant
import se.partee71.ui.theme.AppTheme

/**
 * Bekräftelse. Permanent radering bekräftas alltid här med [destructive] = true – aldrig med
 * svep (skill shared-ui-components). [onDecline] är nej-knappen när den betyder något annat än
 * att stänga (t.ex. "Behåll listan" – spara utan att räkna om); tryck utanför är alltid [onDismiss].
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    dismissLabel: String = stringResource(R.string.cancel),
    onDecline: () -> Unit = onDismiss
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            AppButton(confirmLabel, onConfirm, variant = if (destructive) ButtonVariant.Destructive else ButtonVariant.Primary)
        },
        modifier = modifier,
        dismissButton = { AppButton(dismissLabel, onDecline, variant = ButtonVariant.Text) },
        title = { Text(title, style = AppTheme.typography.headline) },
        text = { Text(message, style = AppTheme.typography.body, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        shape = AppTheme.shapes.card,
        containerColor = AppTheme.extendedColors.card
    )
}
