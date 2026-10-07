@file:OptIn(ExperimentalMaterial3Api::class)

package se.partee71.reseapoteket.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.ui.common.toMessage
import se.partee71.reseapoteket.ui.theme.AppColors
import se.partee71.reseapoteket.ui.theme.AppShapes
import se.partee71.reseapoteket.ui.theme.AppTypography
import se.partee71.reseapoteket.ui.theme.Spacing

/**
 * Bottom sheet med rubrik, t.ex. "ändra mängd" i packlistan. Öppnas helt direkt, och innehåll
 * som är högre än skärmen går att scrolla (#100). Ett skrivfel ([error], via
 * `DataError.toMessage()`) visas sist i panelen, eftersom panelen ligger över skärmens meddelanden.
 */
@Composable
fun AppBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    error: DataError? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = AppShapes.sheet,
        containerColor = AppColors.extended.card,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(title, style = AppTypography.sectionTitle, modifier = Modifier.semantics { heading() })
            content()
            error?.let { FieldError(stringResource(it.toMessage())) }
        }
    }
}
